import { html, nothing } from "../lit.js";
import { t } from "../i18n.js";
import { state, entitiesByGroup } from "../store.js";
import { familySections } from "./group.js";
import { prefCard, prefBool } from "../ui/cards.js";
import { api } from "../api.js";

function fmtBytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return v + " B";
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + " KB";
  if (v < 1024 * 1024 * 1024) return (v / (1024 * 1024)).toFixed(1) + " MB";
  return (v / (1024 * 1024 * 1024)).toFixed(2) + " GB";
}

function volumeLabel(v) {
  if (!v) return "";
  if (v.labelKey === "cameras.storage.usb.none") {
    return t("cameras.storage.usb.none", "USB / Flash (none)");
  }
  if (v.labelKey === "cameras.storage.sd" || v.labelKey === "cameras.storage.usb") {
    return t(
      v.labelKey,
      v.labelKey === "cameras.storage.usb" ? "USB / Flash ({label})" : "SD ({label})",
    ).replace("{label}", v.label || "");
  }
  return t(v.labelKey || "cameras.storage.app", v.label || v.id || "");
}

export function sectionConnect() {
  const adb = state.adb || {};
  const volumes =
    (state.status && state.status.storage && state.status.storage.volumes) ||
    (state.status && state.status.dvr && state.status.dvr.storages) ||
    [];
  const adbBody = html`
    ${prefBool("adb", adb.enabled)}
    ${adb.enabled && adb.port
      ? html`<p class="persist-note" style="margin:10px 0 0">
          ${t("system.adb.port", "Porta")} ${adb.port}
        </p>`
      : nothing}
    ${adb.canToggle === false
      ? html`<p class="persist-note">
          ${t("system.adb.unavailable", "Toggle unavailable on this build")}
        </p>`
      : nothing}
  `;

  const storageBody = volumes.length
    ? html`<ul style="list-style:none;margin:0;padding:0">
        ${volumes.map(function (v) {
          const total = Number(v.totalBytes) || 0;
          const free = Number(v.freeBytes) || 0;
          const used = total > 0 ? Math.max(0, total - free) : 0;
          const pct = total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0;
          return html`<li style="padding:8px 0;border-bottom:1px solid var(--border)">
            <div class="row" style="justify-content:space-between;margin:0 0 4px;gap:8px">
              <strong style="min-width:0">${volumeLabel(v)}</strong>
              ${v.writable === false
                ? html`<span class="badge warn">${t("android.storage.readonly", "Read-only")}</span>`
                : nothing}
            </div>
            <div class="storage-bar" title=${pct + "%"}>
              <div class="storage-bar-fill" style="width:${pct}%"></div>
            </div>
            <p class="sub" style="margin:4px 0 0">
              ${t("android.storage.used_free", "{used} used · {free} free of {total}")
                .replace("{used}", fmtBytes(used))
                .replace("{free}", fmtBytes(free))
                .replace("{total}", fmtBytes(total))}
            </p>
          </li>`;
        })}
      </ul>`
    : html`<p class="persist-note">${t("android.storage.empty", "No volumes reported")}</p>`;

  return html`
    <h1>${t("section.connect.title", "Conexão")}</h1>
    ${familySections(entitiesByGroup("connect"))}
    <div class="grid" style="margin-top:18px">
      ${prefCard({
        icon: "usb",
        title: t("system.adb.title", "ADB sem fio"),
        body: adbBody,
      })}
      ${prefCard({
        icon: "system",
        title: t("android.storage", "Storage"),
        body: storageBody,
      })}
      ${prefCard({
        icon: "system",
        title: t("system.android_settings", "Configurações do Android"),
        body: html`<button
          class="btn primary"
          style="width:100%"
          @click=${async function () {
            await api("/api/system/open-android-settings", { method: "POST" });
          }}
        >
          ${t("system.android_settings", "Configurações do Android")}
        </button>`,
      })}
    </div>
  `;
}
