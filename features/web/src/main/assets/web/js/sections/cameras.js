import { html, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";
import { api } from "../api.js";
import { prefCard, prefSegment } from "../ui/cards.js";
import {
  cameraPlayerView,
  applyCameraPlayerSrc,
  playRecording,
  stopRecordingPlayback,
  isRecordingPlayback,
  canPlayRecording,
  backToLive,
} from "../ui/camera-player.js";

function fmtTs(ts) {
  const n = Number(ts);
  if (!n) return "—";
  try {
    return new Date(n).toLocaleString();
  } catch (e) {
    return String(ts);
  }
}

function fmtBytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return v + " B";
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + " KB";
  return (v / (1024 * 1024)).toFixed(1) + " MB";
}

export async function loadRecordings() {
  try {
    const res = await api("/api/dvr/recordings");
    patch({ recordings: (res && res.recordings) || [] });
  } catch (e) {
    patch({ recordings: [] });
  }
}

export async function startCameraLive() {
  if (isRecordingPlayback()) return;
  if (
    state.cameraPreviewActive &&
    state.cameraPreviewSrc &&
    state.cameraPreviewSrc.indexOf("preview.mjpeg") >= 0
  ) {
    return;
  }
  try {
    await api("/api/dvr/preview/start", { method: "POST" });
    patch({
      cameraPreviewActive: true,
      cameraPreviewSrc: "/api/dvr/preview.mjpeg?t=" + Date.now(),
      cameraPreviewError: "",
      cameraPlayerMode: "live",
      cameraPlayingName: "",
    });
  } catch (e) {
    patch({
      cameraPreviewActive: false,
      cameraPreviewSrc: "",
      cameraPreviewError: String(e && e.message ? e.message : e),
    });
  }
}

export async function stopCameraLive() {
  stopRecordingPlayback();
  const hadPreview = !!(state.cameraPreviewSrc || state.cameraPreviewActive);
  patch({
    cameraPreviewActive: false,
    cameraPreviewSrc: "",
    cameraPlayerMode: "live",
    cameraPlayingName: "",
  });
  if (hadPreview) {
    try {
      await api("/api/dvr/preview/stop", { method: "POST" });
    } catch (e) {}
  }
}

export { applyCameraPlayerSrc };

export function sectionCameras() {
  const dvr = (state.status && state.status.dvr) || {};
  const recording = !!dvr.recording;
  const storages = dvr.storages || [];
  const storageId = dvr.storageId || "app";
  const storageOpts = storages.map(function (s) {
    const label =
      s.labelKey === "cameras.storage.sd"
        ? t("cameras.storage.sd", "SD / USB ({label})").replace("{label}", s.label || "")
        : t(s.labelKey || "cameras.storage.app", s.label || s.id);
    return { value: s.id, label: label };
  });
  if (!storageOpts.length) {
    storageOpts.push({ value: "app", label: t("cameras.storage.app", "App") });
  }
  const recOpts = [
    { value: "0", label: t("cameras.live", "Ao vivo") },
    { value: "1", label: t("cameras.recording", "Gravando…") },
  ];
  const recordings = state.recordings || [];
  const activeName = state.cameraPlayingName || "";

  return html`
    <h1>${t("section.cameras.title", "Câmeras")}</h1>
    <div class="card" style="margin-bottom:16px">
      <h2 style="margin:0 0 8px">${t("cameras.storage", "Salvar em")}</h2>
      <p class="sub" style="margin:0 0 10px">
        ${t("cameras.storage.shared", "Used for new recordings and the list below")}
      </p>
      ${prefSegment("cam-storage", storageOpts, storageId)}
    </div>
    <div class="grid">
      ${prefCard({
        icon: "camera",
        title: t("cameras.rec_toggle", "Gravação"),
        body: prefSegment("cam-rec", recOpts, recording ? "1" : "0"),
      })}
    </div>
    ${cameraPlayerView({
      lastError: dvr.lastError || "",
      startLive: startCameraLive,
    })}
    <div class="card" style="margin-top:18px">
      <div
        class="row"
        style="justify-content:space-between;align-items:center;margin:0 0 10px"
      >
        <h2 style="margin:0">${t("cameras.recordings", "Recordings")}</h2>
        <button
          type="button"
          class="btn"
          @click=${async function () {
            await loadRecordings();
          }}
        >
          ${t("cameras.recordings.refresh", "Refresh")}
        </button>
      </div>
      ${recordings.length
        ? html`<ul style="list-style:none;margin:0;padding:0">
            ${recordings.map(function (r) {
              const playable = canPlayRecording(r.name);
              const isActive = activeName === r.name;
              return html`<li
                style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid var(--border)"
              >
                <div style="min-width:0">
                  <strong class="mono" style="word-break:break-all">${r.name}</strong>
                  <p class="sub" style="margin:2px 0 0">
                    ${fmtTs(r.mtime)} · ${fmtBytes(r.size)}
                  </p>
                </div>
                <div class="row" style="margin:0;gap:6px;flex-shrink:0">
                  ${playable
                    ? html`<button
                        type="button"
                        class="btn ${isActive ? "primary" : ""}"
                        ?disabled=${!!state.cameraPlaybackLoading && !isActive}
                        @click=${async function () {
                          if (isActive) {
                            await backToLive(startCameraLive);
                            return;
                          }
                          await playRecording(r.name);
                        }}
                      >
                        ${isActive
                          ? t("cameras.play.live", "Back to live")
                          : t("cameras.play", "Play")}
                      </button>`
                    : nothing}
                  <a
                    class="btn"
                    href=${"/api/dvr/recordings/" + encodeURIComponent(r.name)}
                    download=${r.name}
                    >${t("cameras.download", "Download")}</a
                  >
                  <button
                    type="button"
                    class="btn ghost"
                    @click=${async function () {
                      if (
                        !confirm(t("cameras.delete.confirm", "Delete this recording?"))
                      )
                        return;
                      if (activeName === r.name) {
                        await backToLive(startCameraLive);
                      }
                      await api("/api/dvr/recordings/" + encodeURIComponent(r.name), {
                        method: "DELETE",
                      });
                      await loadRecordings();
                    }}
                  >
                    ${t("cameras.delete", "Delete")}
                  </button>
                </div>
              </li>`;
            })}
          </ul>`
        : html`<p class="persist-note">
            ${t("cameras.recordings.empty", "No recordings yet")}
          </p>`}
    </div>
  `;
}
