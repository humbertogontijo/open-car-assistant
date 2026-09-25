import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/sensor.css");

import { html, nothing } from "../../lit.js";
import { fmt } from "../../api.js";
import { entityLabel, entityHint, entityValueLabel } from "../../i18n.js";
import { formatDisplayNumber } from "../../units.js";
import { icon, displayUnit, hideBtn, cardSpan } from "./shared.js";

export function sensorDisplay(c) {
  const mapped = entityValueLabel(c);
  if (mapped && mapped !== String(c.value != null ? c.value : "")) return mapped;
  // Prefer mapped enum/binary labels; otherwise format numeric + unit.
  if (c.valueMapId || c.binary || (c.options && c.options.length)) {
    if (mapped) return mapped;
  }
  const n = parseFloat(c.value);
  if (!isNaN(n) && c.unitOfMeasurement) {
    return formatDisplayNumber(c.unitOfMeasurement, n, c.input || "sensor");
  }
  if (mapped) return mapped;
  return fmt(c.value);
}

export function sensorCard(c, restore) {
  const span = cardSpan(c);
  const hint = entityHint(c);
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
          <h3>${entityLabel(c)}</h3>
          ${hint ? html`<p class="hint">${hint}</p>` : nothing}
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
