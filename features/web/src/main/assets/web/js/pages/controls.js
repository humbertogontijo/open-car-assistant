import { html, nothing } from "../lit.js";
import {
  entitiesByGroup,
  hiddenEntitiesByGroup,
  isShowingHidden,
} from "../store.js";
import { t } from "../i18n.js";
import { pageHead, entityGrid, entityLabel } from "../ui/cards.js";
import { familySections } from "./group.js";

/** Cabin HVAC + comfort modes + outdoor temp (indoor lives on climate.current_temperature). */
function isClimateRelated(e) {
  if (!e) return false;
  if (e.id === "climate" || e.id === "sensor_temp_ambient") return true;
  const domain = e.domain || e.entity;
  return domain === "climate";
}

function climateSectionOrder(a, b) {
  var rank = function (e) {
    if (e.id === "climate") return 0;
    if (e.id === "sensor_temp_ambient") return 1;
    return 2;
  };
  var d = rank(a) - rank(b);
  if (d !== 0) return d;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

export function pageControls() {
  const group = "controls";
  const viewing = isShowingHidden(group);
  const items = viewing ? hiddenEntitiesByGroup(group) : entitiesByGroup(group);

  if (viewing) {
    return html`
      ${pageHead(
        t("section.controls.title", "Controles"),
        group,
        t("section.controls.sub", "Cabin climate and comfort"),
      )}
      ${familySections(items, { restore: true })}
    `;
  }

  const climateItems = [];
  const rest = [];
  for (var i = 0; i < items.length; i++) {
    if (isClimateRelated(items[i])) climateItems.push(items[i]);
    else rest.push(items[i]);
  }
  climateItems.sort(climateSectionOrder);

  return html`
    ${pageHead(
      t("section.controls.title", "Controles"),
      group,
      t("section.controls.sub", "Cabin climate and comfort"),
    )}
    ${climateItems.length
      ? html`
          <h2 class="page-label" style="margin:20px 0 10px">
            ${entityLabel("climate")}
          </h2>
          ${entityGrid(climateItems)}
        `
      : nothing}
    ${familySections(rest)}
  `;
}
