import { html, nothing } from "../../lit.js";
import { fmt } from "../../api.js";
import { t, entityValueLabel } from "../../i18n.js";
import { setControl } from "../../actions.js";
import { formatDisplayNumber } from "../../units.js";
import { displayUnit, pinSnapshot, pinChip } from "./shared.js";
import { boolToggle } from "./bool.js";
import { segmentToggle } from "./choice.js";
import { onStep } from "./number.js";

export function inputWidget(c) {
  const locked = c.status !== "ok" && c.status !== "cached";
  const val = c.value;
  const id = c.id;
  const input = c.input || "int";
  const pin = pinSnapshot(c);

  if (input === "bool") {
    return boolToggle(
      val,
      function (v) {
        setControl(id, v);
      },
      locked,
      pin,
      "ctrl:" + id,
    );
  }

  if (input === "choice") {
    return segmentToggle({
      options: c.options || [],
      current: val,
      locked: locked,
      pinnedVal: pin,
      choiceKey: "ctrl:" + id,
      onSelect: function (v) {
        setControl(id, v);
      },
    });
  }

  if (input === "command") {
    return html`
      <div class="command-actions">
        ${!locked
          ? html`<div class="lock-note command-note">
              ${t("status.write_only", "Write-only command")}
            </div>`
          : nothing}
        ${segmentToggle({
          options: c.options || [],
          current: null,
          locked: locked,
          choiceKey: "ctrl:" + id + ":cmd",
          onSelect: function (v) {
            setControl(id, v);
          },
        })}
      </div>
    `;
  }

  if (input === "int" || input === "float") {
    const step = c.step != null ? c.step : input === "float" ? 0.5 : 1;
    const num = parseFloat(val);
    const unitId = c.unitOfMeasurement || null;
    const display = isNaN(num)
      ? "—"
      : formatDisplayNumber(unitId, num, input);
    const pinNum = pin != null ? parseFloat(pin) : NaN;
    const match = pin != null && !isNaN(num) && !isNaN(pinNum) && num === pinNum;
    const pinClass = pin == null ? "" : match ? " pin-match" : " pin-diff";
    const pinLabel =
      pin == null || match
        ? ""
        : isNaN(pinNum)
          ? String(pin)
          : formatDisplayNumber(unitId, pinNum, input);
    return html`
      <div class="stepper${pinClass}">
        <button type="button" ?disabled=${locked} @click=${function () {
          onStep(id, -step);
        }}>
          −
        </button>
        <span class="val" data-step-val=${id}>
          ${display}${displayUnit(c) && !isNaN(num)
            ? html`<span class="unit">${displayUnit(c)}</span>`
            : nothing}
        </span>
        <button type="button" ?disabled=${locked} @click=${function () {
          onStep(id, step);
        }}>
          +
        </button>
        ${pinClass === " pin-diff"
          ? pinChip(pinLabel + (displayUnit(c) ? " " + displayUnit(c) : ""))
          : nothing}
      </div>
    `;
  }

  if (input === "text") {
    const match = pin != null && String(val || "") === String(pin);
    return html`
      <div
        class="select-wrap ${pin == null ? "" : match ? "pin-match" : "pin-diff"}"
      >
        <input
          type="text"
          class="field"
          .value=${val || ""}
          ?disabled=${locked}
          @change=${function (ev) {
            setControl(id, ev.target.value);
          }}
        />
        ${pin != null && !match ? pinChip(String(pin)) : nothing}
      </div>
    `;
  }

  return html`<span class="mono"
    >${entityValueLabel(c) ||
      (function () {
        const n = parseFloat(val);
        if (!isNaN(n) && c.unitOfMeasurement) {
          return formatDisplayNumber(c.unitOfMeasurement, n, input);
        }
        return fmt(val);
      })()}${displayUnit(c) ? " " + displayUnit(c) : ""}</span
  >`;
}
