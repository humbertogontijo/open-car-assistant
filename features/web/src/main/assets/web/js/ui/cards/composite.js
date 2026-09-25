import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/composite.css");

import { html, nothing } from "../../lit.js";
import { t, entityLabel, entityHint } from "../../i18n.js";
import { setControl, setPersist } from "../../actions.js";
import { icon, pinSnapshot, hideBtn, cardSpan, statusNote } from "./shared.js";
import { segmentToggle } from "./choice.js";

const META_ATTRS = {
  friendly_name: 1,
  device_class: 1,
  unit_of_measurement: 1,
  icon: 1,
  group: 1,
  input: 1,
  stale: 1,
  history: 1,
  writable: 1,
  composite: 1,
  update: 1,
  virtual: 1,
  virtual_kind: 1,
  open: 1,
  status: 1,
  move: 1,
};

/** Domain → primary choice attribute (segmented control). */
const PRIMARY_CHOICE = {
  drivetrain: "mode",
  steering: "assist_level",
};

const BOOL_CHIPS = {
  esc: 1,
  hdc: 1,
  auto_hold: 1,
  epb: 1,
  parking_brake: 1,
  sync_drive_mode: 1,
  intelligent: 1,
  battery_hold: 1,
  battery_save: 1,
  snow: 1,
  ar: 1,
  active: 1,
  plug: 1,
  switch: 1,
  pre_now: 1,
  v2l: 1,
  v2v: 1,
  parking: 1,
  external_light: 1,
};

const STATUS_ATTRS = {
  gear: 1,
  percent: 1,
  level_raw: 1,
  temp_c: 1,
  hybrid_soc: 1,
  angle: 1,
  display_mode: 1,
  regen: 1,
  battery_mode: 1,
  brake_pedal: 1,
  custom_key: 1,
  estimated_time: 1,
  energy: 1,
  work_current: 1,
  work_voltage: 1,
  current: 1,
  limit: 1,
  soc_max: 1,
  soc_min: 1,
  discharge_soc: 1,
};

function attrLabel(attr) {
  return t("attr." + attr, attr.replace(/_/g, " "));
}

function isOn(v) {
  return v === 1 || v === "1" || v === true || v === "true" || v === "on";
}

function productAttrs(raw) {
  const out = {};
  Object.keys(raw || {}).forEach(function (k) {
    if (META_ATTRS[k]) return;
    if (raw[k] == null || raw[k] === "") return;
    out[k] = raw[k];
  });
  return out;
}

export function compositeCard(c, restore) {
  const locked = c.status !== "ok" && c.status !== "cached";
  const attrs = productAttrs(c.attributes);
  const domain = c.domain || c.entity || c.id;
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");
  const span = cardSpan(c);
  const primaryKey = PRIMARY_CHOICE[domain];
  const keys = Object.keys(attrs);

  function sendAttr(attr, value) {
    if (locked) return;
    setControl(c.id, attr + ":" + value);
  }

  const boolKeys = [];
  const statusKeys = [];
  keys.forEach(function (attr) {
    if (attr === primaryKey) return;
    if (BOOL_CHIPS[attr]) boolKeys.push(attr);
    else if (STATUS_ATTRS[attr]) statusKeys.push(attr);
    else statusKeys.push(attr);
  });

  const primaryVal =
    primaryKey && attrs[primaryKey] != null ? attrs[primaryKey] : c.value;
  const hasChoice = primaryKey && c.options && c.options.length;

  return html`
    <div
      class="ctrl-card composite-card ${locked ? "locked" : ""} ${pinned ? "pinned" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || c.id)}</div>
        <div class="ctrl-meta">
          <h3>${entityLabel(c)}</h3>
          ${entityHint(c) ? html`<p class="hint">${entityHint(c)}</p>` : nothing}
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
                  ?disabled=${!pinned && (c.value == null || c.value === "")}
                  @click=${function (ev) {
                    ev.stopPropagation();
                    if (pinned) {
                      setPersist(c.id, { enabled: false });
                      return;
                    }
                    if (c.value == null || c.value === "") return;
                    setPersist(c.id, { enabled: true, value: String(c.value) });
                  }}
                >
                  ${icon("pin")}
                </button>`}
        </div>
      </div>
      <div class="ctrl-body composite-body">
        ${c.stale
          ? html`<div class="lock-note">${t("status.cached", "Último conhecido")}</div>`
          : locked && c.status && c.status !== "ok"
            ? html`<div class="lock-note">${statusNote(c)}</div>`
            : nothing}

        ${hasChoice
          ? html`
              <div class="composite-primary">
                ${segmentToggle({
                  options: c.options || [],
                  current: primaryVal != null ? String(primaryVal) : null,
                  locked: locked,
                  choiceKey: "cmp:" + c.id + ":" + primaryKey,
                  onSelect: function (v) {
                    sendAttr(primaryKey, v);
                  },
                })}
              </div>
            `
          : nothing}

        ${boolKeys.length
          ? html`
              <div class="composite-chips">
                ${boolKeys.map(function (attr) {
                  const on = isOn(attrs[attr]);
                  return html`
                    <button
                      type="button"
                      class="composite-chip ${on ? "active" : ""}"
                      ?disabled=${locked}
                      @click=${function () {
                        sendAttr(attr, on ? "0" : "1");
                      }}
                    >
                      ${attrLabel(attr)}
                    </button>
                  `;
                })}
              </div>
            `
          : nothing}

        ${statusKeys.length
          ? html`
              <div class="composite-status">
                ${statusKeys.map(function (attr) {
                  return html`
                    <span class="composite-stat">
                      <span class="k">${attrLabel(attr)}</span>
                      <span class="v">${attrs[attr]}</span>
                    </span>
                  `;
                })}
              </div>
            `
          : nothing}
      </div>
    </div>
  `;
}
