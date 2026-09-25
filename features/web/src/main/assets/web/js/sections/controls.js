import { html } from "../lit.js";
import {
  entitiesByGroup,
  hiddenEntitiesByGroup,
  isShowingHidden,
  state,
} from "../store.js";
import { t } from "../i18n.js";
import { pageHead } from "../ui/cards.js";
import { familySections } from "./group.js";
import { dashSummary, pickEntities, withoutIds } from "../ui/dashboard.js";

var CLIMATE_HERO_IDS = [
  "sensor_hvac_temp",
  "sensor_temp_indoor",
  "sensor_temp_ambient",
  "hvac_power",
  "hvac_temp",
];

export function sectionControls() {
  const group = "controls";
  const viewing = isShowingHidden(group);
  const items = viewing ? hiddenEntitiesByGroup(group) : entitiesByGroup(group);
  const pool = viewing
    ? items
    : (state.entities || []).filter(function (e) {
        return e.group === "controls" && (e.status === "ok" || e.status === "cached");
      });
  const hero = pickEntities(pool, CLIMATE_HERO_IDS);
  const rest = withoutIds(items, hero.map(function (e) {
    return e.id;
  }));

  return html`
    ${pageHead(
      t("section.controls.title", "Controles"),
      group,
      t("section.controls.sub", "Cabin climate and comfort"),
    )}
    ${viewing
      ? familySections(items, { restore: true })
      : html`
          ${dashSummary(hero, { className: "dash-climate" })}
          <h2 class="section-label" style="margin:20px 0 10px">
            ${t("dash.controls.all", "All controls")}
          </h2>
          ${familySections(rest)}
        `}
  `;
}
