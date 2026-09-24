import { html } from "../lit.js";
import { t } from "../i18n.js";
import { entitiesByGroup, groupByEntity } from "../store.js";
import { entityGrid, entityLabel } from "../ui/cards.js";

var FAMILY_ORDER = [
  "drive_mode",
  "regen",
  "steering",
  "brake",
  "climate",
  "seat",
  "energy",
  "charging",
  "adas",
  "lock",
  "light",
  "window",
  "hud",
  "sensor",
  "android",
  "extra",
];

export function familySections(items) {
  if (!items || !items.length) {
    return entityGrid(items);
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
      <h2 class="section-label" style="margin:20px 0 10px">${entityLabel(fam)}</h2>
      ${entityGrid(buckets[fam])}
    `;
  });
}

export function sectionGroup(title, sub, group) {
  return html`
    <h1>${title}</h1>
    ${sub ? html`<p class="sub">${sub}</p>` : ""}
    ${familySections(entitiesByGroup(group))}
  `;
}
