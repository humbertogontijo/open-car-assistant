import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/prefs.css");

import { html, nothing } from "../../lit.js";
import { runPref } from "../../actions.js";
import { icon } from "./shared.js";
import { segmentToggle } from "./choice.js";
import { boolToggle } from "./bool.js";

/** Pref toggle that runs runPref(pref, value, el). */
export function prefSegment(pref, options, current, extra) {
  extra = extra || {};
  return segmentToggle({
    options: options,
    current: current,
    locked: !!extra.locked,
    pinnedVal: extra.pinnedVal,
    choiceKey: extra.choiceKey || "pref:" + pref,
    onSelect: function (val) {
      runPref(pref, val, extra);
    },
  });
}

export function prefBool(pref, current, extra) {
  return boolToggle(
    current,
    function (val) {
      runPref(pref, val, extra);
    },
    extra && extra.locked,
    extra && extra.pinnedVal,
    (extra && extra.choiceKey) || "pref:" + pref,
  );
}

/** Pref / system card shell. body is a TemplateResult or nothing. */
export function prefCard(opts) {
  return html`
    <div class="ctrl-card pref-card">
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(opts.icon || "system")}</div>
        <div class="ctrl-meta">
          <h3>${opts.title}</h3>
          ${opts.sub ? html`<p class="hint">${opts.sub}</p>` : nothing}
        </div>
      </div>
      <div class="ctrl-body">${opts.body || nothing}</div>
    </div>
  `;
}
