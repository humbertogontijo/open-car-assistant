import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/sensor.css");

import { html, nothing } from "../../lit.js";
import { fmt } from "../../api.js";
import { formatDisplayNumber } from "../../units.js";
import { icon, displayUnit, hideBtn, cardSpan } from "./shared.js";

export function sensorDisplay(c) {
  if (c.valueLabel) return c.valueLabel;
  const n = parseFloat(c.value);
  if (!isNaN(n) && c.unitOfMeasurement) {
    return formatDisplayNumber(c.unitOfMeasurement, n, c.input || "sensor");
  }
  return fmt(c.value);
}

export function sensorCard(c, restore) {
  const span = cardSpan(c);
  return html`
    <div
      class="ctrl-card sensor-card"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || "sensor")}</div>
        <div class="ctrl-meta">
          <h3>${c.label}</h3>
          ${c.hint || c.description
            ? html`<p class="hint">${c.hint || c.description}</p>`
            : nothing}
        </div>
        <div class="card-actions">${hideBtn(c.id, restore)}</div>
      </div>
      <div class="ctrl-body">
        <div class="entity-value">
          ${sensorDisplay(c)}${displayUnit(c)
            ? html`<span class="unit">${displayUnit(c)}</span>`
            : nothing}
        </div>
      </div>
    </div>
  `;
}
