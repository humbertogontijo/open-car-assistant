import { html, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";

export function sectionAbout() {
  const s = state.status || {};
  const setup = state.setup || {};
  const rows = [
    ["integration", s.integration],
    ["variant", s.variant],
    ["webPort", s.webPort],
    ["privileged", setup.privilegedOk ? "ok" : "optional"],
  ].filter(function (r) {
    return r[1] != null && r[1] !== "";
  });

  const tip =
    setup && !setup.privilegedOk
      ? html`<p class="persist-note" style="margin:0 0 1rem">
          ${t(
            "about.tip.privileged",
            "Privileged install is optional — only if Climate or vendor writes fail.",
          )}
          <a
            href="#"
            @click=${function (ev) {
              ev.preventDefault();
              patch({ showSetup: true });
            }}
            >${t("setup.open", "Open setup")}</a
          >
        </p>`
      : nothing;

  return html`
    <h1>${t("nav.about", "Sobre")}</h1>
    ${tip}
    <div class="card">
      <p style="margin:0 0 12px">
        ${t("about.blurb", "Open Car Assistant — shell unificado HU + web.")}
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
