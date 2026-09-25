/**
 * Camera player:
 *   live  → HLS (hls.js) on <video>
 *   dvr   → wall-clock timeline scrub over closed oca_dvr_*.mp4
 * Cut mode draws a draggable range on the scrubber; /api/dvr/cut remuxes it.
 */
import { html, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";
import { api } from "../api.js";
import { startH264Live, stopH264Live, isLivePlaying } from "./live-h264.js";
import {
  LIVE_EDGE_TIP_MS,
  dayKeyFromMs,
  todayKey,
  dayBounds,
  availableTimelineDays as daysFromSegments,
  timelineRangeForDay,
} from "./dvr-timeline.js";

const FRAME_MS = 100;
const SPEED_STEPS = [0.5, 0.75, 1, 1.25, 1.5, 2];
const MIN_CUT_MS = 1000;
let liveH264Active = false;
let liveBusy = false;

/** @type {{ name: string, startUtcMs: number, endUtcMs: number, durationMs: number, positionMs: number, timer: number|null, format: "mp4" }|null} */
let playback = null;
let seeking = false;
/** @type {number|null} wall UTC */
let cutFromMs = null;
/** @type {number|null} wall UTC */
let cutToMs = null;
let cutBusy = false;
/** When true, timeline shows a draggable cut range. */
let cutMode = false;
/** @type {"from"|"to"|"move"|null} */
let cutDrag = null;
/** @type {{ from: number, to: number, wall: number }|null} */
let cutDragOrigin = null;
let timelineSeeking = false;
let playAtBusy = false;
/** @type {((() => Promise<void>)|null)} */
let liveStarter = null;

export function isRecordingPlayback() {
  return state.cameraPlayerMode === "dvr";
}

/** True while the user is scrubbing or dragging a cut range (event paint should not repaint). */
export function isTimelineBusy() {
  return !!timelineSeeking || !!cutDrag;
}

export function canPlayRecording(name) {
  return typeof name === "string" && /\.mp4$/i.test(name);
}

function isMp4Name(name) {
  return typeof name === "string" && /\.mp4$/i.test(name);
}

function playerVideo() {
  return document.getElementById("cameraPlayerVideo");
}

function fmtWall(ms) {
  const n = Number(ms);
  if (!n) return "—";
  try {
    return new Date(n).toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch (e) {
    return String(ms);
  }
}

function clearCutMarks() {
  cutFromMs = null;
  cutToMs = null;
  cutMode = false;
  cutDrag = null;
  cutDragOrigin = null;
}

function enterCutMode() {
  const r = timelineRange();
  if (!r.start || !r.end || r.end <= r.start) return;
  const maxEnd = r.scrubMax != null ? r.scrubMax : r.end - 1;
  const span = Math.max(MIN_CUT_MS, maxEnd - r.start);
  const win = Math.max(MIN_CUT_MS * 5, Math.min(60_000, Math.floor(span * 0.12)));
  let center = wallPlayheadMs();
  if (!center || center < r.start || center > maxEnd) {
    center = maxEnd - Math.floor(win / 2);
  }
  let from = Math.floor(center - win / 2);
  let to = Math.floor(center + win / 2);
  if (from < r.start) {
    to += r.start - from;
    from = r.start;
  }
  if (to > maxEnd) {
    from -= to - maxEnd;
    to = maxEnd;
  }
  from = Math.max(r.start, from);
  to = Math.min(maxEnd, Math.max(from + MIN_CUT_MS, to));
  cutFromMs = from;
  cutToMs = to;
  cutMode = true;
  cutDrag = null;
  cutDragOrigin = null;
  patch({});
}

function exitCutMode() {
  clearCutMarks();
  patch({});
}

function clampCutRange(from, to) {
  const r = timelineRange();
  if (!r.start || !r.end || r.end <= r.start) return;
  const maxEnd = r.scrubMax != null ? r.scrubMax : r.end - 1;
  let a = Math.max(r.start, Math.min(from, maxEnd - MIN_CUT_MS));
  let b = Math.min(maxEnd, Math.max(to, r.start + MIN_CUT_MS));
  if (b - a < MIN_CUT_MS) {
    if (cutDrag === "from") a = b - MIN_CUT_MS;
    else b = a + MIN_CUT_MS;
    a = Math.max(r.start, a);
    b = Math.min(maxEnd, b);
  }
  cutFromMs = Math.floor(a);
  cutToMs = Math.floor(b);
}

function updateCutRangeDom() {
  const r = timelineRange();
  if (!cutMode || cutFromMs == null || cutToMs == null || !r.start || r.end <= r.start) {
    return;
  }
  const span = r.end - r.start;
  const left = ((cutFromMs - r.start) / span) * 100;
  const width = ((cutToMs - cutFromMs) / span) * 100;
  const el = document.getElementById("cameraTimelineCut");
  if (el) {
    el.style.left = left + "%";
    el.style.width = Math.max(0.4, width) + "%";
  }
  const maskL = document.getElementById("cameraTimelineCutMaskL");
  if (maskL) maskL.style.width = Math.max(0, left) + "%";
  const maskR = document.getElementById("cameraTimelineCutMaskR");
  if (maskR) {
    const right = left + width;
    maskR.style.left = right + "%";
    maskR.style.width = Math.max(0, 100 - right) + "%";
  }
  const mark = document.getElementById("cameraPlayerCutLabel");
  if (mark) {
    mark.textContent = fmtWall(cutFromMs) + " – " + fmtWall(cutToMs);
    mark.style.display = "";
  }
  const saveBtn = document.getElementById("cameraPlayerCutSave");
  if (saveBtn) saveBtn.disabled = !canSaveCut();
}

function beginCutDrag(kind, trackEl, clientX, pointerId) {
  if (!cutMode || cutFromMs == null || cutToMs == null) return;
  const wall = wallFromClientX(trackEl, clientX);
  if (wall == null) return;
  cutDrag = kind;
  cutDragOrigin = { from: cutFromMs, to: cutToMs, wall: wall };
  timelineSeeking = false;
  try {
    trackEl.setPointerCapture(pointerId);
  } catch (e) {}
}

function moveCutDrag(trackEl, clientX) {
  if (!cutDrag || !cutDragOrigin) return;
  const wall = wallFromClientX(trackEl, clientX);
  if (wall == null) return;
  if (cutDrag === "from") {
    clampCutRange(wall, cutDragOrigin.to);
  } else if (cutDrag === "to") {
    clampCutRange(cutDragOrigin.from, wall);
  } else if (cutDrag === "move") {
    const delta = wall - cutDragOrigin.wall;
    const len = cutDragOrigin.to - cutDragOrigin.from;
    const r = timelineRange();
    const maxEnd = r.scrubMax != null ? r.scrubMax : r.end - 1;
    let from = cutDragOrigin.from + delta;
    let to = cutDragOrigin.to + delta;
    if (from < r.start) {
      from = r.start;
      to = from + len;
    }
    if (to > maxEnd) {
      to = maxEnd;
      from = to - len;
    }
    clampCutRange(from, to);
  }
  updateCutRangeDom();
}

function endCutDrag() {
  if (!cutDrag) return;
  cutDrag = null;
  cutDragOrigin = null;
  patch({});
}

function cutRangeHtml(r) {
  if (!cutMode || cutFromMs == null || cutToMs == null || !r.start || r.end <= r.start) {
    return nothing;
  }
  const span = r.end - r.start;
  const left = ((cutFromMs - r.start) / span) * 100;
  const width = ((cutToMs - cutFromMs) / span) * 100;
  const right = left + width;
  return html`
    <div
      class="camera-timeline-cut-mask start"
      id="cameraTimelineCutMaskL"
      style=${"width:" + Math.max(0, left) + "%"}
      aria-hidden="true"
    ></div>
    <div
      class="camera-timeline-cut-mask end"
      id="cameraTimelineCutMaskR"
      style=${"left:" + right + "%;width:" + Math.max(0, 100 - right) + "%"}
      aria-hidden="true"
    ></div>
    <div
      class="camera-timeline-cut"
      id="cameraTimelineCut"
      style=${"left:" + left + "%;width:" + Math.max(0.4, width) + "%"}
      @pointerdown=${function (ev) {
        ev.stopPropagation();
        const track = document.getElementById("cameraTimelineTrack");
        if (!track) return;
        beginCutDrag("move", track, ev.clientX, ev.pointerId);
      }}
    >
      <button
        type="button"
        class="camera-timeline-cut-handle start"
        aria-label=${t("cameras.cut.handle_start", "Cut start")}
        @pointerdown=${function (ev) {
          ev.preventDefault();
          ev.stopPropagation();
          const track = document.getElementById("cameraTimelineTrack");
          if (!track) return;
          beginCutDrag("from", track, ev.clientX, ev.pointerId);
        }}
      ></button>
      <button
        type="button"
        class="camera-timeline-cut-handle end"
        aria-label=${t("cameras.cut.handle_end", "Cut end")}
        @pointerdown=${function (ev) {
          ev.preventDefault();
          ev.stopPropagation();
          const track = document.getElementById("cameraTimelineTrack");
          if (!track) return;
          beginCutDrag("to", track, ev.clientX, ev.pointerId);
        }}
      ></button>
    </div>
  `;
}

function timeline() {
  return state.dvrTimeline || {};
}

function selectedDayKey() {
  const k = state.dvrTimelineDay;
  if (typeof k === "string" && /^\d{4}-\d{2}-\d{2}$/.test(k)) return k;
  return todayKey();
}

/** Days that have any stored/active segment, plus today. Newest first. */
function availableTimelineDays() {
  return daysFromSegments(timeline().segments || []);
}

function formatDayLabel(dayKey) {
  if (dayKey === todayKey()) return t("cameras.timeline.today", "Today");
  const yStart = dayBounds(todayKey()).start - 1;
  if (dayKey === dayKeyFromMs(yStart)) {
    return t("cameras.timeline.yesterday", "Yesterday");
  }
  const p = dayKey.split("-").map(Number);
  try {
    return new Date(p[0], p[1] - 1, p[2]).toLocaleDateString(undefined, {
      weekday: "short",
      month: "short",
      day: "numeric",
    });
  } catch (e) {
    return dayKey;
  }
}

function setTimelineDay(dayKey) {
  const days = availableTimelineDays();
  let next = dayKey || todayKey();
  if (days.indexOf(next) < 0) next = todayKey();
  const cur = selectedDayKey();
  if (next === cur) {
    if (next === todayKey() && state.dvrTimelineDay != null) {
      patch({ dvrTimelineDay: null });
    }
    return;
  }
  if (cutMode) clearCutMarks();
  patch({
    dvrTimelineDay: next === todayKey() ? null : next,
  });
  // Historical day: leave live HLS and scrub into that day's recordings.
  if (next !== todayKey()) {
    const bounds = dayBounds(next);
    const segs = (timeline().segments || [])
      .filter(function (s) {
        const a = Number(s.startUtcMs) || 0;
        const b = Number(s.endUtcMs) || 0;
        return b > bounds.start && a < bounds.endFull;
      })
      .sort(function (a, b) {
        return Number(a.startUtcMs) - Number(b.startUtcMs);
      });
    let at = bounds.start;
    if (segs.length) at = Number(segs[0].startUtcMs) || bounds.start;
    at = Math.min(at, bounds.scrubMax);
    playAt(at, liveStarter).catch(function () {});
  }
}

function shiftTimelineDay(dir) {
  const days = availableTimelineDays();
  const cur = selectedDayKey();
  let i = days.indexOf(cur);
  if (i < 0) i = 0;
  // days are newest-first; dir -1 = older, +1 = newer
  const next = days[i - dir];
  if (!next) return;
  setTimelineDay(next);
}

/**
 * Fixed local-day window [midnight, midnight+24h].
 * For today, live caps scrubbing at "now"; the track still spans the full day.
 */
function timelineRange() {
  return timelineRangeForDay(selectedDayKey(), timeline());
}

function wallPlayheadMs() {
  if (state.cameraPlayerMode === "dvr" && playback) {
    const v = playerVideo();
    if (v && Number.isFinite(v.currentTime)) {
      return playback.startUtcMs + Math.max(0, Math.floor(v.currentTime * 1000));
    }
    return playback.startUtcMs + (playback.positionMs || 0);
  }
  const r = timelineRange();
  if (r.isToday && state.cameraPlayerMode === "live" && r.liveAt) {
    return r.liveAt;
  }
  return Number(state.cameraTimelineAtMs) || r.scrubMax || r.liveAt || Date.now();
}

function canSaveCut() {
  return (
    cutFromMs != null &&
    cutToMs != null &&
    cutFromMs < cutToMs &&
    !cutBusy
  );
}

async function downloadCut() {
  if (!canSaveCut()) return;
  cutBusy = true;
  patch({ cameraPreviewError: "" });
  try {
    const url =
      "/api/dvr/cut?fromMs=" +
      encodeURIComponent(String(cutFromMs)) +
      "&toMs=" +
      encodeURIComponent(String(cutToMs));
    const res = await fetch(url, { credentials: "same-origin" });
    const ctype = (res.headers.get("content-type") || "").toLowerCase();
    if (!res.ok || ctype.indexOf("json") >= 0) {
      let msg = "cut failed";
      try {
        const j = await res.json();
        if (j && j.error) msg = j.error;
      } catch (e) {}
      throw new Error(msg);
    }
    const blob = await res.blob();
    let filename = "clip.mp4";
    const cd = res.headers.get("content-disposition") || "";
    const m = /filename=\"?([^\";]+)\"?/i.exec(cd);
    if (m && m[1]) filename = m[1];
    const obj = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = obj;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () {
      URL.revokeObjectURL(obj);
    }, 2000);
    clearCutMarks();
    patch({});
  } catch (e) {
    patch({
      cameraPreviewError: String(e && e.message ? e.message : e),
    });
  } finally {
    cutBusy = false;
    patch({});
  }
}

function detachVideo() {
  const v = playerVideo();
  if (!v) return;
  try {
    v.pause();
    v.removeAttribute("src");
    v.load();
  } catch (e) {}
}

function recordingUrl(name) {
  return "/api/dvr/recordings/" + encodeURIComponent(name) + "?inline=1&t=" + Date.now();
}

function attachStream(name, fromMs) {
  const v = playerVideo();
  if (!v || !playback || !isMp4Name(name)) return;
  const startSec = Math.max(0, (fromMs || 0) / 1000);
  try {
    v.muted = false;
    v.setAttribute("playsinline", "");
    v.setAttribute("webkit-playsinline", "");
  } catch (e) {}
  const onMeta = function () {
    v.removeEventListener("loadedmetadata", onMeta);
    try {
      if (startSec > 0 && Number.isFinite(v.duration)) {
        v.currentTime = Math.min(startSec, Math.max(0, v.duration - 0.05));
      }
      v.play().catch(function () {});
    } catch (e) {}
  };
  const onWaiting = function () {
    patch({ cameraPlaybackLoading: true });
  };
  const onPlaying = function () {
    patch({ cameraPlaybackLoading: false });
  };
  v.addEventListener("loadedmetadata", onMeta);
  v.addEventListener("waiting", onWaiting);
  v.addEventListener("playing", onPlaying);
  v.addEventListener("canplay", onPlaying);
  v.src = recordingUrl(name);
  v.load();
}

function clearTimer() {
  if (playback && playback.timer != null) {
    clearInterval(playback.timer);
    playback.timer = null;
  }
}

function syncTransport() {
  const wall = wallPlayheadMs();
  state.cameraTimelineAtMs = wall;
  const r = timelineRange();
  const playhead = document.getElementById("cameraTimelinePlayhead");
  if (playhead && !timelineSeeking && !cutDrag && r.start && r.end && r.end > r.start) {
    const pct = ((wall - r.start) / (r.end - r.start)) * 100;
    playhead.style.left = Math.max(0, Math.min(100, pct)) + "%";
  }
  // Grow active segment fill between full re-renders.
  syncSegmentDom(r);
  const mark = document.getElementById("cameraPlayerCutLabel");
  if (mark) {
    if (cutMode && cutFromMs != null && cutToMs != null) {
      mark.textContent = fmtWall(cutFromMs) + " – " + fmtWall(cutToMs);
      mark.style.display = "";
    } else {
      mark.textContent = "";
      mark.style.display = "none";
    }
  }
  const saveBtn = document.getElementById("cameraPlayerCutSave");
  if (saveBtn) saveBtn.disabled = !canSaveCut();
  updateCutRangeDom();
  const liveBtn = document.getElementById("cameraTimelineLive");
  if (liveBtn) {
    const onToday = !!r.isToday;
    liveBtn.disabled =
      state.cameraPlayerMode === "live" && !state.cameraPlaybackPaused && onToday;
  }
  const liveEdge = document.getElementById("cameraTimelineLiveEdge");
  if (liveEdge && r.isToday && r.liveAt != null && r.end > r.start) {
    const lp = ((r.liveAt - r.start) / (r.end - r.start)) * 100;
    liveEdge.style.left = Math.max(0, Math.min(100, lp)) + "%";
  }
  const future = document.querySelector(".camera-timeline-future");
  if (future && r.isToday && r.liveAt != null && r.end > r.start) {
    const lp = ((r.liveAt - r.start) / (r.end - r.start)) * 100;
    future.style.left = Math.max(0, Math.min(100, lp)) + "%";
    future.style.width = Math.max(0, 100 - lp) + "%";
  }
  const liveLab = document.querySelector(".camera-timeline-axis-lab.live");
  if (liveLab && r.isToday && r.liveAt != null && r.end > r.start) {
    const lp = ((r.liveAt - r.start) / (r.end - r.start)) * 100;
    liveLab.style.left = Math.max(0, Math.min(100, lp)) + "%";
  }
  const pauseBtn = document.getElementById("cameraTransportPause");
  if (pauseBtn) {
    const canPause =
      state.cameraPlayerMode === "live" || state.cameraPlayerMode === "dvr";
    pauseBtn.disabled = !canPause;
    pauseBtn.textContent = state.cameraPlaybackPaused ? "▶" : "❚❚";
    pauseBtn.title = state.cameraPlaybackPaused
      ? t("cameras.play.resume", "Resume")
      : t("cameras.play.pause", "Pause");
  }
  const speedLab = document.getElementById("cameraTransportSpeed");
  if (speedLab) {
    speedLab.textContent = formatSpeed(currentSpeed());
  }
  applyPlaybackRate();
}

function currentSpeed() {
  const n = Number(state.cameraPlaybackRate);
  return n > 0 ? n : 1;
}

function formatSpeed(rate) {
  const r = Number(rate) || 1;
  if (r === 1) return "1×";
  return String(r).replace(/\.0$/, "") + "×";
}

function applyPlaybackRate() {
  const v = playerVideo();
  if (!v) return;
  try {
    v.playbackRate = currentSpeed();
  } catch (e) {}
}

function resetPlaybackSpeed() {
  if (currentSpeed() === 1) {
    applyPlaybackRate();
    return;
  }
  patch({ cameraPlaybackRate: 1 });
  applyPlaybackRate();
}

function nudgeSpeed(dir) {
  const cur = currentSpeed();
  let i = 0;
  let best = Infinity;
  for (let k = 0; k < SPEED_STEPS.length; k++) {
    const d = Math.abs(SPEED_STEPS[k] - cur);
    if (d < best) {
      best = d;
      i = k;
    }
  }
  i = Math.max(0, Math.min(SPEED_STEPS.length - 1, i + dir));
  patch({ cameraPlaybackRate: SPEED_STEPS[i] });
  applyPlaybackRate();
  syncTransport();
}

/** Jump to the next closed segment, live edge, or pause — never snap-loop. */
function advanceAfterSegment() {
  if (!playback || playAtBusy) return;
  clearTimer();
  const segs = (timeline().segments || []).slice().sort(function (a, b) {
    return Number(a.startUtcMs) - Number(b.startUtcMs);
  });
  const curStart = Number(playback.startUtcMs) || 0;
  let next = null;
  for (let i = 0; i < segs.length; i++) {
    if (Number(segs[i].startUtcMs) > curStart + 250) {
      next = segs[i];
      break;
    }
  }
  if (next) {
    playAt(Number(next.startUtcMs), liveStarter).catch(function () {});
    return;
  }
  const r = timelineRange();
  if (r.recording) {
    backToLive(liveStarter).catch(function () {});
    return;
  }
  patch({ cameraPlaybackPaused: true });
  syncTransport();
}

function tickClock() {
  if (!playback || seeking || state.cameraPlaybackPaused || playAtBusy) return;
  if (playback.format === "mp4") {
    const v = playerVideo();
    if (v) {
      if (v.ended) {
        advanceAfterSegment();
        return;
      }
      if (Number.isFinite(v.currentTime)) {
        playback.positionMs = Math.floor(v.currentTime * 1000);
      }
      // Near media end without ended event: advance once.
      if (
        playback.durationMs > 0 &&
        playback.positionMs >= playback.durationMs - 200
      ) {
        advanceAfterSegment();
        return;
      }
      syncTransport();
      return;
    }
  }
  playback.positionMs = Math.min(
    playback.durationMs,
    playback.positionMs + FRAME_MS,
  );
  if (playback.positionMs >= playback.durationMs) {
    advanceAfterSegment();
    return;
  }
  syncTransport();
}

function startClock() {
  clearTimer();
  if (!playback) return;
  playback.timer = setInterval(tickClock, FRAME_MS);
}

export function stopRecordingPlayback() {
  clearTimer();
  detachVideo();
  playback = null;
  seeking = false;
  if (state.cameraPlayerMode === "dvr") {
    patch({
      cameraPlayerMode: "live",
      cameraPlayingName: "",
      cameraPlayingKind: "",
      cameraPlaybackPaused: false,
      cameraPlaybackLoading: false,
      cameraPlaybackDurationMs: 0,
      cameraPreviewError: "",
    });
  }
}

/**
 * @param {string} name
 * @param {{ durationMs?: number, kind?: string, offsetMs?: number, startUtcMs?: number, endUtcMs?: number, paused?: boolean }} [opts]
 */
export async function playRecording(name, opts) {
  if (!canPlayRecording(name)) {
    patch({
      cameraPreviewError: t("cameras.play.unsupported", "This file cannot be played"),
    });
    return;
  }
  clearTimer();
  detachVideo();
  stopH264Live(playerVideo());
  liveH264Active = false;
  seeking = false;

  if (!(state.status && state.status.dvr && state.status.dvr.recording)) {
    try {
      await api("/api/dvr/preview/stop", { method: "POST" });
    } catch (e) {}
  }

  let durationMs = Number(opts && opts.durationMs) > 0 ? Number(opts.durationMs) : 0;
  const startUtcMs = Number(opts && opts.startUtcMs) || 0;
  const endUtcMs =
    Number(opts && opts.endUtcMs) ||
    (startUtcMs ? startUtcMs + durationMs : 0);
  const offsetMs = Math.max(0, Number(opts && opts.offsetMs) || 0);

  playback = {
    name: name,
    startUtcMs: startUtcMs,
    endUtcMs: endUtcMs || startUtcMs + durationMs,
    durationMs: Math.max(0, durationMs),
    positionMs: offsetMs,
    timer: null,
    format: "mp4",
  };
  patch({
    cameraPlayerMode: "dvr",
    cameraPlayingName: name,
    cameraPlayingKind: "dvr",
    cameraPlaybackPaused: !!(opts && opts.paused),
    cameraPlaybackLoading: true,
    cameraPlaybackDurationMs: playback.durationMs,
    cameraTimelineAtMs: startUtcMs + offsetMs,
    cameraPreviewActive: false,
    cameraPreviewSrc: "",
    cameraPreviewError: "",
  });
  requestAnimationFrame(function () {
    if (!playback || playback.name !== name) return;
    attachStream(name, offsetMs);
    applyPlaybackRate();
    if (opts && opts.paused) {
      const v = playerVideo();
      if (v) v.pause();
      clearTimer();
      patch({ cameraPlaybackLoading: false });
    } else {
      startClock();
    }
    syncTransport();
  });
}

/** Seek wall-clock; snaps gaps server-side. At live edge → HLS. */
export async function playAt(wallUtcMs, startLive) {
  const at = Number(wallUtcMs);
  if (!at || playAtBusy) return;
  if (isLiveEdgeWall(at)) {
    await backToLive(startLive);
    syncTransport();
    return;
  }
  playAtBusy = true;
  patch({ cameraPlaybackLoading: true, cameraPreviewError: "" });
  try {
    const res = await api("/api/dvr/play?atMs=" + encodeURIComponent(String(at)));
    if (!res || res.ok === false) {
      throw new Error((res && res.error) || "play failed");
    }
    if (res.live) {
      await backToLive(startLive);
      syncTransport();
      return;
    }
    // Same segment near end → avoid reload thrash; just pause at end.
    if (
      playback &&
      playback.name === res.name &&
      Math.abs(Number(res.offsetMs) - (playback.positionMs || 0)) < 400 &&
      Number(res.offsetMs) >= Number(res.durationMs) - 500
    ) {
      patch({ cameraPlaybackPaused: true, cameraPlaybackLoading: false });
      return;
    }
    await playRecording(res.name, {
      durationMs: res.durationMs,
      offsetMs: res.offsetMs,
      startUtcMs: res.startUtcMs,
      endUtcMs: res.endUtcMs,
      kind: "dvr",
    });
    // Seal may have grown the timeline — refresh quietly.
    try {
      const { loadRecordings } = await import("../sections/cameras.js");
      await loadRecordings();
    } catch (e) {}
  } catch (e) {
    patch({
      cameraPlaybackLoading: false,
      cameraPreviewError: String(e && e.message ? e.message : e),
    });
  } finally {
    playAtBusy = false;
  }
}

export async function togglePlaybackPause() {
  // Live: pause freezes HLS buffer, or timeshifts into sealed DVR near live.
  if (state.cameraPlayerMode === "live") {
    if (state.cameraPlaybackPaused) {
      patch({ cameraPlaybackPaused: false });
      const v = playerVideo();
      if (v) {
        applyPlaybackRate();
        v.play().catch(function () {});
      }
      syncTransport();
      return;
    }
    const r = timelineRange();
    if (r.isToday && r.segs && r.segs.length) {
      const tip = liveEdgeTipMs(r);
      const max = r.scrubMax != null ? r.scrubMax : r.end - 1;
      let at = wallPlayheadMs();
      if (at >= max - tip) at = max - tip - 1;
      at = Math.max(r.start, Math.min(Math.floor(at), max));
      await playAt(at, liveStarter);
      if (state.cameraPlayerMode === "dvr" && playback) {
        patch({ cameraPlaybackPaused: true });
        const v = playerVideo();
        if (v) v.pause();
        clearTimer();
        syncTransport();
        return;
      }
    }
    const v = playerVideo();
    if (v) v.pause();
    patch({ cameraPlaybackPaused: true });
    syncTransport();
    return;
  }

  if (!playback) return;
  const next = !state.cameraPlaybackPaused;
  patch({ cameraPlaybackPaused: next });
  const v = playerVideo();
  if (v) {
    if (next) v.pause();
    else {
      if (v.ended) v.currentTime = 0;
      applyPlaybackRate();
      v.play().catch(function () {});
      startClock();
    }
  }
  if (next) clearTimer();
}

export function seekPlaybackSeconds(sec) {
  if (!playback) return;
  const ms = Math.max(0, Math.min(playback.durationMs, Number(sec) * 1000));
  playback.positionMs = ms;
  syncTransport();
  const v = playerVideo();
  if (v) {
    try {
      v.currentTime = ms / 1000;
    } catch (e) {}
    if (!state.cameraPlaybackPaused) v.play().catch(function () {});
  }
}

export async function backToLive(startLive) {
  stopRecordingPlayback();
  clearCutMarks();
  resetPlaybackSpeed();
  patch({
    dvrTimelineDay: null,
    cameraTimelineAtMs: Date.now(),
    cameraPlaybackPaused: false,
  });
  if (typeof startLive === "function") await startLive();
  applyPlaybackRate();
}

/** Attach / keep HLS live on <video>. */
export async function applyCameraPlayerSrc() {
  if (state.cameraPlayerMode === "dvr") {
    return;
  }
  const want = state.cameraPreviewSrc || "";
  const video = playerVideo();
  if (!want || want.indexOf("live.m3u8") < 0 || !video) {
    if (liveH264Active || isLivePlaying()) {
      stopH264Live(video);
      liveH264Active = false;
    }
    if (video) {
      video.style.display = "none";
      video.dataset.ocaSrc = "";
    }
    return;
  }
  video.style.display = "";
  if (liveH264Active && isLivePlaying()) {
    video.dataset.ocaSrc = want;
    return;
  }
  if (video.dataset.ocaSrc === want && liveH264Active) return;
  if (liveBusy) return;
  liveBusy = true;
  video.dataset.ocaSrc = want;
  try {
    const ok = await startH264Live(video);
    liveH264Active = !!ok;
    applyPlaybackRate();
    if (state.cameraPlaybackPaused && video) {
      try {
        video.pause();
      } catch (e) {}
    }
    if (!ok) {
      video.style.display = "none";
      const msg = t("cameras.live.failed", "Live H.264 stream failed — check device logs");
      if (state.cameraPreviewError !== msg) patch({ cameraPreviewError: msg });
    } else if (state.cameraPreviewError) {
      patch({ cameraPreviewError: "" });
    }
  } finally {
    liveBusy = false;
  }
}

function timelineTicksHtml(r) {
  if (!r.start || !r.end || r.end <= r.start || !r.segs.length) return nothing;
  const span = r.end - r.start;
  const segs = displaySegsForTrack(r);
  return html`<div class="camera-timeline-ticks" id="cameraTimelineTicks" aria-hidden="true">
    ${segs.map(function (s) {
      const left = ((Number(s.startUtcMs) - r.start) / span) * 100;
      const width = ((Number(s.endUtcMs) - Number(s.startUtcMs)) / span) * 100;
      return html`<span
        class=${"camera-timeline-seg" + (s.active ? " is-active" : "")}
        style=${"left:" +
        Math.max(0, left) +
        "%;width:" +
        Math.max(0.35, width) +
        "%"}
      ></span>`;
    })}
  </div>`;
}

function displaySegsForTrack(r) {
  const raw = r.segs || [];
  if (!raw.length) return [];
  const segs = raw.map(function (s) {
    return {
      startUtcMs: Math.max(r.start, Number(s.startUtcMs) || 0),
      endUtcMs: Math.min(r.end, Number(s.endUtcMs) || 0),
      active: !!s.active,
    };
  }).filter(function (s) {
    return s.endUtcMs > s.startUtcMs;
  });
  if (!r.recording || !r.isToday) return segs;
  const liveEnd = r.liveAt || r.scrubMax || Date.now();
  let idx = -1;
  for (let i = 0; i < segs.length; i++) {
    if (segs[i].active) idx = i;
  }
  if (idx < 0) idx = segs.length - 1;
  if (idx >= 0 && segs[idx].endUtcMs < liveEnd) {
    segs[idx] = Object.assign({}, segs[idx], {
      endUtcMs: Math.min(r.end, liveEnd),
      active: true,
    });
  }
  return segs;
}

function syncSegmentDom(r) {
  if (!r || !r.start || !r.end || r.end <= r.start) return;
  const wrap = document.getElementById("cameraTimelineTicks");
  if (!wrap) return;
  const span = r.end - r.start;
  const segs = displaySegsForTrack(r);
  const nodes = wrap.querySelectorAll(".camera-timeline-seg");
  // Rebuild if count changed (seal/rotate); otherwise update geometry in place.
  if (nodes.length !== segs.length) return;
  for (let i = 0; i < segs.length; i++) {
    const left = ((segs[i].startUtcMs - r.start) / span) * 100;
    const width = ((segs[i].endUtcMs - segs[i].startUtcMs) / span) * 100;
    nodes[i].style.left = Math.max(0, left) + "%";
    nodes[i].style.width = Math.max(0.35, width) + "%";
    nodes[i].classList.toggle("is-active", !!segs[i].active);
  }
}

/** Keep timeline paint in sync while the live range stretches (no API needed). */
let timelinePaintTimer = null;
function ensureTimelinePaintClock() {
  if (timelinePaintTimer != null) return;
  timelinePaintTimer = setInterval(function () {
    if (state.section !== "cameras" && state.section !== "dvr") return;
    const tl = state.dvrTimeline || {};
    const onToday = selectedDayKey() === todayKey();
    if (
      !onToday &&
      !tl.recording &&
      state.cameraPlayerMode !== "dvr"
    ) {
      return;
    }
    // DOM-only: playhead, live edge, future mask, active fill.
    syncTransport();
  }, 500);
}

function wallFromClientX(trackEl, clientX) {
  const r = timelineRange();
  if (!r.start || !r.end || r.end <= r.start || !trackEl) return null;
  const rect = trackEl.getBoundingClientRect();
  if (rect.width <= 0) return null;
  const pct = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  let wall = Math.floor(r.start + pct * (r.end - r.start));
  const max = r.scrubMax != null ? r.scrubMax : r.end - 1;
  if (wall > max) wall = max;
  return wall;
}

function liveEdgeTipMs(r) {
  if (!r || !r.start || !r.end || r.end <= r.start) return LIVE_EDGE_TIP_MS;
  const span = r.end - r.start;
  return Math.max(120, Math.min(LIVE_EDGE_TIP_MS, Math.floor(span * 0.002)));
}

function isLiveEdgeWall(wallMs) {
  const r = timelineRange();
  if (!r.isToday || r.scrubMax == null) return false;
  return wallMs >= r.scrubMax - liveEdgeTipMs(r);
}

function onTimelineSeekWall(wallMs, startLive) {
  if (wallMs == null) return;
  if (isLiveEdgeWall(wallMs)) {
    backToLive(startLive).catch(function () {});
    return;
  }
  playAt(wallMs, startLive);
}

function updateHoverTip(trackEl, clientX) {
  const tip = document.getElementById("cameraTimelineHover");
  if (!tip || !trackEl) return;
  const wall = wallFromClientX(trackEl, clientX);
  if (wall == null) {
    tip.hidden = true;
    return;
  }
  const rect = trackEl.getBoundingClientRect();
  const pct = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  tip.hidden = false;
  tip.textContent = isLiveEdgeWall(wall)
    ? t("cameras.live", "Live")
    : fmtWall(wall);
  tip.style.left = pct * 100 + "%";
}

/** Nice step (ms) for axis labels across [start, end]. */
function timelineAxisStepMs(spanMs) {
  const candidates = [
    15 * 1000,
    30 * 1000,
    60 * 1000,
    2 * 60 * 1000,
    5 * 60 * 1000,
    10 * 60 * 1000,
    15 * 60 * 1000,
    30 * 60 * 1000,
    60 * 60 * 1000,
    2 * 60 * 60 * 1000,
    6 * 60 * 60 * 1000,
  ];
  const target = 5;
  let step = candidates[0];
  for (let i = 0; i < candidates.length; i++) {
    step = candidates[i];
    if (spanMs / step <= target + 1) break;
  }
  return step;
}

function fmtAxisTick(ms, spanMs) {
  const n = Number(ms);
  if (!n) return "—";
  try {
    const d = new Date(n);
    // Single-day scrubber (≤26h): clock time only.
    if (spanMs <= 26 * 60 * 60 * 1000) {
      return d.toLocaleTimeString(undefined, {
        hour: "2-digit",
        minute: "2-digit",
      });
    }
    if (spanMs >= 20 * 60 * 60 * 1000) {
      return d.toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    }
    return d.toLocaleTimeString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch (e) {
    return String(ms);
  }
}

/** Labels under the scrubber across the fixed 24h day. */
function timelineAxisLabels(r) {
  if (!r.start || !r.end || r.end <= r.start) return [];
  const span = r.end - r.start;
  const step = timelineAxisStepMs(span);
  const out = [
    {
      pct: 0,
      text: "00:00",
      edge: "start",
    },
  ];
  let tick = Math.ceil(r.start / step) * step;
  if (tick <= r.start) tick += step;
  const livePct =
    r.isToday && r.liveAt
      ? ((r.liveAt - r.start) / span) * 100
      : null;
  while (tick < r.end) {
    const pct = ((tick - r.start) / span) * 100;
    if (pct > 6 && pct < 94) {
      // Keep clear of Live label.
      if (livePct == null || Math.abs(pct - livePct) > 6) {
        out.push({ pct: pct, text: fmtAxisTick(tick, span), edge: "mid" });
      }
    }
    tick += step;
  }
  if (r.isToday && r.liveAt != null) {
    out.push({
      pct: Math.max(0, Math.min(100, livePct)),
      text: t("cameras.live", "Live"),
      edge: "live",
    });
  } else {
    out.push({
      pct: 100,
      text: "24:00",
      edge: "end",
    });
  }
  return out;
}

function timelineAxisHtml(r) {
  if (!r.start || !r.end || r.end <= r.start) {
    return html`<div class="camera-timeline-axis">
      <span class="sub">${t("cameras.timeline.empty", "No recorded history yet")}</span>
    </div>`;
  }
  const labs = timelineAxisLabels(r);
  const emptyDay = !(r.segs && r.segs.length);
  return html`<div class="camera-timeline-axis" aria-hidden="true">
    ${emptyDay
      ? html`<span class="camera-timeline-axis-empty sub"
          >${t("cameras.timeline.empty_day", "No recordings this day")}</span
        >`
      : nothing}
    ${labs.map(function (l) {
      return html`<span
        class=${"camera-timeline-axis-lab " + l.edge}
        style=${"left:" + l.pct + "%"}
        >${l.text}</span
      >`;
    })}
  </div>`;
}

function futureMaskHtml(r) {
  if (!r.isToday || r.liveAt == null || !r.start || r.end <= r.start) return nothing;
  const pct = ((r.liveAt - r.start) / (r.end - r.start)) * 100;
  if (pct >= 99.5) return nothing;
  return html`<div
    class="camera-timeline-future"
    style=${"left:" + pct + "%;width:" + Math.max(0, 100 - pct) + "%"}
    aria-hidden="true"
  ></div>`;
}

function dayPickerHtml() {
  const days = availableTimelineDays();
  const cur = selectedDayKey();
  const i = days.indexOf(cur);
  const canOlder = i >= 0 && i < days.length - 1;
  const canNewer = i > 0;
  return html`<div class="row camera-timeline-day">
    <button
      type="button"
      class="btn ghost"
      ?disabled=${!canOlder}
      title=${t("cameras.timeline.prev_day", "Previous day")}
      @click=${function () {
        shiftTimelineDay(-1);
      }}
    >
      ‹
    </button>
    <label class="camera-timeline-day-select">
      <span class="sr-only">${t("cameras.timeline.day", "Day")}</span>
      <select
        @change=${function (ev) {
          setTimelineDay(ev.target.value);
        }}
      >
        ${days.map(function (d) {
          return html`<option value=${d} ?selected=${d === cur}
            >${formatDayLabel(d)}</option
          >`;
        })}
      </select>
    </label>
    <button
      type="button"
      class="btn ghost"
      ?disabled=${!canNewer}
      title=${t("cameras.timeline.next_day", "Next day")}
      @click=${function () {
        shiftTimelineDay(1);
      }}
    >
      ›
    </button>
  </div>`;
}

export function cameraTimelineView(opts) {
  ensureTimelinePaintClock();
  const startLive = (opts && opts.startLive) || liveStarter;
  liveStarter = typeof startLive === "function" ? startLive : liveStarter;
  const r = timelineRange();
  const hasTimeline = !!(r.start && r.end && r.end > r.start);
  const hasRecordings = !!(r.segs && r.segs.length);
  const wall = wallPlayheadMs();
  const playPct =
    hasTimeline && r.end > r.start
      ? Math.max(0, Math.min(100, ((wall - r.start) / (r.end - r.start)) * 100))
      : 100;
  const livePct =
    r.isToday && r.liveAt != null && r.end > r.start
      ? Math.max(0, Math.min(100, ((r.liveAt - r.start) / (r.end - r.start)) * 100))
      : null;
  const inDvr = state.cameraPlayerMode === "dvr";
  const isLive = state.cameraPlayerMode === "live";
  const canTransport = isLive || inDvr;
  const paused = !!state.cameraPlaybackPaused;
  const speed = currentSpeed();

  return html`
    <div class="card camera-timeline">
      ${dayPickerHtml()}
      <div class="row camera-transport">
        <button
          type="button"
          class="btn ghost camera-transport-play"
          id="cameraTransportPause"
          ?disabled=${!canTransport}
          title=${paused
            ? t("cameras.play.resume", "Resume")
            : t("cameras.play.pause", "Pause")}
          @click=${async function () {
            if (!canTransport) return;
            await togglePlaybackPause();
            syncTransport();
            patch({});
          }}
        >
          ${paused ? "▶" : "❚❚"}
        </button>
        <button
          type="button"
          class="btn ghost"
          ?disabled=${!canTransport || speed <= SPEED_STEPS[0]}
          title=${t("cameras.play.speed_slower", "Slower")}
          @click=${function () {
            nudgeSpeed(-1);
            patch({});
          }}
        >
          −
        </button>
        <span class="mono camera-transport-speed" id="cameraTransportSpeed"
          >${formatSpeed(speed)}</span
        >
        <button
          type="button"
          class="btn ghost"
          ?disabled=${!canTransport || speed >= SPEED_STEPS[SPEED_STEPS.length - 1]}
          title=${t("cameras.play.speed_faster", "Faster")}
          @click=${function () {
            nudgeSpeed(1);
            patch({});
          }}
        >
          +
        </button>
        <button
          type="button"
          class="btn ghost"
          id="cameraTimelineLive"
          ?disabled=${isLive && !paused && r.isToday}
          @click=${async function () {
            patch({ cameraPlaybackPaused: false });
            await backToLive(startLive);
            applyPlaybackRate();
          }}
        >
          ${t("cameras.play.live", "Back to live")}
        </button>
        <div class="row camera-cut-bar" style="margin:0;margin-left:auto">
          ${cutMode
            ? html`
                <span class="sub mono" id="cameraPlayerCutLabel"
                  >${fmtWall(cutFromMs)} – ${fmtWall(cutToMs)}</span
                >
                <button
                  type="button"
                  class="btn ghost"
                  @click=${function () {
                    exitCutMode();
                  }}
                >
                  ${t("cameras.cut.cancel", "Cancel")}
                </button>
                <button
                  type="button"
                  class="btn primary"
                  id="cameraPlayerCutSave"
                  ?disabled=${!canSaveCut()}
                  @click=${async function () {
                    await downloadCut();
                  }}
                >
                  ${t("cameras.cut.download", "Download clip")}
                </button>
              `
            : html`
                <button
                  type="button"
                  class="btn ghost"
                  ?disabled=${!hasRecordings}
                  title=${t("cameras.cut.edit", "Select a clip range on the timeline")}
                  @click=${function () {
                    enterCutMode();
                  }}
                >
                  ${t("cameras.cut.edit", "Cut")}
                </button>
                <span class="sub mono" id="cameraPlayerCutLabel" style="display:none"></span>
              `}
        </div>
      </div>
      <div
        class=${"camera-timeline-track" +
        (hasTimeline ? "" : " is-empty") +
        (cutMode ? " is-cutting" : "")}
        id="cameraTimelineTrack"
        role="slider"
        tabindex="0"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow=${String(Math.round(playPct))}
        aria-label=${t("cameras.timeline.seek", "Scrub timeline")}
        @keydown=${function (ev) {
          if (!hasTimeline) return;
          const key = ev.key;
          if (key !== "ArrowLeft" && key !== "ArrowRight" && key !== "Home" && key !== "End") {
            return;
          }
          ev.preventDefault();
          const step = ev.shiftKey ? 60_000 : 10_000;
          let wall = wallPlayheadMs();
          if (key === "Home") wall = r.start;
          else if (key === "End") wall = r.scrubMax != null ? r.scrubMax : r.end - 1;
          else if (key === "ArrowLeft") wall -= step;
          else wall += step;
          const max = r.scrubMax != null ? r.scrubMax : r.end - 1;
          wall = Math.max(r.start, Math.min(Math.floor(wall), max));
          onTimelineSeekWall(wall, startLive);
        }}
        @pointerdown=${function (ev) {
          if (!hasTimeline) return;
          if (cutDrag) return;
          const track = ev.currentTarget;
          timelineSeeking = true;
          try {
            track.setPointerCapture(ev.pointerId);
          } catch (e) {}
          const wallMs = wallFromClientX(track, ev.clientX);
          const ph = document.getElementById("cameraTimelinePlayhead");
          if (ph && wallMs != null && r.end > r.start) {
            ph.style.left =
              ((wallMs - r.start) / (r.end - r.start)) * 100 + "%";
          }
          updateHoverTip(track, ev.clientX);
        }}
        @pointermove=${function (ev) {
          const track = ev.currentTarget;
          if (cutDrag) {
            moveCutDrag(track, ev.clientX);
            updateHoverTip(track, ev.clientX);
            return;
          }
          updateHoverTip(track, ev.clientX);
          if (!timelineSeeking || !hasTimeline) return;
          const wallMs = wallFromClientX(track, ev.clientX);
          const ph = document.getElementById("cameraTimelinePlayhead");
          if (ph && wallMs != null && r.end > r.start) {
            ph.style.left =
              ((wallMs - r.start) / (r.end - r.start)) * 100 + "%";
          }
        }}
        @pointerup=${function (ev) {
          const track = ev.currentTarget;
          if (cutDrag) {
            endCutDrag();
            return;
          }
          if (!timelineSeeking) return;
          timelineSeeking = false;
          onTimelineSeekWall(wallFromClientX(track, ev.clientX), startLive);
        }}
        @pointercancel=${function () {
          if (cutDrag) endCutDrag();
          timelineSeeking = false;
        }}
        @pointerleave=${function () {
          const tip = document.getElementById("cameraTimelineHover");
          if (tip && !timelineSeeking && !cutDrag) tip.hidden = true;
        }}
      >
        <div class="camera-timeline-rail" aria-hidden="true"></div>
        ${timelineTicksHtml(r)}
        ${futureMaskHtml(r)}
        ${cutRangeHtml(r)}
        ${livePct != null
          ? html`<span
              class="camera-timeline-live-edge"
              id="cameraTimelineLiveEdge"
              style=${"left:" + livePct + "%"}
              aria-hidden="true"
            ></span>`
          : nothing}
        <span
          class="camera-timeline-playhead"
          id="cameraTimelinePlayhead"
          style=${"left:" + playPct + "%"}
        ></span>
        <span class="camera-timeline-hover mono" id="cameraTimelineHover" hidden></span>
      </div>
      ${timelineAxisHtml(r)}
    </div>
  `;
}

export function cameraPlayerView(opts) {
  const mode = state.cameraPlayerMode || "live";
  const playingName = state.cameraPlayingName || "";
  const err = state.cameraPreviewError || "";
  const lastError = (opts && opts.lastError) || "";
  const startLive = opts && opts.startLive;
  liveStarter = typeof startLive === "function" ? startLive : liveStarter;
  const useVideo = mode === "live" || (mode === "dvr" && isMp4Name(playingName));
  const label =
    mode === "dvr"
      ? t("cameras.timeline.dvr", "DVR")
      : t("cameras.live", "Live");
  const loading = !!state.cameraPlaybackLoading && mode === "dvr";

  return html`
    <div
      class=${"card camera-player" + ((opts && opts.embedded) ? " camera-player-fill" : "")}
      style=${(opts && opts.embedded) ? "margin:0" : "margin-top:18px"}
    >
      <div
        class="row camera-player-bar"
        style="justify-content:space-between;align-items:center;margin:0 0 10px;gap:8px;flex-wrap:wrap"
      >
        <div class="row" style="margin:0;gap:8px;align-items:center;min-width:0;flex:1 1 auto">
          <span
            class="badge camera-player-label ${mode === "dvr" ? "accent" : "ok"}"
            title=${label}
            >${label}</span
          >
          ${loading
            ? html`<span class="sub mono camera-player-loading"
                >${t("cameras.play.loading", "Loading…")}</span
              >`
            : nothing}
        </div>
      </div>
      <div class="camera-preview-wrap">
        <video
          class="preview"
          id="cameraPlayerVideo"
          playsinline
          ?muted=${mode === "live"}
          style=${useVideo ? "" : "display:none"}
        ></video>
        ${loading
          ? html`<div class="camera-preview-loading" aria-live="polite">
              ${t("cameras.play.loading", "Loading…")}
            </div>`
          : nothing}
      </div>
      ${err ? html`<p class="sub">${err}</p>` : nothing}
      ${lastError ? html`<p class="sub" style="color:var(--warn)">${lastError}</p>` : nothing}
    </div>
  `;
}
