import { html, nothing } from "../lit.js";
import { t } from "../i18n.js";
import { state, entitiesByGroup } from "../store.js";
import { entityGrid, prefCard, prefBool } from "../ui/cards.js";
import { api } from "../api.js";

export function sectionAndroid() {
  const adb = state.adb || {};
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

  return html`
    <h1>${t("section.android.title", "Android")}</h1>
    ${entityGrid(entitiesByGroup("android"))}
    <div class="grid" style="margin-top:18px">
      ${prefCard({
        icon: "usb",
        title: t("system.adb.title", "ADB sem fio"),
        body: adbBody,
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
