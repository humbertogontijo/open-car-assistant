import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/bool.css");

import { t } from "../../i18n.js";
import { segmentToggle } from "./choice.js";

export function isOn(v) {
  return v === "1" || v === "true" || v === true || v === 1 || v === "on";
}

export function boolOpts() {
  return [
    { value: "0", label: t("value.off", "Off") },
    { value: "1", label: t("value.on", "On") },
  ];
}

export function boolVal(val) {
  return isOn(val) ? "1" : "0";
}

export function boolToggle(current, onSelect, locked, pinnedVal, choiceKey) {
  return segmentToggle({
    options: boolOpts(),
    current: boolVal(current),
    locked: locked,
    pinnedVal: pinnedVal,
    choiceKey: choiceKey,
    onSelect: onSelect,
  });
}
