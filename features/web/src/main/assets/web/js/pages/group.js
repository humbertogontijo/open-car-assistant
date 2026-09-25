import { html } from "../lit.js";
import {
  entitiesByGroup,
  groupByEntity,
  hiddenEntitiesByGroup,
  isShowingHidden,
} from "../store.js";
import { entityGrid, entityLabel, pageHead } from "../ui/cards.js";

var FAMILY_ORDER = [
  "climate",
  "seat",
  "drive_mode",
  "regen",
  "steering",
  "brake",
  "lock",
  "window",
  "energy",
  "charging",
  "adas",
  "light",
  "hud",
  "sensor",
  "device_tracker",
  "media_player",
  "android",
  "extra",
];

export function familySections(items, opts) {
  opts = opts || {};
  if (!items || !items.length) {
    return entityGrid(items, opts);
  }
  var buckets = groupByEntity(items);
  var keys = Object.keys(buckets);
  keys.sort(function (a, b) {
    var ia = FAMILY_ORDER.indexOf(a);
    var ib = FAMILY_ORDER.indexOf(b);
    if (ia < 0) ia = FAMILY_ORDER.length;
    if (ib < 0) ib = FAMILY_ORDER.length;
    if (ia !== ib) return ia - ib;
    return a < b ? -1 : a > b ? 1 : 0;
  });
  return keys.map(function (fam) {
    return html`
      <h2 class="page-label" style="margin:20px 0 10px">${entityLabel(fam)}</h2>
      ${entityGrid(buckets[fam], opts)}
    `;
  });
}

export function pageGroup(title, sub, group) {
  const viewing = isShowingHidden(group);
  const items = viewing
    ? hiddenEntitiesByGroup(group)
    : entitiesByGroup(group);
  return html`
    ${pageHead(title, group, sub)}
    ${familySections(items, viewing ? { restore: true } : null)}
  `;
}
