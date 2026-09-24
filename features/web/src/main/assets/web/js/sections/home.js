import { html, nothing } from "../lit.js";
import { state } from "../store.js";
import { t } from "../i18n.js";
import { entityGrid } from "../ui/cards.js";

export function sectionHome() {
  const home = state.entities.filter(function (e) {
    if (e.group === "home" && e.entity === "sensor") return e.status === "ok";
    return e.entity === "drive_mode" || e.entity === "regen";
  });
  const histN = (state.historyEntities || []).length;
  return html`
    <h1>${t("section.home.title", "Início")}</h1>
    ${entityGrid(home)}
    <div class="card" style="margin-top:18px">
      <h2>${t("section.history.title", "History")}</h2>
      <p class="sub" style="margin:0 0 12px">
        ${histN
          ? t("history.home.summary", "{n} entities tracked").replace("{n}", String(histN))
          : t("history.empty", "No history yet")}
      </p>
      <button
        class="btn primary"
        type="button"
        @click=${function () {
          if (typeof window.__ocaGoSection === "function") window.__ocaGoSection("history");
        }}
      >
        ${t("history.open", "Open history")}
      </button>
    </div>
  `;
}
