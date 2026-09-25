import { html, nothing } from "../lit.js";
import {
  entitiesByGroup,
  hiddenEntitiesByGroup,
  isShowingHidden,
} from "../store.js";
import { t } from "../i18n.js";
import { pageHead, entityGrid, entityLabel } from "../ui/cards.js";
import { familySections } from "./group.js";

/** Cabin HVAC + comfort modes + outdoor temp. */
function isClimateRelated(e) {
  if (!e) return false;
  if (e.id === "climate.cabin" || e.id === "climate" || e.id === "sensor.temp_ambient") {
    return true;
  }
  return (e.domain || e.entity) === "climate";
}

/** Exterior covers — windows / sunroof / sunshade / trunk + sibling switches. */
function isCoverRelated(e) {
  if (!e) return false;
  const domain = e.domain || e.entity;
  if (domain === "cover") return true;
  const id = e.id || "";
  return (
    id === "switch.window_lock" ||
    id === "switch.auto_close_window" ||
    id === "switch.sunroof_tilt" ||
    id === "number.trunk_open_height" ||
    id === "switch.mirror_fold" ||
    id === "switch.mirror_auto_fold"
  );
}

function climateSectionOrder(a, b) {
  var rank = function (e) {
    if (e.id === "climate.cabin" || e.id === "climate") return 0;
    if (e.id === "sensor.temp_ambient" || e.id === "sensor_temp_ambient") return 1;
    return 2;
  };
  var d = rank(a) - rank(b);
  if (d !== 0) return d;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

var COVER_ORDER = [
  "cover.window_driver",
  "cover.window_passenger",
  "cover.window_rear_left",
  "cover.window_rear_right",
  "cover.sunroof",
  "cover.sunshade",
  "cover.trunk",
  "switch.sunroof_tilt",
  "switch.window_lock",
  "switch.auto_close_window",
  "number.trunk_open_height",
  "switch.mirror_fold",
  "switch.mirror_auto_fold",
];

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
  const coverItems = [];
  const rest = [];
  for (var i = 0; i < items.length; i++) {
    if (isClimateRelated(items[i])) climateItems.push(items[i]);
    else if (isCoverRelated(items[i])) coverItems.push(items[i]);
    else rest.push(items[i]);
  }
  climateItems.sort(climateSectionOrder);
  coverItems.sort(function (a, b) {
    var da = COVER_ORDER.indexOf(a.id);
    var db = COVER_ORDER.indexOf(b.id);
    if (da < 0) da = COVER_ORDER.length;
    if (db < 0) db = COVER_ORDER.length;
    if (da !== db) return da - db;
    return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
  });

  return html`
    ${pageHead(
      t("section.controls.title", "Controles"),
      group,
      t("section.controls.sub", "Cabin climate, covers, and comfort"),
    )}
    ${climateItems.length
      ? html`
          <h2 class="page-label" style="margin:20px 0 10px">
            ${entityLabel("climate.cabin")}
          </h2>
          ${entityGrid(climateItems)}
        `
      : nothing}
    ${coverItems.length
      ? html`
          <h2 class="page-label" style="margin:20px 0 10px">
            ${t("section.exterior.title", "Exterior")}
          </h2>
          ${entityGrid(coverItems)}
        `
      : nothing}
    ${familySections(rest)}
  `;
}
