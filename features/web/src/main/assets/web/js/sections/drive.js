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
import { dashSummary, pickEntities, withoutIds, entityById } from "../ui/dashboard.js";

var DRIVE_HERO_IDS = [
  "sensor_speed",
  "sensor_gear",
  "sensor_drive_mode",
  "sensor_regen",
  "drive_mode",
  "regen",
];

export function sectionDrive() {
  const group = "drive";
  const viewing = isShowingHidden(group);
  const items = viewing ? hiddenEntitiesByGroup(group) : entitiesByGroup(group);
  // Status strip pulls home sensors + drive writables.
  const pool = viewing
    ? items
    : (state.entities || []).filter(function (e) {
        return (
          (e.group === "drive" ||
            e.group === "home" ||
            e.id === "sensor_speed" ||
            e.id === "sensor_gear" ||
            e.id === "sensor_drive_mode" ||
            e.id === "sensor_regen") &&
          (e.status === "ok" || e.status === "cached")
        );
      });
  const hero = pickEntities(pool, DRIVE_HERO_IDS);
  // Prefer writable drive_mode/regen in the strip when both sensor + control exist.
  const strip = [];
  const seen = {};
  function add(id) {
    if (seen[id]) return;
    const e = entityById(hero, id) || entityById(pool, id);
    if (e) {
      strip.push(e);
      seen[id] = true;
    }
  }
  add("sensor_speed");
  add("sensor_gear");
  add("drive_mode");
  if (!seen["drive_mode"]) add("sensor_drive_mode");
  add("regen");
  if (!seen["regen"]) add("sensor_regen");

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
          <h2 class="section-label" style="margin:20px 0 10px">
            ${t("dash.drive.controls", "Drive controls")}
          </h2>
          ${familySections(rest)}
        `}
  `;
}
