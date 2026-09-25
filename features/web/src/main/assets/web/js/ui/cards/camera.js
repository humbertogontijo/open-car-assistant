import { html, nothing } from "../../lit.js";
import { t, entityLabel, entityHint, entityValueLabel } from "../../i18n.js";
import { icon, hideBtn, cardSpan } from "./shared.js";

/**
 * Read-only camera entity card (role + idle/streaming).
 * Live mosaic remains on the /cameras DVR page.
 */
export function cameraCard(c, restore) {
  const span = cardSpan(c);
  const role =
    (c.attributes && c.attributes.role) ||
    (c.id && String(c.id).indexOf("camera.") === 0
      ? String(c.id).slice("camera.".length)
      : "");
  const streaming = c.value === "streaming";
  const stateLabel = entityValueLabel(c) ||
    (streaming
      ? t("camera.state.streaming", "Streaming")
      : t("camera.state.idle", "Idle"));
  const hint = entityHint(c) || role;

  return html`
    <div
      class="ctrl-card camera-card ${streaming ? "streaming" : ""}"
      data-card=${c.id}
      data-col-span=${span.cols}
      data-row-span=${span.rows}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || "camera")}</div>
        <div class="ctrl-meta">
          <h3>${entityLabel(c)}</h3>
          ${hint ? html`<p class="hint">${hint}</p>` : nothing}
        </div>
        <div class="card-actions">${hideBtn(c.id, restore)}</div>
      </div>
      <div class="ctrl-body">
        <div class="entity-value">${stateLabel}</div>
      </div>
    </div>
  `;
}
