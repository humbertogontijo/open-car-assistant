import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/climate.css");

import { html, nothing } from "../../lit.js";
import { t } from "../../i18n.js";
import { setControl, setPersist } from "../../actions.js";
import { formatDisplayNumber } from "../../units.js";
import { icon, displayUnit, pinSnapshot, hideBtn, cardSpan } from "./shared.js";

function climateAttr(c, snake) {
  const attrs = c.attributes || {};
  if (attrs[snake] != null && attrs[snake] !== "") return attrs[snake];
  if (c[snake] != null && c[snake] !== "") return c[snake];
  return null;
}

function climateModeLabel(mode) {
  const m = String(mode || "").toLowerCase();
  if (m === "off") return t("climate.mode.off", "Off");
  if (m === "auto") return t("climate.mode.auto", "Auto");
  if (m === "cool") return t("climate.mode.cool", "Cool");
  if (m === "heat") return t("climate.mode.heat", "Heat");
  if (m === "fan_only") return t("climate.mode.fan_only", "Fan");
  return mode || "—";
}

/** Prefer attributes.hvac_mode; unknown values fall back to off. */
function climateMode(c) {
  const fromAttr = climateAttr(c, "hvac_mode");
  if (fromAttr != null && fromAttr !== "") return String(fromAttr).toLowerCase();
  const raw = c.value != null && c.value !== "" ? c.value : c.state;
  if (raw == null || raw === "") return "off";
  const s = String(raw).toLowerCase();
  if (
    s === "off" ||
    s === "on" ||
    s === "auto" ||
    s === "cool" ||
    s === "heat" ||
    s === "fan_only" ||
    s === "defrost"
  ) {
    return s;
  }
  return "off";
}

export function climateCard(c, restore) {
  const mode = climateMode(c);
  const powered = mode !== "off";
  const locked = c.status !== "ok" && c.status !== "cached";
  const modesRaw = climateAttr(c, "hvac_modes");
  const modes = Array.isArray(modesRaw)
    ? modesRaw.map(String)
    : ["off", "auto", "cool", "heat", "fan_only"];
  const tempMin = Number(
    climateAttr(c, "min_temp") != null ? climateAttr(c, "min_temp") : c.min != null ? c.min : 16,
  );
  const tempMax = Number(
    climateAttr(c, "max_temp") != null ? climateAttr(c, "max_temp") : c.max != null ? c.max : 32,
  );
  const tempStep = Number(
    climateAttr(c, "target_temp_step") != null
      ? climateAttr(c, "target_temp_step")
      : c.step != null
        ? c.step
        : 0.5,
  );
  const tempRaw = climateAttr(c, "temperature");
  const temp =
    tempRaw != null && tempRaw !== "" && !isNaN(Number(tempRaw))
      ? Number(tempRaw)
      : null;
  const currentRaw = climateAttr(c, "current_temperature");
  const current =
    currentRaw != null && currentRaw !== "" && !isNaN(Number(currentRaw))
      ? Number(currentRaw)
      : null;
  const fanRaw = climateAttr(c, "fan_mode");
  const fan =
    fanRaw != null && fanRaw !== "" && !isNaN(Number(fanRaw)) ? Number(fanRaw) : 0;
  const fanModesRaw = climateAttr(c, "fan_modes");
  const fanMax = Array.isArray(fanModesRaw) && fanModesRaw.length
    ? Math.max.apply(null, fanModesRaw.map(Number).filter(function (n) { return !isNaN(n); }))
    : 8;
  const acOn = Number(climateAttr(c, "ac") || 0) !== 0;
  const recircOn = Number(climateAttr(c, "recirc") || 0) !== 0;
  const unit = displayUnit(c) || "°C";
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");
  const span = cardSpan(c);

  function send(cmd) {
    if (locked) return;
    setControl(c.id, cmd);
  }

  function nudgeTemp(delta) {
    if (locked || temp == null) return;
    let next = temp + delta;
    if (!isNaN(tempMin)) next = Math.max(tempMin, next);
    if (!isNaN(tempMax)) next = Math.min(tempMax, next);
    // Avoid float drift (e.g. 22.5000001)
    next = Math.round(next / tempStep) * tempStep;
    send("temperature:" + next);
  }

  function onFanInput(ev) {
    if (locked) return;
    const n = parseInt(ev.target.value, 10);
    if (isNaN(n)) return;
    send("fan_mode:" + n);
  }

  const modeChips = modes.map(function (m) {
    const active = m === mode;
    return html`
      <button
        type="button"
        class="climate-mode-chip ${active ? "active" : ""}"
        ?disabled=${locked}
        aria-pressed=${active ? "true" : "false"}
        @click=${function () {
          send(m === "off" || m === "on" ? m : "hvac_mode:" + m);
        }}
      >
        ${climateModeLabel(m)}
      </button>
    `;
  });

  return html`
    <div
      class="ctrl-card climate-card ${powered ? "is-on" : ""} ${locked ? "locked" : ""} ${pinned ? "pinned" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || "climate")}</div>
        <div class="ctrl-meta">
          <h3>${c.label}</h3>
          <p class="hint climate-state">${climateModeLabel(mode)}</p>
        </div>
        <div class="card-actions">
          ${hideBtn(c.id, restore)}
          ${restore
            ? nothing
            : html`
          <button
            type="button"
            class="pin-btn ${pinned ? "active" : ""}"
            title=${pinTitle}
            aria-label=${pinTitle}
            aria-pressed=${pinned ? "true" : "false"}
            ?disabled=${!pinned && !mode}
            @click=${function (ev) {
              ev.stopPropagation();
              if (pinned) {
                setPersist(c.id, { enabled: false });
                return;
              }
              const pinVal =
                temp != null
                  ? "hvac_mode:" + mode + ";temperature:" + temp
                  : mode;
              setPersist(c.id, { enabled: true, value: pinVal });
            }}
          >
            ${icon("pin")}
          </button>`}
        </div>
      </div>
      <div class="ctrl-body climate-body">
        <div class="climate-modes" role="group" aria-label=${t("climate.modes", "HVAC mode")}>
          ${modeChips}
        </div>
        <div class="climate-temp-row">
          <div class="climate-temp-readout">
            <span class="climate-temp-target">
              ${temp != null ? formatDisplayNumber(c.unitOfMeasurement, temp, "float") : "—"}
              <span class="unit">${unit}</span>
            </span>
            ${current != null
              ? html`<span class="climate-temp-current">
                  ${t("climate.current", "Now")}
                  ${formatDisplayNumber(c.unitOfMeasurement, current, "sensor")}${unit}
                </span>`
              : nothing}
          </div>
          <div class="climate-temp-stepper" role="group" aria-label=${t("control.hvac_temp", "Temperature")}>
            <button
              type="button"
              class="climate-step-btn"
              ?disabled=${locked || temp == null}
              @click=${function () { nudgeTemp(-tempStep); }}
            >−</button>
            <button
              type="button"
              class="climate-step-btn"
              ?disabled=${locked || temp == null}
              @click=${function () { nudgeTemp(tempStep); }}
            >+</button>
          </div>
        </div>
        <div
          class="climate-fan-slider"
          role="group"
          aria-label=${t("control.hvac_fan", "Fan")}
        >
          <span class="climate-fan-label">${fan}<span class="unit">/${fanMax}</span></span>
          <input
            type="range"
            class="media-range"
            min="0"
            max=${fanMax}
            step="1"
            .value=${String(fan)}
            ?disabled=${locked}
            @change=${onFanInput}
            @input=${function (ev) {
              const label = ev.target.parentElement &&
                ev.target.parentElement.querySelector(".climate-fan-label");
              if (label) {
                label.innerHTML =
                  ev.target.value + '<span class="unit">/' + fanMax + "</span>";
              }
            }}
          />
        </div>
        <div class="climate-toggles" role="group" aria-label=${t("climate.toggles", "Climate options")}>
          <button
            type="button"
            class="climate-toggle ${acOn ? "active" : ""}"
            ?disabled=${locked}
            aria-pressed=${acOn ? "true" : "false"}
            @click=${function () { send("ac:" + (acOn ? "0" : "1")); }}
          >${t("control.hvac_ac", "A/C")}</button>
          <button
            type="button"
            class="climate-toggle ${recircOn ? "active" : ""}"
            ?disabled=${locked}
            aria-pressed=${recircOn ? "true" : "false"}
            @click=${function () { send("recirc:" + (recircOn ? "0" : "1")); }}
          >${t("control.hvac_recirc", "Recirc")}</button>
          <button
            type="button"
            class="climate-toggle ${powered ? "active" : ""}"
            ?disabled=${locked}
            aria-pressed=${powered ? "true" : "false"}
            @click=${function () { send(powered ? "off" : "on"); }}
          >${powered ? t("climate.power.off", "Off") : t("climate.power.on", "On")}</button>
        </div>
      </div>
    </div>
  `;
}
