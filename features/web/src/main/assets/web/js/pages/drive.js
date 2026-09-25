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

var DRIVE_HERO_IDS = [
  "sensor_speed",
  "sensor_gear",
  "drive_mode",
  "regen",
];

export function pageDrive() {
  const group = "drive";
  const viewing = isShowingHidden(group);
  const items = viewing ? hiddenEntitiesByGroup(group) : entitiesByGroup(group);
  // Status strip pulls home sensors + drive writables (one entity per property).
  const pool = viewing
    ? items
    : (state.entities || []).filter(function (e) {
        return (
          (e.group === "drive" ||
            e.id === "sensor_speed" ||
            e.id === "sensor_gear" ||
            e.id === "drive_mode" ||
            e.id === "regen") &&
          (e.status === "ok" || e.status === "cached")
        );
      });
  const strip = pickEntities(pool, DRIVE_HERO_IDS);

  const rest = withoutIds(
    items,
    strip.map(function (e) {
      return e.id;
    }),
  );

  return html`
    ${pageHead(
      t("section.drive.title", "Condução"),
      group,
      t("section.drive.sub", "Drive mode, regen, and chassis"),
    )}
    ${viewing
      ? familySections(items, { restore: true })
      : html`
          ${dashSummary(strip, { className: "dash-drive" })}
          <h2 class="page-label" style="margin:20px 0 10px">
            ${t("dash.drive.controls", "Drive controls")}
          </h2>
          ${familySections(rest)}
        `}
  `;
}
