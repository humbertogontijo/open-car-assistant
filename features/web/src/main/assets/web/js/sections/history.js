import { html, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";
import { api, fmt } from "../api.js";
import { prefSegment } from "../ui/cards.js";
import { formatDisplayNumber, unitLabelFor } from "../units.js";

function historyEntityMeta(id) {
  const e = (state.entities || []).find(function (x) {
    return x.id === id;
  });
  const dc = (e && e.deviceClass) || inferHistoryDeviceClass(id);
  const input = (e && e.input) || (dc === "enum" ? "choice" : "sensor");
  return {
    id: id,
    deviceClass: dc,
    input: input,
    options: (e && e.options) || [],
    unitLabel: (e && e.unitLabel) || "",
    unitOfMeasurement: (e && e.unitOfMeasurement) || null,
  };
}

function inferHistoryDeviceClass(id) {
  const s = String(id || "");
  if (/soc|battery/i.test(s)) return "battery";
  if (/fuel/i.test(s)) return "fuel";
  if (/temp|hvac/i.test(s)) return "temperature";
  if (/speed/i.test(s)) return "speed";
  if (/range|odometer|distance/i.test(s)) return "distance";
  if (/charge_a|current/i.test(s)) return "current";
  if (/voltage/i.test(s)) return "voltage";
  if (/power|energy/i.test(s)) return "energy";
  if (/gear|drive_mode|plug|regen|mode/i.test(s)) return "enum";
  return null;
}

function historyViewsFor(meta) {
  const numeric = {
    battery: 1,
    fuel: 1,
    temperature: 1,
    speed: 1,
    distance: 1,
    current: 1,
    voltage: 1,
    power: 1,
    energy: 1,
    duration: 1,
    pressure: 1,
    humidity: 1,
  };
  const dc = meta && meta.deviceClass;
  const views = [];
  if (dc && numeric[dc]) views.push("graph");
  if (!dc || dc === "enum" || (meta && (meta.input === "bool" || meta.input === "choice"))) {
    views.push("timeline");
  } else if (views.indexOf("graph") >= 0) {
    views.push("timeline");
  }
  views.push("list");
  return views;
}

function historyEntityLabel(id) {
  const e = (state.entities || []).find(function (x) {
    return x.id === id;
  });
  if (e && (e.label || e.i18n)) return e.label || t(e.i18n, id);
  if (String(id).indexOf("sensor_") === 0) {
    return t("sensor." + String(id).slice("sensor_".length), id);
  }
  return t("control." + id, id);
}

function historyFormatValue(value, meta) {
  if (value == null || value === "") return "—";
  if (meta && meta.options && meta.options.length) {
    const hit = meta.options.find(function (o) {
      return String(o.value) === String(value);
    });
    if (hit) return hit.label || String(value);
  }
  if (meta && meta.input === "bool") {
    const on = value === "1" || value === "true" || value === "on";
    return on ? t("value.on", "On") : t("value.off", "Off");
  }
  const n = parseFloat(value);
  if (!isNaN(n) && meta && meta.unitOfMeasurement) {
    const shown = formatDisplayNumber(meta.unitOfMeasurement, n, meta.input || "sensor");
    const unit = unitLabelFor({
      unitOfMeasurement: meta.unitOfMeasurement,
      unitLabel: meta.unitLabel,
    });
    return unit ? shown + " " + unit : shown;
  }
  if (meta && meta.unitLabel) return fmt(value) + " " + meta.unitLabel;
  return fmt(value);
}

function fmtTs(ts) {
  const n = Number(ts);
  if (!n) return "—";
  try {
    return new Date(n).toLocaleString();
  } catch (e) {
    return String(ts);
  }
}

function historyGraph(points) {
  if (!points || !points.length) {
    return html`<p class="persist-note">${t("history.no_points", "No samples in this range")}</p>`;
  }
  const nums = points
    .map(function (p) {
      const n = parseFloat(p.value);
      return isNaN(n) ? null : { n: n, ts: p.ts, value: p.value };
    })
    .filter(Boolean);
  if (nums.length < 2) {
    return html`
      <p class="persist-note">
        ${t("history.graph.need_numeric", "Need at least two numeric samples for a graph")}
      </p>
      ${historyTable(points)}
    `;
  }
  let min = nums[0].n;
  let max = nums[0].n;
  nums.forEach(function (x) {
    if (x.n < min) min = x.n;
    if (x.n > max) max = x.n;
  });
  const span = max - min || 1;
  const w = 640;
  const h = 180;
  const pad = 8;
  const step = (w - pad * 2) / (nums.length - 1);
  const coords = nums
    .map(function (x, i) {
      const px = pad + i * step;
      const py = h - pad - ((x.n - min) / span) * (h - pad * 2);
      return px.toFixed(1) + "," + py.toFixed(1);
    })
    .join(" ");
  return html`
    <svg
      viewBox="0 0 ${w} ${h}"
      width="100%"
      height="180"
      style="display:block;margin:8px 0 14px;background:var(--surface-2);border-radius:8px"
    >
      <polyline
        fill="none"
        stroke="var(--accent, #3af)"
        stroke-width="2.5"
        points=${coords}
      ></polyline>
    </svg>
    <p class="sub" style="margin:0 0 10px">
      ${String(min)} … ${String(max)} · ${points.length}
      ${t("history.samples", "samples")}
    </p>
  `;
}

function historyTimeline(points, meta) {
  if (!points || !points.length) {
    return html`<p class="persist-note">${t("history.no_points", "No samples in this range")}</p>`;
  }
  const rows = points.slice().reverse().slice(0, 300);
  return html`<div style="max-height:420px;overflow:auto">
    ${rows.map(function (p) {
      return html`<div
        style="display:flex;gap:14px;padding:10px 0;border-bottom:1px solid var(--border);align-items:flex-start"
      >
        <span class="mono sub" style="flex-shrink:0;min-width:9.5rem">${fmtTs(p.ts)}</span>
        <span style="font-weight:600">${historyFormatValue(p.value, meta)}</span>
      </div>`;
    })}
  </div>`;
}

function historyTable(points, meta) {
  if (!points || !points.length) {
    return html`<p class="persist-note">${t("history.no_points", "No samples in this range")}</p>`;
  }
  const rows = points.slice().reverse().slice(0, 400);
  return html`<div style="max-height:360px;overflow:auto">
    <table class="table">
      <thead>
        <tr>
          <th>${t("history.col.time", "Time")}</th>
          <th>${t("history.col.value", "Value")}</th>
        </tr>
      </thead>
      <tbody>
        ${rows.map(function (p) {
          return html`<tr>
            <td class="mono">${fmtTs(p.ts)}</td>
            <td class="mono">${historyFormatValue(p.value, meta)}</td>
          </tr>`;
        })}
      </tbody>
    </table>
  </div>`;
}

export async function loadHistoryPoints() {
  const id = state.historySelected || (state.historyEntities && state.historyEntities[0]);
  if (!id) {
    patch({ historyPoints: [] });
    return;
  }
  state.historySelected = id;
  const hours = state.historyRangeHours || 24;
  const end = Date.now();
  const start = end - hours * 60 * 60 * 1000;
  try {
    const res = await api(
      "/api/history/" +
        encodeURIComponent(id) +
        "?start=" +
        start +
        "&end=" +
        end +
        "&limit=2000",
    );
    patch({ historyPoints: (res && res.points) || [], historySelected: id });
  } catch (e) {
    patch({ historyPoints: [], historySelected: id });
  }
}

export function sectionHistory() {
  const entities = state.historyEntities || [];
  const selected = state.historySelected || entities[0] || "";
  const hours = state.historyRangeHours || 24;
  const points = state.historyPoints || [];
  const meta = historyEntityMeta(selected);
  const views = historyViewsFor(meta);
  let view =
    state.historyView && views.indexOf(state.historyView) >= 0
      ? state.historyView
      : views[0];
  if (state.historyView !== view) state.historyView = view;

  const rangeOpts = [
    { value: "6", label: t("history.range.6h", "6 h") },
    { value: "24", label: t("history.range.24h", "24 h") },
    { value: "72", label: t("history.range.72h", "3 d") },
    { value: "168", label: t("history.range.7d", "7 d") },
  ];
  const viewOpts = views.map(function (v) {
    return {
      value: v,
      label:
        v === "graph"
          ? t("history.view.graph", "Graph")
          : v === "timeline"
            ? t("history.view.timeline", "Timeline")
            : t("history.view.list", "List"),
    };
  });

  const body = !entities.length
    ? html`<p class="persist-note">${t("history.empty", "No history yet")}</p>`
    : html`
        <div
          class="row"
          style="gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:14px"
        >
          <label style="flex:1;min-width:160px">
            ${t("history.entity", "Entity")}
            <select
              class="field"
              style="width:100%;margin-top:6px"
              .value=${selected}
              @change=${async function (ev) {
                patch({ historySelected: ev.target.value });
                await loadHistoryPoints();
              }}
            >
              ${entities.map(function (id) {
                return html`<option value=${id} ?selected=${id === selected}>
                  ${historyEntityLabel(id)}
                </option>`;
              })}
            </select>
          </label>
          <div>
            ${t("history.range", "Range")}
            <div style="margin-top:6px">
              ${prefSegment("hist-range", rangeOpts, String(hours))}
            </div>
          </div>
          <div>
            ${t("history.view", "View")}
            <div style="margin-top:6px">${prefSegment("hist-view", viewOpts, view)}</div>
          </div>
          <button
            class="btn primary"
            type="button"
            @click=${async function () {
              await loadHistoryPoints();
            }}
          >
            ${t("history.load", "Load")}
          </button>
        </div>
        ${view === "graph"
          ? historyGraph(points)
          : view === "timeline"
            ? historyTimeline(points, meta)
            : historyTable(points, meta)}
      `;

  return html`
    <h1>${t("section.history.title", "History")}</h1>
    <p class="sub">${t("section.history.sub", "Samples only when a value changes")}</p>
    <div class="card">${body}</div>
  `;
}
