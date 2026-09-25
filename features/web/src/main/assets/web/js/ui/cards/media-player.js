import { loadCss } from "../load-css.js";
loadCss("/static/js/ui/cards/media-player.css");

import { html, nothing } from "../../lit.js";
import { fmt } from "../../api.js";
import { t } from "../../i18n.js";
import { setControl, mediaStateLabel } from "../../actions.js";
import { icon, hideBtn, cardSpan } from "./shared.js";

export function mediaAttr(c, camel, snake) {
  if (c[camel] != null && c[camel] !== "") return c[camel];
  const attrs = c.attributes || {};
  if (attrs[camel] != null && attrs[camel] !== "") return attrs[camel];
  if (snake) {
    if (c[snake] != null && c[snake] !== "") return c[snake];
    if (attrs[snake] != null && attrs[snake] !== "") return attrs[snake];
  }
  return null;
}

export function mediaPlayerCard(c, restore) {
  const playing = c.value === "playing";
  const title =
    mediaAttr(c, "mediaTitle", "media_title") ||
    t("media_player.nothing", "Nothing playing");
  const artist = mediaAttr(c, "mediaArtist", "media_artist") || "";
  const album = mediaAttr(c, "mediaAlbum", "media_album") || "";
  const stateLabel =
    mediaStateLabel(c.value) || c.valueLabel || fmt(c.value);
  const locked = c.status !== "ok" && c.status !== "cached";
  const sub = [artist, album].filter(Boolean).join(" · ");
  const volMaxRaw = mediaAttr(c, "volumeMax", "volume_max");
  const volMinRaw = mediaAttr(c, "volumeMin", "volume_min");
  const volMax = Number(volMaxRaw != null ? volMaxRaw : c.max != null ? c.max : 39);
  const volMin = Number(volMinRaw != null ? volMinRaw : c.min != null ? c.min : 0);
  const volRaw = mediaAttr(c, "volume");
  const vol =
    volRaw != null && volRaw !== "" && !isNaN(Number(volRaw))
      ? Number(volRaw)
      : volMin;
  const span = cardSpan(c);

  function send(cmd) {
    if (locked) return;
    setControl(c.id, cmd);
  }

  function onVolumeInput(ev) {
    if (locked) return;
    const n = parseInt(ev.target.value, 10);
    if (isNaN(n)) return;
    send("volume:" + n);
  }

  return html`
    <div
      class="ctrl-card media-player-card ${playing ? "is-playing" : ""} ${locked ? "locked" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || "sound")}</div>
        <div class="ctrl-meta">
          <h3>${c.label}</h3>
          <p class="hint media-state">${stateLabel}</p>
        </div>
        <div class="card-actions">${hideBtn(c.id, restore)}</div>
      </div>
      <div class="ctrl-body media-player-body">
        <div class="media-now">
          <div class="media-title">${title}</div>
          ${sub
            ? html`<div class="media-artist">${sub}</div>`
            : nothing}
        </div>
        <div class="media-transport" role="group" aria-label=${t("media_player.transport", "Transport")}>
          <button
            type="button"
            class="media-btn"
            title=${t("media_player.previous", "Previous")}
            aria-label=${t("media_player.previous", "Previous")}
            ?disabled=${locked}
            @click=${function () {
              send("previous");
            }}
          >
            ‹‹
          </button>
          <button
            type="button"
            class="media-btn media-btn-main"
            title=${playing
              ? t("media_player.pause", "Pause")
              : t("media_player.play", "Play")}
            aria-label=${playing
              ? t("media_player.pause", "Pause")
              : t("media_player.play", "Play")}
            ?disabled=${locked}
            @click=${function () {
              send(playing ? "pause" : "play");
            }}
          >
            ${playing ? "❚❚" : "▶"}
          </button>
          <button
            type="button"
            class="media-btn"
            title=${t("media_player.next", "Next")}
            aria-label=${t("media_player.next", "Next")}
            ?disabled=${locked}
            @click=${function () {
              send("next");
            }}
          >
            ››
          </button>
        </div>
        <div
          class="media-volume-slider"
          role="group"
          aria-label=${t("media_player.volume", "Volume")}
        >
          <span class="media-volume-label">${vol}<span class="unit">/${isNaN(volMax) ? 39 : volMax}</span></span>
          <input
            type="range"
            class="media-range"
            min=${isNaN(volMin) ? 0 : volMin}
            max=${isNaN(volMax) ? 39 : volMax}
            step="1"
            .value=${String(vol)}
            ?disabled=${locked}
            @change=${onVolumeInput}
            @input=${function (ev) {
              // Live label while dragging; commit on change.
              const label = ev.target.parentElement &&
                ev.target.parentElement.querySelector(".media-volume-label");
              if (label) {
                const max = isNaN(volMax) ? 39 : volMax;
                label.innerHTML =
                  ev.target.value + '<span class="unit">/' + max + "</span>";
              }
            }}
          />
        </div>
      </div>
    </div>
  `;
}
