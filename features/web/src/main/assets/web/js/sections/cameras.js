import { html, nothing } from "../lit.js";
import { state, patch, notify } from "../store.js";
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

/** Client-side recording clock (status only refreshes every ~3s). */
let recSyncAt = 0;
let recSyncElapsedMs = 0;
/** @type {ReturnType<typeof setInterval>|null} */
let recTickTimer = null;

function stopRecTick() {
  if (recTickTimer) {
    clearInterval(recTickTimer);
    recTickTimer = null;
  }
  recSyncAt = 0;
  recSyncElapsedMs = 0;
}

function ensureRecTick() {
  if (recTickTimer) return;
  recTickTimer = setInterval(function () {
    const sec = state.section;
    const dvr = state.status && state.status.dvr;
    if ((sec !== "cameras" && sec !== "dvr") || !dvr || !dvr.recording) {
      stopRecTick();
      notify();
      return;
    }
    notify();
  }, 1000);
}

/** Smooth session elapsed; resyncs from server when status arrives. */
function recordingElapsedMs(dvr) {
  if (!dvr || !dvr.recording) {
    stopRecTick();
    return 0;
  }
  const serverMs = Number(dvr.elapsedMs);
  const serverOk = Number.isFinite(serverMs) && serverMs >= 0;
  const fallback = Number(dvr.segmentElapsedMs) || 0;
  const base = serverOk ? serverMs : fallback;
  const now = Date.now();
  if (!recSyncAt) {
    recSyncAt = now;
    recSyncElapsedMs = base;
  } else {
    const local = recSyncElapsedMs + (now - recSyncAt);
    // Resync on poll when drift is noticeable (clock skew / late status).
    if (serverOk && Math.abs(local - serverMs) > 1500) {
      recSyncAt = now;
      recSyncElapsedMs = serverMs;
    }
  }
  ensureRecTick();
  return recSyncElapsedMs + (Date.now() - recSyncAt);
}
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
  if (v < 1024 * 1024 * 1024) return (v / (1024 * 1024)).toFixed(1) + " MB";
  return (v / (1024 * 1024 * 1024)).toFixed(2) + " GB";
}

function storageLabel(s) {
  if (!s) return "";
  if (s.labelKey === "cameras.storage.usb.none") {
    return t("cameras.storage.usb.none", "USB / Flash (none)");
  }
  if (s.labelKey === "cameras.storage.sd" || s.labelKey === "cameras.storage.usb") {
    const base = t(
      s.labelKey,
      s.labelKey === "cameras.storage.usb" ? "USB / Flash ({label})" : "SD ({label})",
    ).replace("{label}", s.label || "");
    if (s.available === false || s.writable === false) {
      return base + " — " + t("cameras.storage.unavailable", "Not available");
    }
    return base;
  }
  return t(s.labelKey || "cameras.storage.app", s.label || s.id);
}

function spaceBar(used, total) {
  const tot = Number(total) || 0;
  const u = Number(used) || 0;
  const pct = tot > 0 ? Math.min(100, Math.round((u / tot) * 100)) : 0;
  return html`
    <div class="storage-bar" title=${pct + "%"}>
      <div class="storage-bar-fill" style="width:${pct}%"></div>
    </div>
  `;
}

async function setCamMode(mode) {
  const storage =
    (state.status && state.status.dvr && state.status.dvr.storageId) || "";
  await api("/api/dvr/mode", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body:
      "mode=" +
      encodeURIComponent(mode) +
      "&storage=" +
      encodeURIComponent(storage || ""),
  });
  try {
    const s = await api("/api/status");
    patch({ status: s });
  } catch (e) {
    patch({});
  }
  await loadRecordings();
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
    state.cameraPreviewSrc.indexOf("live.m3u8") >= 0
  ) {
    return;
  }
  try {
    const start = await api("/api/dvr/preview/start", { method: "POST" });
    if (!start || start.ok === false) {
      throw new Error(
        (start && start.status && start.status.lastError) || "preview start failed",
      );
    }
    patch({
      cameraPreviewActive: true,
      cameraPreviewSrc: "/api/dvr/live.m3u8",
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
  const { stopH264Live } = await import("../ui/live-h264.js");
  stopH264Live(document.getElementById("cameraPlayerVideo"));
  patch({
    cameraPreviewActive: false,
    cameraPreviewSrc: "",
    cameraPlayerMode: "live",
    cameraPlayingName: "",
    cameraPreviewError: "",
  });
  try {
    await api("/api/dvr/preview/stop", { method: "POST" });
  } catch (e) {}
}

export { applyCameraPlayerSrc };

export function sectionCameras() {
  const dvr = (state.status && state.status.dvr) || {};
  const mode = dvr.mode || (dvr.recording ? "segment" : "off");
  const segmentActive = mode === "segment" || (mode !== "dvr" && !!dvr.recording);
  const dvrActive = mode === "dvr";
  const storages = dvr.storages || [];
  const storageId = dvr.storageId || "app";
  const selected =
    storages.find(function (s) {
      return s.id === storageId;
    }) ||
    storages[0] ||
    {};
  const storageOpts = storages.map(function (s) {
    const unavailable = s.available === false || s.writable === false;
    return {
      value: s.id,
      label: storageLabel(s),
      disabled: unavailable,
      title: unavailable
        ? t("cameras.storage.usb.hint", "Plug in a USB flash drive to save recordings here")
        : undefined,
    };
  });
  if (!storageOpts.length) {
    storageOpts.push({ value: "app", label: t("cameras.storage.app", "App") });
  }
  const policy = dvr.policy || {};
  const maxTotalMb = Number(policy.maxTotalMb) || 2048;
  const maxAgeDays = Number(policy.maxAgeDays) || 0;
  const sizeOpts = [512, 1024, 2048, 4096, 8192].map(function (mb) {
    return { value: String(mb), label: mb >= 1024 ? mb / 1024 + " GB" : mb + " MB" };
  });
  const ageOpts = [
    { value: "0", label: t("cameras.policy.age.off", "Off") },
    { value: "1", label: t("cameras.policy.age.days", "{n} days").replace("{n}", "1") },
    { value: "3", label: t("cameras.policy.age.days", "{n} days").replace("{n}", "3") },
    { value: "7", label: t("cameras.policy.age.days", "{n} days").replace("{n}", "7") },
    { value: "14", label: t("cameras.policy.age.days", "{n} days").replace("{n}", "14") },
    { value: "30", label: t("cameras.policy.age.days", "{n} days").replace("{n}", "30") },
  ];
  const freeB = Number(selected.freeBytes) || Number(dvr.selectedFreeBytes) || 0;
  const totalB = Number(selected.totalBytes) || Number(dvr.selectedTotalBytes) || 0;
  const usedB = totalB > 0 ? Math.max(0, totalB - freeB) : 0;
  const usageBytes = Number(dvr.usageBytes) || 0;
  const usageCount = Number(dvr.usageCount) || 0;
  const capBytes = maxTotalMb * 1024 * 1024;
  const recordings = state.recordings || [];
  const activeName = state.cameraPlayingName || "";
  const elapsedMs = recordingElapsedMs(dvr);
  const elapsedSec = Math.floor(elapsedMs / 1000);
  const elapsedLabel =
    elapsedSec > 0
      ? Math.floor(elapsedSec / 60) + ":" + String(elapsedSec % 60).padStart(2, "0")
      : "";

  return html`
    <h1>${t("section.cameras.title", "Câmeras")}</h1>

    <div class="cameras-hero">
      ${cameraPlayerView({
        lastError: dvr.lastError || "",
        startLive: startCameraLive,
        embedded: true,
      })}

      <div class="cameras-side">
        ${prefCard({
          icon: "camera",
          title: t("cameras.record", "Record"),
          body: html`
            <p class="sub" style="margin:0 0 10px">
              ${t(
                "cameras.record.hint",
                "One clip up to 5 min / 100 MB, then stops.",
              )}
            </p>
            ${segmentActive
              ? html`<button
                    type="button"
                    class="btn primary"
                    style="width:100%"
                    @click=${async function () {
                      await setCamMode("off");
                    }}
                  >
                    ${t("cameras.rec_stop", "Stop recording")}
                    ${elapsedLabel ? " · " + elapsedLabel : ""}
                  </button>`
              : html`<button
                    type="button"
                    class="btn"
                    style="width:100%"
                    ?disabled=${dvrActive}
                    @click=${async function () {
                      await setCamMode("segment");
                    }}
                  >
                    ${t("cameras.rec_start", "Start recording")}
                  </button>`}
            ${dvrActive
              ? html`<p class="sub" style="margin:8px 0 0">
                  ${t("cameras.record.dvr_blocks", "Disable DVR to record a single clip.")}
                </p>`
              : nothing}
          `,
        })}

        ${prefCard({
          icon: "camera",
          title: t("cameras.mode.dvr", "DVR"),
          body: html`
            <p class="sub" style="margin:0 0 10px">
              ${t(
                "cameras.mode.dvr.hint",
                "Continuous recording. Starts on ACC/boot, stops on sleep.",
              )}
            </p>
            ${dvrActive
              ? html`<button
                    type="button"
                    class="btn primary"
                    style="width:100%"
                    @click=${async function () {
                      await setCamMode("off");
                    }}
                  >
                    ${t("cameras.dvr.disable", "Disable DVR")}
                    ${elapsedLabel ? " · " + elapsedLabel : ""}
                  </button>`
              : html`<button
                    type="button"
                    class="btn"
                    style="width:100%"
                    ?disabled=${segmentActive}
                    @click=${async function () {
                      await setCamMode("dvr");
                    }}
                  >
                    ${t("cameras.dvr.enable", "Enable DVR")}
                  </button>`}
            ${segmentActive
              ? html`<p class="sub" style="margin:8px 0 0">
                  ${t("cameras.dvr.segment_blocks", "Stop the clip first to enable DVR.")}
                </p>`
              : nothing}
          `,
        })}
      </div>
    </div>

    <div class="cameras-meta">
      <div class="card">
        <h2 style="margin:0 0 8px">${t("cameras.storage", "Salvar em")}</h2>
        ${prefSegment("cam-storage", storageOpts, storageId)}
        ${dvr.storageNote
          ? html`<p class="sub" style="color:var(--warn);margin:8px 0 0">${dvr.storageNote}</p>`
          : nothing}
        <div style="margin-top:12px">
          ${spaceBar(usedB, totalB)}
          <p class="sub" style="margin:6px 0 0">
            ${t("cameras.storage.used_free", "{used} used · {free} free of {total}")
              .replace("{used}", fmtBytes(usedB))
              .replace("{free}", fmtBytes(freeB))
              .replace("{total}", fmtBytes(totalB))}
          </p>
          <p class="sub" style="margin:2px 0 0">
            ${t("cameras.storage.clips", "Recordings: {used} in {count} files (cap {cap})")
              .replace("{used}", fmtBytes(usageBytes))
              .replace("{count}", String(usageCount))
              .replace("{cap}", fmtBytes(capBytes))}
          </p>
        </div>
      </div>
      <div class="card">
        <h2 style="margin:0 0 8px">${t("cameras.policy", "Retention")}</h2>
        <p class="sub" style="margin:0 0 6px">${t("cameras.policy.max_size", "Max total size")}</p>
        ${prefSegment("cam-retention-size", sizeOpts, String(maxTotalMb))}
        <p class="sub" style="margin:10px 0 6px">${t("cameras.policy.max_age", "Max age")}</p>
        ${prefSegment("cam-retention-age", ageOpts, String(maxAgeDays))}
      </div>
    </div>

    <div class="card">
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
              const locked = !!r.locked;
              return html`<li
                style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid var(--border)"
              >
                <div style="min-width:0">
                  <strong class="mono" style="word-break:break-all">${r.name}</strong>
                  ${locked
                    ? html` <span class="badge accent">${t("cameras.lock.badge", "Locked")}</span>`
                    : nothing}
                  <p class="sub" style="margin:2px 0 0">
                    ${fmtTs(r.mtime)} · ${fmtBytes(r.size)}
                  </p>
                </div>
                <div class="row" style="margin:0;gap:6px;flex-shrink:0;flex-wrap:wrap">
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
                          await playRecording(r.name, {
                            durationMs: r.durationMs,
                            frameCount: r.frameCount,
                          });
                        }}
                      >
                        ${isActive
                          ? t("cameras.play.live", "Back to live")
                          : t("cameras.play", "Play")}
                      </button>`
                    : nothing}
                  <button
                    type="button"
                    class="btn ghost"
                    @click=${async function () {
                      await api(
                        "/api/dvr/recordings/" + encodeURIComponent(r.name) + "/lock",
                        {
                          method: "POST",
                          headers: {
                            "Content-Type": "application/x-www-form-urlencoded",
                          },
                          body: "locked=" + (locked ? "0" : "1"),
                        },
                      );
                      await loadRecordings();
                    }}
                  >
                    ${locked
                      ? t("cameras.unlock", "Unlock")
                      : t("cameras.lock", "Lock")}
                  </button>
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
