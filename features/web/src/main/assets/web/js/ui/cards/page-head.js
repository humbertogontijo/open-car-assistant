import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/page-head.css");

import { html, nothing } from "../../lit.js";
import { t } from "../../i18n.js";
import {
  patch,
  hiddenEntitiesByGroup,
  isShowingHidden,
} from "../../store.js";
import { icon } from "./shared.js";

/** Page title row with optional hidden-cards toggle for entity groups. */
export function pageHead(title, group, sub) {
  const hidden = group ? hiddenEntitiesByGroup(group) : [];
  const viewing = group ? isShowingHidden(group) : false;
  const toggle =
    group && hidden.length
      ? html`<button
          type="button"
          class="btn ${viewing ? "" : "ghost"}"
          aria-pressed=${viewing ? "true" : "false"}
          @click=${function () {
            patch({
              showHiddenGroup: viewing ? null : group,
            });
          }}
        >
          ${icon("hide")}
          ${viewing
            ? t("entity.hidden.exit", "Show all")
            : t("entity.hidden.title", "Hidden cards") +
              " (" +
              hidden.length +
              ")"}
        </button>`
      : nothing;

  return html`
    <div class="page-head">
      <h1>${title}</h1>
      ${toggle}
    </div>
    ${sub ? html`<p class="sub">${sub}</p>` : nothing}
    ${viewing
      ? html`<p class="sub">${t("entity.hidden.viewing", "Showing hidden cards only")}</p>`
      : nothing}
  `;
}
