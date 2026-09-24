/**
 * Shared camera player: live MJPEG stream or recorded concatenated-JPEG playback.
 */
import { html, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";
import { api } from "../api.js";

const FPS = 10;
const FRAME_MS = 1000 / FPS;

/** @type {{ name: string, frames: Uint8Array[], index: number, timer: number|null, url: string|null }|null} */
let playback = null;

export function isRecordingPlayback() {
  return state.cameraPlayerMode === "recording";
}

export function canPlayRecording(name) {
  return typeof name === "string" && /\.mjpeg$/i.test(name);
}

function splitJpegFrames(buffer) {
  const bytes = new Uint8Array(buffer);
  const frames = [];
  let i = 0;
  while (i < bytes.length - 1) {
    if (bytes[i] === 0xff && bytes[i + 1] === 0xd8) {
      let j = i + 2;
      while (j < bytes.length - 1) {
        if (bytes[j] === 0xff && bytes[j + 1] === 0xd9) {
          frames.push(bytes.subarray(i, j + 2));
          i = j + 2;
          break;
        }
        j++;
      }
      if (j >= bytes.length - 1) break;
    } else {
      i++;
    }
  }
  return frames;
}

function playerImg() {
  return document.getElementById("cameraPlayerFrame");
}

function revokeFrameUrl() {
  if (playback && playback.url) {
    try {
      URL.revokeObjectURL(playback.url);
    } catch (e) {}
    playback.url = null;
  }
}

function showFrame(index) {
  if (!playback || !playback.frames.length) return;
  const img = playerImg();
  if (!img) return;
  const i = ((index % playback.frames.length) + playback.frames.length) % playback.frames.length;
  playback.index = i;
  revokeFrameUrl();
  const blob = new Blob([playback.frames[i]], { type: "image/jpeg" });
  const url = URL.createObjectURL(blob);
  playback.url = url;
  img.dataset.ocaSrc = "";
  img.src = url;
  const counter = document.getElementById("cameraPlayerCounter");
  if (counter) {
    counter.textContent = i + 1 + "/" + playback.frames.length;
  }
}

function clearTimer() {
  if (playback && playback.timer != null) {
    clearInterval(playback.timer);
    playback.timer = null;
  }
}

function tick() {
  if (!playback || state.cameraPlaybackPaused) return;
  showFrame(playback.index + 1);
}

export function stopRecordingPlayback() {
  clearTimer();
  revokeFrameUrl();
  playback = null;
  if (state.cameraPlayerMode === "recording") {
    patch({
      cameraPlayerMode: "live",
      cameraPlayingName: "",
      cameraPlaybackPaused: false,
      cameraPlaybackLoading: false,
      cameraPlaybackIndex: 0,
      cameraPlaybackCount: 0,
      cameraPreviewError: "",
    });
  }
}

export async function playRecording(name) {
  if (!canPlayRecording(name)) {
    patch({
      cameraPreviewError: t("cameras.play.unsupported", "This file cannot be played"),
    });
    return;
  }
  clearTimer();
  revokeFrameUrl();
  playback = null;

  // Free the live camera while reviewing a clip.
  try {
    await api("/api/dvr/preview/stop", { method: "POST" });
  } catch (e) {}

  patch({
    cameraPlayerMode: "recording",
    cameraPlayingName: name,
    cameraPlaybackPaused: false,
    cameraPlaybackLoading: true,
    cameraPlaybackIndex: 0,
    cameraPlaybackCount: 0,
    cameraPreviewActive: false,
    cameraPreviewSrc: "",
    cameraPreviewError: "",
  });

  try {
    const res = await fetch(
      "/api/dvr/recordings/" + encodeURIComponent(name) + "?inline=1",
    );
    if (!res.ok) throw new Error("HTTP " + res.status);
    const buf = await res.arrayBuffer();
    const frames = splitJpegFrames(buf);
    if (!frames.length) throw new Error(t("cameras.play.empty", "No frames in recording"));

    playback = {
      name: name,
      frames: frames,
      index: 0,
      timer: null,
      url: null,
    };
    patch({
      cameraPlaybackLoading: false,
      cameraPlaybackIndex: 0,
      cameraPlaybackCount: frames.length,
      cameraPreviewError: "",
    });
    requestAnimationFrame(function () {
      if (!playback || playback.name !== name) return;
      showFrame(0);
      if (playback.timer == null) {
        playback.timer = setInterval(tick, FRAME_MS);
      }
    });
  } catch (e) {
    playback = null;
    patch({
      cameraPlayerMode: "live",
      cameraPlayingName: "",
      cameraPlaybackLoading: false,
      cameraPlaybackPaused: false,
      cameraPreviewError: String(e && e.message ? e.message : e),
    });
  }
}

export function togglePlaybackPause() {
  if (!playback) return;
  const next = !state.cameraPlaybackPaused;
  patch({
    cameraPlaybackPaused: next,
    cameraPlaybackIndex: playback.index,
    cameraPlaybackCount: playback.frames.length,
  });
  if (!next) {
    // Resume immediately so UI feels responsive.
    showFrame(playback.index + 1);
  }
}

export async function backToLive(startLive) {
  stopRecordingPlayback();
  if (typeof startLive === "function") {
    await startLive();
  }
}

/** Keep live <img src> in sync without resetting recording frames on re-render. */
export function applyCameraPlayerSrc() {
  const img = playerImg();
  if (!img) return;
  if (state.cameraPlayerMode === "recording") return;
  const want = state.cameraPreviewSrc || "";
  if (img.dataset.ocaSrc === want) return;
  img.dataset.ocaSrc = want;
  if (want) img.src = want;
  else {
    img.removeAttribute("src");
  }
}

export function cameraPlayerView(opts) {
  const mode = state.cameraPlayerMode || "live";
  const playingName = state.cameraPlayingName || "";
  const loading = !!state.cameraPlaybackLoading;
  const paused = !!state.cameraPlaybackPaused;
  const index = state.cameraPlaybackIndex || 0;
  const count = state.cameraPlaybackCount || 0;
  const err = state.cameraPreviewError || "";
  const lastError = (opts && opts.lastError) || "";
  const startLive = opts && opts.startLive;

  const label =
    mode === "recording"
      ? t("cameras.play.playing", "Playing") + (playingName ? " · " + playingName : "")
      : t("cameras.live", "Live");

  return html`
    <div class="card camera-player" style="margin-top:18px">
      <div
        class="row"
        style="justify-content:space-between;align-items:center;margin:0 0 10px;gap:8px;flex-wrap:wrap"
      >
        <div class="row" style="margin:0;gap:8px;align-items:center;min-width:0">
          <span class="badge ${mode === "recording" ? "accent" : "ok"}">${label}</span>
          ${mode === "recording" && count
            ? html`<span class="sub mono" id="cameraPlayerCounter"
                >${index + 1}/${count}</span
              >`
            : nothing}
          ${loading
            ? html`<span class="sub">${t("cameras.play.loading", "Loading…")}</span>`
            : nothing}
        </div>
        ${mode === "recording"
          ? html`<div class="row" style="margin:0;gap:6px;flex-shrink:0">
              <button
                type="button"
                class="btn"
                ?disabled=${loading || !count}
                @click=${function () {
                  togglePlaybackPause();
                }}
              >
                ${paused
                  ? t("cameras.play.resume", "Resume")
                  : t("cameras.play.pause", "Pause")}
              </button>
              <button
                type="button"
                class="btn ghost"
                @click=${async function () {
                  await backToLive(startLive);
                }}
              >
                ${t("cameras.play.live", "Back to live")}
              </button>
            </div>`
          : nothing}
      </div>
      <img
        class="preview"
        id="cameraPlayerFrame"
        alt=""
        style="display:block;width:100%;max-height:520px;object-fit:contain;background:#000"
      />
      ${err ? html`<p class="sub">${err}</p>` : nothing}
      ${lastError
        ? html`<p class="sub" style="color:var(--warn)">${lastError}</p>`
        : nothing}
    </div>
  `;
}
