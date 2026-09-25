import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/light.css");
loadCss("/static/js/ui/cards/media-player.css"); // .media-range

import { html, nothing } from "../../lit.js";
import { t, optionLabel, entityLabel } from "../../i18n.js";
import { setControl, setPersist } from "../../actions.js";
import { icon, pinSnapshot, hideBtn, cardSpan, statusNote } from "./shared.js";

function attr(c, key) {
  const attrs = c.attributes || {};
  if (attrs[key] != null && attrs[key] !== "") return attrs[key];
  return null;
}

function isOn(c) {
  const state = c.state != null ? c.state : c.value;
  if (state === "on" || state === "off") return state === "on";
  const bri = attr(c, "brightness");
  return bri != null && Number(bri) > 0;
}

export function lightCard(c, restore) {
  const locked = c.status !== "ok" && c.status !== "cached";
  const on = isOn(c);
  const brightnessRaw = attr(c, "brightness");
  const brightness =
    brightnessRaw != null && !isNaN(Number(brightnessRaw))
      ? Number(brightnessRaw)
      : 0;
  const color = attr(c, "color");
  const min = c.min != null ? Number(c.min) : 0;
  const max = c.max != null ? Number(c.max) : 100;
  const step = c.step != null ? Number(c.step) : 1;
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");
  const span = cardSpan(c);
  const modes = c.options || [];

  function send(cmd) {
    if (locked) return;
    setControl(c.id, cmd);
  }

  const modeChips = modes.map(function (o) {
    const val = o.value;
    const active = color != null && String(color) === String(val);
    return html`
      <button
        type="button"
        class="light-mode-chip ${active ? "active" : ""}"
        ?disabled=${locked}
        aria-pressed=${active ? "true" : "false"}
        @click=${function () {
          send("color:" + val);
        }}
      >
        ${optionLabel(o) || val}
      </button>
    `;
  });

  return html`
    <div
      class="ctrl-card light-card ${on ? "is-on" : ""} ${locked ? "locked" : ""} ${pinned ? "pinned" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || "light")}</div>
        <div class="ctrl-meta">
          <h3>${entityLabel(c)}</h3>
          <p class="hint light-state">
            ${on ? t("common.on", "On") : t("common.off", "Off")}
          </p>
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
      <div class="ctrl-body light-body">
        ${c.stale
          ? html`<div class="lock-note">${t("status.cached", "Último conhecido")}</div>`
          : locked && c.status && c.status !== "ok"
            ? html`<div class="lock-note">${statusNote(c)}</div>`
            : nothing}

        ${modes.length
          ? html`
              <div
                class="light-modes"
                role="group"
                aria-label=${t("attr.color", "Color")}
              >
                ${modeChips}
              </div>
            `
          : nothing}

        <div
          class="light-bri-slider"
          role="group"
          aria-label=${t("attr.brightness", "Brightness")}
        >
          <button
            type="button"
            class="light-power-chip ${on ? "active" : ""}"
            ?disabled=${locked}
            @click=${function () {
              send(
                on
                  ? "off"
                  : "brightness:" + String(Math.max(step, Math.round(max * 0.5))),
              );
            }}
          >
            ${on ? t("common.on", "On") : t("common.off", "Off")}
          </button>
          <span class="light-bri-label"
            >${Math.round(brightness)}<span class="unit">%</span></span
          >
          <input
            type="range"
            class="media-range"
            min=${min}
            max=${max}
            step=${step}
            .value=${String(brightness)}
            ?disabled=${locked}
            @change=${function (ev) {
              send("brightness:" + ev.target.value);
            }}
          />
        </div>
      </div>
    </div>
  `;
}
