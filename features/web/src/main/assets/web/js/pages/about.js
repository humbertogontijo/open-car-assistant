import { html } from "../lit.js";
import { state } from "../store.js";
import { t } from "../i18n.js";

export function pageAbout() {
  const s = state.status || {};
  const setup = state.setup || {};
  const rows = [
    ["integration", s.integration],
    ["variant", s.variant],
    ["webPort", s.webPort],
    ["accessMode", setup.accessMode],
  ].filter(function (r) {
    return r[1] != null && r[1] !== "";
  });

  return html`
    <h1>${t("nav.about", "Sobre")}</h1>
    <div class="card">
      <p style="margin:0 0 12px">
        ${t("about.blurb", "Open Automotive Assistant — unified HU + web shell.")}
      </p>
      ${rows.map(
        function (r) {
          return html`<div
            style="display:flex;justify-content:space-between;gap:12px;padding:6px 0;border-bottom:1px solid var(--border)"
          >
            <span>${r[0]}</span><span class="mono">${String(r[1])}</span>
          </div>`;
        },
      )}
    </div>
  `;
}
