import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/cover.css");

import { html, nothing } from "../../lit.js";
import { t, entityLabel, entityHint } from "../../i18n.js";
import { setControl, setPersist } from "../../actions.js";
import { icon, pinSnapshot, hideBtn, cardSpan, statusNote } from "./shared.js";
import { segmentToggle } from "./choice.js";

function openCloseOpts() {
  return [
    { value: "closed", labelKey: "common.closed" },
    { value: "open", labelKey: "common.open" },
  ];
}

function isOpen(c) {
  const s = c.state != null ? c.state : c.value;
  return s === "open";
}

function currentPosition(c) {
  const attrs = c.attributes || {};
  const raw = attrs.current_position;
  if (raw == null || raw === "") return null;
  const n = typeof raw === "number" ? raw : parseFloat(raw);
  return isNaN(n) ? null : Math.round(n);
}

/** Position covers expose `current_position`; trunk (status-only) does not. */
function supportsPosition(c) {
  return currentPosition(c) != null || (c.min != null && c.max != null && !c.composite);
}

export function coverCard(c, restore) {
  const locked = c.status !== "ok" && c.status !== "cached";
  const open = isOpen(c);
  const pos = currentPosition(c);
  const showPos = supportsPosition(c);
  const displayPos = pos != null ? pos : open ? 100 : 0;
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");
  const span = cardSpan(c);

  function commitPosition(n) {
    if (locked) return;
    const v = Math.max(0, Math.min(100, Math.round(Number(n))));
    if (isNaN(v)) return;
    setControl(c.id, String(v));
  }

  return html`
    <div
      class="ctrl-card cover-card ${open ? "is-open" : ""} ${locked ? "locked" : ""} ${pinned ? "pinned" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || "window")}</div>
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
      <div class="ctrl-body cover-body">
        ${c.stale
          ? html`<div class="lock-note">${t("status.cached", "Último conhecido")}</div>`
          : locked && c.status && c.status !== "ok"
            ? html`<div class="lock-note">${statusNote(c)}</div>`
            : nothing}

        ${segmentToggle({
          options: openCloseOpts(),
          current: open ? "open" : "closed",
          locked: locked,
          choiceKey: "cover:" + c.id + ":state",
          onSelect: function (v) {
            if (locked) return;
            setControl(c.id, v);
          },
        })}

        ${showPos
          ? html`
              <div
                class="cover-position"
                role="group"
                aria-label=${t("attr.position", "Position")}
              >
                <span class="cover-position-label"
                  >${displayPos}<span class="unit">%</span></span
                >
                <input
                  type="range"
                  class="cover-range"
                  min="0"
                  max="100"
                  step="1"
                  .value=${String(displayPos)}
                  ?disabled=${locked}
                  @change=${function (ev) {
                    commitPosition(ev.target.value);
                  }}
                  @input=${function (ev) {
                    const label =
                      ev.target.parentElement &&
                      ev.target.parentElement.querySelector(
                        ".cover-position-label",
                      );
                    if (label) {
                      label.innerHTML =
                        ev.target.value + '<span class="unit">%</span>';
                    }
                  }}
                />
              </div>
            `
          : nothing}
      </div>
    </div>
  `;
}
