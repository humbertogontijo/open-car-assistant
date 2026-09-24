/**
 * Camera player:
 *   live       → HLS (hls.js) on <video>
 *   recording  → .mp4 on <video>, legacy .mjpeg multipart on <img>
 */
import { html, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";
import { api } from "../api.js";
import { startH264Live, stopH264Live, isLivePlaying } from "./live-h264.js";

const FRAME_MS = 100;
let liveH264Active = false;
let liveBusy = false;

/** @type {{ name: string, durationMs: number, positionMs: number, timer: number|null, format: "mp4"|"mjpeg" }|null} */
let playback = null;
let seeking = false;

export function isRecordingPlayback() {
  return state.cameraPlayerMode === "recording";
}

export function canPlayRecording(name) {
  return typeof name === "string" && /\.(mjpeg|mp4)$/i.test(name);
}

function isMp4Name(name) {
  return typeof name === "string" && /\.mp4$/i.test(name);
}

function playerImg() {
  return document.getElementById("cameraPlayerFrame");
}

function playerVideo() {
  return document.getElementById("cameraPlayerVideo");
}

function fmtTime(ms) {
  const total = Math.max(0, Math.floor(Number(ms) / 1000));
  return Math.floor(total / 60) + ":" + String(total % 60).padStart(2, "0");
}

function detachImg() {
  const img = playerImg();
  if (!img) return;
  img.dataset.ocaSrc = "";
  try {
    img.removeAttribute("src");
  } catch (e) {}
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

function streamUrl(name, fromMs) {
  return (
    "/api/dvr/recordings/" +
    encodeURIComponent(name) +
    "/stream?fromMs=" +
    encodeURIComponent(String(Math.max(0, Math.floor(fromMs || 0)))) +
    "&t=" +
    Date.now()
  );
}

function recordingUrl(name) {
  return "/api/dvr/recordings/" + encodeURIComponent(name) + "?inline=1&t=" + Date.now();
}

function attachStream(name, fromMs) {
  if (isMp4Name(name)) {
    const v = playerVideo();
    if (!v || !playback) return;
    const startSec = Math.max(0, (fromMs || 0) / 1000);
    const onMeta = function () {
      v.removeEventListener("loadedmetadata", onMeta);
      try {
        if (startSec > 0 && Number.isFinite(v.duration)) {
          v.currentTime = Math.min(startSec, v.duration);
        }
        v.play().catch(function () {});
      } catch (e) {}
    };
    v.addEventListener("loadedmetadata", onMeta);
    v.src = recordingUrl(name);
    v.load();
    return;
  }
  const img = playerImg();
  if (!img || !playback) return;
  img.dataset.ocaSrc = streamUrl(name, fromMs);
  img.src = img.dataset.ocaSrc;
}

function clearTimer() {
  if (playback && playback.timer != null) {
    clearInterval(playback.timer);
    playback.timer = null;
  }
}

function syncTransport() {
  if (!playback) return;
  const cur = document.getElementById("cameraPlayerTime");
  const dur = document.getElementById("cameraPlayerDuration");
  const seek = document.getElementById("cameraPlayerSeek");
  let posMs = playback.positionMs;
  let durMs = Math.max(playback.durationMs, FRAME_MS);
  if (playback.format === "mp4") {
    const v = playerVideo();
    if (v && Number.isFinite(v.duration) && v.duration > 0) {
      durMs = Math.round(v.duration * 1000);
      playback.durationMs = durMs;
    }
    if (v && !seeking) posMs = Math.round((v.currentTime || 0) * 1000);
    playback.positionMs = posMs;
  }
  if (cur) cur.textContent = fmtTime(posMs);
  if (dur) dur.textContent = fmtTime(durMs);
  if (seek && !seeking) {
    seek.max = String((durMs / 1000).toFixed(1));
    seek.value = String((posMs / 1000).toFixed(1));
  }
}

function tickClock() {
  if (!playback || state.cameraPlaybackPaused || seeking) return;
  if (playback.format === "mp4") {
    const v = playerVideo();
    if (v) {
      playback.positionMs = Math.round((v.currentTime || 0) * 1000);
      syncTransport();
      if (v.ended) {
        clearTimer();
        patch({ cameraPlaybackPaused: true });
      }
    }
    return;
  }
  playback.positionMs = Math.min(playback.durationMs, playback.positionMs + FRAME_MS);
  syncTransport();
  if (playback.positionMs >= playback.durationMs) {
    clearTimer();
    detachImg();
    patch({ cameraPlaybackPaused: true });
  }
}

function startClock() {
  clearTimer();
  if (!playback) return;
  playback.timer = setInterval(tickClock, FRAME_MS);
}

export function stopRecordingPlayback() {
  clearTimer();
  detachImg();
  detachVideo();
  playback = null;
  seeking = false;
  if (state.cameraPlayerMode === "recording") {
    patch({
      cameraPlayerMode: "live",
      cameraPlayingName: "",
      cameraPlaybackPaused: false,
      cameraPlaybackLoading: false,
      cameraPlaybackIndex: 0,
      cameraPlaybackCount: 0,
      cameraPlaybackDurationMs: 0,
      cameraPreviewError: "",
    });
  }
}

/**
 * @param {string} name
 * @param {{ durationMs?: number, frameCount?: number }} [opts]
 */
export async function playRecording(name, opts) {
  if (!canPlayRecording(name)) {
    patch({
      cameraPreviewError: t("cameras.play.unsupported", "This file cannot be played"),
    });
    return;
  }
  clearTimer();
  detachImg();
  detachVideo();
  stopH264Live(playerVideo());
  liveH264Active = false;
  seeking = false;

  if (!(state.status && state.status.dvr && state.status.dvr.recording)) {
    try {
      await api("/api/dvr/preview/stop", { method: "POST" });
    } catch (e) {}
  }

  const format = isMp4Name(name) ? "mp4" : "mjpeg";
  let durationMs = Number(opts && opts.durationMs) > 0 ? Number(opts.durationMs) : 0;
  if (!durationMs && format === "mjpeg" && Number(opts && opts.frameCount) > 0) {
    durationMs = Number(opts.frameCount) * FRAME_MS;
  }
  if (format === "mjpeg" && !durationMs) {
    patch({ cameraPreviewError: t("cameras.play.empty", "No frames in recording") });
    return;
  }

  playback = {
    name: name,
    durationMs: Math.max(format === "mp4" ? 0 : FRAME_MS, durationMs || FRAME_MS),
    positionMs: 0,
    timer: null,
    format: format,
  };
  patch({
    cameraPlayerMode: "recording",
    cameraPlayingName: name,
    cameraPlaybackPaused: false,
    cameraPlaybackLoading: false,
    cameraPlaybackDurationMs: playback.durationMs,
    cameraPreviewActive: false,
    cameraPreviewSrc: "",
    cameraPreviewError: "",
  });
  requestAnimationFrame(function () {
    if (!playback || playback.name !== name) return;
    attachStream(name, 0);
    startClock();
    syncTransport();
  });
}

export function togglePlaybackPause() {
  if (!playback) return;
  const next = !state.cameraPlaybackPaused;
  patch({ cameraPlaybackPaused: next });
  if (playback.format === "mp4") {
    const v = playerVideo();
    if (v) {
      if (next) v.pause();
      else {
        if (v.ended) v.currentTime = 0;
        v.play().catch(function () {});
        startClock();
      }
    }
    if (next) clearTimer();
    return;
  }
  if (next) {
    clearTimer();
    detachImg();
  } else {
    if (playback.positionMs >= playback.durationMs) playback.positionMs = 0;
    attachStream(playback.name, playback.positionMs);
    startClock();
  }
}

export function seekPlaybackSeconds(sec) {
  if (!playback) return;
  const ms = Math.max(0, Math.min(playback.durationMs, Number(sec) * 1000));
  playback.positionMs = ms;
  syncTransport();
  if (playback.format === "mp4") {
    const v = playerVideo();
    if (v) {
      try {
        v.currentTime = ms / 1000;
      } catch (e) {}
      if (!state.cameraPlaybackPaused) v.play().catch(function () {});
    }
    return;
  }
  if (!state.cameraPlaybackPaused) attachStream(playback.name, ms);
}

export async function backToLive(startLive) {
  stopRecordingPlayback();
  if (typeof startLive === "function") await startLive();
}

/** Attach / keep HLS live on <video>. */
export async function applyCameraPlayerSrc() {
  if (state.cameraPlayerMode === "recording") return;
  const want = state.cameraPreviewSrc || "";
  const video = playerVideo();
  const img = playerImg();
  if (img) {
    img.style.display = "none";
    img.removeAttribute("src");
    img.dataset.ocaSrc = "";
  }
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
  // Soft refresh must not tear down HLS.
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

export function cameraPlayerView(opts) {
  const mode = state.cameraPlayerMode || "live";
  const playingName = state.cameraPlayingName || "";
  const paused = !!state.cameraPlaybackPaused;
  const err = state.cameraPreviewError || "";
  const lastError = (opts && opts.lastError) || "";
  const startLive = opts && opts.startLive;
  const showTransport = mode === "recording";
  const useVideo =
    mode === "live" || (mode === "recording" && isMp4Name(playingName));
  const label =
    mode === "recording"
      ? t("cameras.play.playing", "Playing") + (playingName ? " · " + playingName : "")
      : t("cameras.live", "Live");

  return html`
    <div
      class=${"card camera-player" + ((opts && opts.embedded) ? " camera-player-fill" : "")}
      style=${(opts && opts.embedded) ? "margin:0" : "margin-top:18px"}
    >
      <div
        class="row camera-player-bar"
        style="justify-content:space-between;align-items:center;margin:0 0 10px;gap:8px;flex-wrap:wrap"
      >
        <div class="row" style="margin:0;gap:8px;align-items:center;min-width:0">
          <span class="badge ${mode === "recording" ? "accent" : "ok"}">${label}</span>
        </div>
        ${mode === "recording"
          ? html`<div class="row" style="margin:0;gap:6px;flex-shrink:0">
              <button type="button" class="btn ghost" @click=${async function () {
                await backToLive(startLive);
              }}>
                ${t("cameras.play.live", "Back to live")}
              </button>
            </div>`
          : nothing}
      </div>
      <div class="camera-preview-wrap">
        <img class="preview" id="cameraPlayerFrame" alt="" style=${useVideo ? "display:none" : ""} />
        <video
          class="preview"
          id="cameraPlayerVideo"
          playsinline
          muted
          style=${useVideo ? "" : "display:none"}
        ></video>
      </div>
      ${showTransport
        ? html`<div class="camera-transport">
            <button type="button" class="btn ghost camera-transport-play" @click=${function () {
              togglePlaybackPause();
            }}>${paused ? "▶" : "❚❚"}</button>
            <span class="sub mono" id="cameraPlayerTime">0:00</span>
            <input
              type="range"
              id="cameraPlayerSeek"
              class="camera-seek"
              min="0"
              max="1"
              step="0.1"
              value="0"
              @pointerdown=${function () {
                seeking = true;
                clearTimer();
                if (playback && playback.format === "mjpeg") detachImg();
                else {
                  const v = playerVideo();
                  if (v) v.pause();
                }
              }}
              @pointerup=${function (ev) {
                seeking = false;
                seekPlaybackSeconds(ev.target.value);
                if (!state.cameraPlaybackPaused) {
                  if (playback && playback.format === "mjpeg") {
                    attachStream(playback.name, playback.positionMs);
                  }
                  startClock();
                }
              }}
              @input=${function (ev) {
                if (!playback) return;
                playback.positionMs = Math.max(
                  0,
                  Math.min(playback.durationMs, Number(ev.target.value) * 1000),
                );
                syncTransport();
              }}
            />
            <span class="sub mono" id="cameraPlayerDuration">0:00</span>
          </div>`
        : nothing}
      ${err ? html`<p class="sub">${err}</p>` : nothing}
      ${lastError ? html`<p class="sub" style="color:var(--warn)">${lastError}</p>` : nothing}
    </div>
  `;
}
