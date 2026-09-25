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
  "sensor.speed",
  "sensor.gear",
];

export function pageDrive() {
  const group = "drive";
  const viewing = isShowingHidden(group);
  const items = viewing ? hiddenEntitiesByGroup(group) : entitiesByGroup(group);
  // Status strip: telemetry only. Drive composites stay in the card grid below.
  const pool = viewing
    ? items
    : (state.entities || []).filter(function (e) {
        return (
          (e.id === "sensor.speed" || e.id === "sensor.gear") &&
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
