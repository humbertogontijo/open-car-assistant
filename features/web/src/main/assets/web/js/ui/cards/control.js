import { html, nothing } from "../../lit.js";
import { t } from "../../i18n.js";
import { setPersist } from "../../actions.js";
import {
  icon,
  pinSnapshot,
  statusNote,
  hideBtn,
  cardSpan,
} from "./shared.js";
import { inputWidget } from "./input.js";
import { sensorCard } from "./sensor.js";
import { mediaPlayerCard } from "./media-player.js";
import { climateCard } from "./climate.js";

export function controlCard(c, restore) {
  if (c.input === "media_player" || c.entity === "media_player" || c.domain === "media_player") {
    return mediaPlayerCard(c, restore);
  }
  if (c.input === "climate" || c.entity === "climate" || c.domain === "climate") {
    // Atomic climate-domain bools (parking_comfort, nap_mode, …) keep the simple card.
    if (c.id === "climate" || c.input === "climate") {
      return climateCard(c, restore);
    }
  }
  if (c.input === "sensor") {
    if (!restore && c.status !== "ok") return nothing;
    return sensorCard(c, restore);
  }

  const locked = c.status !== "ok" && c.status !== "cached";
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const hasValue = c.value != null && c.value !== "";
  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");
  const span = cardSpan(c);

  return html`
    <div
      class="ctrl-card ${locked ? "locked" : ""} ${pinned ? "pinned" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || c.id)}</div>
        <div class="ctrl-meta">
          <h3>${c.label}</h3>
          ${c.hint ? html`<p class="hint">${c.hint}</p>` : nothing}
          ${c.acronym
            ? html`<span class="badge acronym">${c.acronym}</span>`
            : nothing}
        </div>
        <div class="card-actions">
          ${hideBtn(c.id, restore)}
          ${restore || c.writeOnly || c.input === "command"
            ? nothing
            : html`
          <button
            type="button"
            class="pin-btn ${pinned ? "active" : ""}"
            title=${pinTitle}
            aria-label=${pinTitle}
            aria-pressed=${pinned ? "true" : "false"}
            ?disabled=${!pinned && !hasValue}
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
      <div class="ctrl-body">
        ${c.stale
          ? html`<div class="lock-note">${t("status.cached", "Último conhecido")}</div>`
          : locked && c.status && c.status !== "ok"
            ? html`<div class="lock-note">${statusNote(c)}</div>`
            : nothing}
        ${inputWidget(c)}
      </div>
    </div>
  `;
}
