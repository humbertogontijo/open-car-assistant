import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/number.css");

import { findControl } from "../../store.js";
import { faceValue } from "../../persist.js";
import { setControl } from "../../actions.js";

export function onStep(id, delta) {
  const c = findControl(id) || {};
  const curRaw = faceValue(c);
  let cur = parseFloat(curRaw);
  if (isNaN(cur)) cur = c.min != null ? Number(c.min) : 0;
  // Step in vehicle-native units; display layer converts for the UI.
  let next = cur + delta;
  if (delta < 0 && c.min != null) next = Math.max(Number(c.min), next);
  if (delta > 0 && c.max != null) next = Math.min(Number(c.max), next);
  if (delta > 0 && next < cur) next = cur;
  if (delta < 0 && next > cur) next = cur;
  if (next === cur) return;
  if (c.input === "int" || (c.step && Number(c.step) === 1)) next = Math.round(next);
  else next = Math.round(next * 10) / 10;
  setControl(id, String(next));
}
