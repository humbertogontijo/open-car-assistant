import { html, svg, nothing } from "../lit.js";
import { state, patch } from "../store.js";
import { t, entityLabel, entityValueLabel, optionLabel } from "../i18n.js";
import { api, fmt } from "../api.js";
import { prefSegment, choiceSelect } from "../ui/cards.js";
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
    valueMapId: e && e.valueMapId,
    binary: e && e.binary,
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
  if (e) return entityLabel(e);
  if (String(id).indexOf("sensor_") === 0) {
    return t("sensor." + String(id).slice("sensor_".length), id);
  }
  return t("control." + id, id);
}

function historyFormatValue(value, meta) {
  if (value == null || value === "") return "—";
  if (meta) {
    const mapped = entityValueLabel(
      Object.assign({}, meta, { value: value }),
      value,
    );
    if (meta.valueMapId || meta.binary || (meta.options && meta.options.length) || meta.input === "bool") {
      if (mapped && mapped !== String(value)) return mapped;
    }
  }
  if (meta && meta.options && meta.options.length) {
    const hit = meta.options.find(function (o) {
      return String(o.value) === String(value);
    });
    if (hit) return optionLabel(hit);
  }
  if (meta && meta.input === "bool") {
    const on = value === "1" || value === "true" || value === "on";
    return on ? t("common.on", "On") : t("common.off", "Off");
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

function fmtAxisTs(ts, spanMs) {
  const n = Number(ts);
  if (!n) return "";
  try {
    const d = new Date(n);
    const opts =
      spanMs <= 36 * 3600 * 1000
        ? { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }
        : spanMs <= 8 * 24 * 3600 * 1000
          ? { month: "short", day: "numeric", hour: "2-digit" }
          : { month: "short", day: "numeric" };
    return d.toLocaleString(undefined, opts);
  } catch (e) {
    return String(ts);
  }
}

function fmtYTick(n) {
  if (!isFinite(n)) return "";
  const a = Math.abs(n);
  if (a >= 1000) return n.toFixed(0);
  if (a >= 100) return String(Math.round(n * 10) / 10);
  if (a >= 10) return String(Math.round(n * 100) / 100);
  return String(Math.round(n * 1000) / 1000);
}

function timeRange(points) {
  if (!points || !points.length) return { t0: 0, t1: 1, span: 1 };
  let t0 = Number(points[0].ts) || 0;
  let t1 = t0;
  for (let i = 1; i < points.length; i++) {
    const ts = Number(points[i].ts) || 0;
    if (ts < t0) t0 = ts;
    if (ts > t1) t1 = ts;
  }
  const span = Math.max(1, t1 - t0);
  return { t0: t0, t1: t1, span: span };
}

function axisTicks(t0, t1, count) {
  const span = Math.max(1, t1 - t0);
  const n = Math.max(2, count || 4);
  const out = [];
  for (let i = 0; i < n; i++) {
    out.push(t0 + (span * i) / (n - 1));
  }
  return out;
}

function showHistTip(wrap, clientX, clientY, lines) {
  const tip = wrap && wrap.querySelector(".hist-tip");
  if (!tip) return;
  tip.hidden = false;
  tip.innerHTML = lines
    .map(function (line, i) {
      return (
        '<div class="' +
        (i === 0 ? "hist-tip-val" : "hist-tip-time") +
        '">' +
        line +
        "</div>"
      );
    })
    .join("");
  const rect = wrap.getBoundingClientRect();
  let left = clientX - rect.left + 12;
  let top = clientY - rect.top - 8;
  tip.style.left = "0px";
  tip.style.top = "0px";
  const tw = tip.offsetWidth || 120;
  const th = tip.offsetHeight || 40;
  if (left + tw > rect.width - 4) left = clientX - rect.left - tw - 12;
  if (top + th > rect.height - 4) top = clientY - rect.top - th - 4;
  if (left < 4) left = 4;
  if (top < 4) top = 4;
  tip.style.left = left + "px";
  tip.style.top = top + "px";
}

function hideHistTip(wrap) {
  const tip = wrap && wrap.querySelector(".hist-tip");
  if (tip) tip.hidden = true;
  const cross = wrap && wrap.querySelector(".hist-crosshair");
  if (cross) cross.setAttribute("visibility", "hidden");
  const dot = wrap && wrap.querySelector(".hist-hover-dot");
  if (dot) dot.setAttribute("visibility", "hidden");
}

function historyGraph(points, meta) {
  if (!points || !points.length) {
    return html`<p class="persist-note">${t("history.no_points", "No samples in this range")}</p>`;
  }
  const nums = points
    .map(function (p) {
      const n = parseFloat(p.value);
      return isNaN(n) ? null : { n: n, ts: Number(p.ts) || 0, value: p.value };
    })
    .filter(Boolean);
  if (nums.length < 2) {
    return html`
      <p class="persist-note">
        ${t("history.graph.need_numeric", "Need at least two numeric samples for a graph")}
      </p>
      ${historyTable(points, meta)}
    `;
  }
  let min = nums[0].n;
  let max = nums[0].n;
  nums.forEach(function (x) {
    if (x.n < min) min = x.n;
    if (x.n > max) max = x.n;
  });
  if (min === max) {
    min -= 1;
    max += 1;
  }
  const span = max - min;
  const range = timeRange(nums);
  const w = 640;
  const h = 220;
  const padL = 44;
  const padR = 14;
  const padT = 14;
  const padB = 34;
  const plotW = w - padL - padR;
  const plotH = h - padT - padB;

  function xAt(ts) {
    return padL + ((ts - range.t0) / range.span) * plotW;
  }
  function yAt(n) {
    return padT + (1 - (n - min) / span) * plotH;
  }

  const coords = nums
    .map(function (x) {
      return xAt(x.ts).toFixed(1) + "," + yAt(x.n).toFixed(1);
    })
    .join(" ");

  const yTicks = [max, (min + max) / 2, min];
  const xTicks = axisTicks(range.t0, range.t1, 4);

  function onMove(ev) {
    const wrap = ev.currentTarget;
    const svg = wrap.querySelector("svg");
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const mx = ((ev.clientX - rect.left) / rect.width) * w;
    const plotX = Math.max(padL, Math.min(padL + plotW, mx));
    const ts = range.t0 + ((plotX - padL) / plotW) * range.span;
    let best = nums[0];
    let bestD = Math.abs(best.ts - ts);
    for (let i = 1; i < nums.length; i++) {
      const d = Math.abs(nums[i].ts - ts);
      if (d < bestD) {
        best = nums[i];
        bestD = d;
      }
    }
    const px = xAt(best.ts);
    const py = yAt(best.n);
    const cross = wrap.querySelector(".hist-crosshair");
    if (cross) {
      cross.setAttribute("x1", String(px));
      cross.setAttribute("x2", String(px));
      cross.setAttribute("visibility", "visible");
    }
    const dot = wrap.querySelector(".hist-hover-dot");
    if (dot) {
      dot.setAttribute("cx", String(px));
      dot.setAttribute("cy", String(py));
      dot.setAttribute("visibility", "visible");
    }
    showHistTip(wrap, ev.clientX, ev.clientY, [
      historyFormatValue(best.value, meta),
      fmtTs(best.ts),
    ]);
  }

  return html`
    <div
      class="hist-chart"
      @mousemove=${onMove}
      @mouseleave=${function (ev) {
        hideHistTip(ev.currentTarget);
      }}
    >
      <svg class="hist-svg" viewBox="0 0 ${w} ${h}" width="100%" height="220" aria-hidden="true">
        ${svg`
          <line
            class="hist-axis"
            x1=${padL}
            y1=${padT}
            x2=${padL}
            y2=${padT + plotH}
          ></line>
          <line
            class="hist-axis"
            x1=${padL}
            y1=${padT + plotH}
            x2=${padL + plotW}
            y2=${padT + plotH}
          ></line>
          ${yTicks.map(function (v) {
            const y = yAt(v);
            return svg`
              <line
                class="hist-grid"
                x1=${padL}
                y1=${y}
                x2=${padL + plotW}
                y2=${y}
              ></line>
              <text
                class="hist-tick hist-tick-y"
                x=${padL - 6}
                y=${y + 3.5}
                text-anchor="end"
              >${fmtYTick(v)}</text>
            `;
          })}
          ${xTicks.map(function (ts, i) {
            const x = xAt(ts);
            const anchor = i === 0 ? "start" : i === xTicks.length - 1 ? "end" : "middle";
            return svg`
              <line
                class="hist-tick-mark"
                x1=${x}
                y1=${padT + plotH}
                x2=${x}
                y2=${padT + plotH + 5}
              ></line>
              <text
                class="hist-tick hist-tick-x"
                x=${x}
                y=${padT + plotH + 20}
                text-anchor=${anchor}
              >${fmtAxisTs(ts, range.span)}</text>
            `;
          })}
          <polyline
            class="hist-line"
            fill="none"
            stroke-width="2.5"
            points=${coords}
          ></polyline>
          <line
            class="hist-crosshair"
            x1="0"
            y1=${padT}
            x2="0"
            y2=${padT + plotH}
            visibility="hidden"
          ></line>
          <circle class="hist-hover-dot" r="4.5" visibility="hidden"></circle>
        `}
      </svg>
      <div class="hist-tip" hidden></div>
    </div>
  `;
}

function timelineTone(value, meta, index) {
  if (meta && meta.input === "bool") {
    const on = value === "1" || value === "true" || value === "on";
    return on ? "on" : "off";
  }
  return "tone-" + (index % 6);
}

function historyTimeline(points, meta) {
  if (!points || !points.length) {
    return html`<p class="persist-note">${t("history.no_points", "No samples in this range")}</p>`;
  }
  const sorted = points
    .slice()
    .sort(function (a, b) {
      return (Number(a.ts) || 0) - (Number(b.ts) || 0);
    })
    .slice(-300);
  const range = timeRange(sorted);
  const endTs = Math.max(range.t1, Date.now());
  const span = Math.max(1, endTs - range.t0);
  const xTicks = axisTicks(range.t0, endTs, 4);

  const segments = sorted.map(function (p, i) {
    const ts = Number(p.ts) || 0;
    const nextTs = i + 1 < sorted.length ? Number(sorted[i + 1].ts) || endTs : endTs;
    const left = ((ts - range.t0) / span) * 100;
    const width = Math.max(0.35, ((nextTs - ts) / span) * 100);
    return {
      left: left,
      width: width,
      value: p.value,
      ts: ts,
      tone: timelineTone(p.value, meta, i),
      label: historyFormatValue(p.value, meta),
    };
  });

  function onMove(ev) {
    const wrap = ev.currentTarget;
    const track = wrap.querySelector(".hist-timeline-track");
    if (!track) return;
    const rect = track.getBoundingClientRect();
    if (rect.width <= 0) return;
    const pct = Math.max(0, Math.min(1, (ev.clientX - rect.left) / rect.width));
    const ts = range.t0 + pct * span;
    let best = segments[0];
    for (let i = 0; i < segments.length; i++) {
      const s = segments[i];
      const sEnd = s.ts + (s.width / 100) * span;
      if (ts >= s.ts && ts <= sEnd) {
        best = s;
        break;
      }
      if (Math.abs(s.ts - ts) < Math.abs(best.ts - ts)) best = s;
    }
    showHistTip(wrap, ev.clientX, ev.clientY, [best.label, fmtTs(best.ts)]);
  }

  return html`
    <div
      class="hist-timeline"
      @mousemove=${onMove}
      @mouseleave=${function (ev) {
        hideHistTip(ev.currentTarget);
      }}
    >
      <div class="hist-timeline-track">
        ${segments.map(function (s) {
          return html`<div
            class="hist-timeline-seg hist-tone-${s.tone}"
            style="left:${s.left.toFixed(2)}%;width:${s.width.toFixed(2)}%"
            title=${s.label + " · " + fmtTs(s.ts)}
          ></div>`;
        })}
        ${sorted.map(function (p) {
          const left = (((Number(p.ts) || 0) - range.t0) / span) * 100;
          return html`<div
            class="hist-timeline-mark"
            style="left:${left.toFixed(2)}%"
          ></div>`;
        })}
      </div>
      <div class="hist-timeline-axis">
        ${xTicks.map(function (ts) {
          const left = ((ts - range.t0) / span) * 100;
          return html`<span class="hist-timeline-tick" style="left:${left.toFixed(2)}%">
            <span class="hist-timeline-tick-mark"></span>
            <span class="hist-timeline-tick-label">${fmtAxisTs(ts, span)}</span>
          </span>`;
        })}
      </div>
      <div class="hist-tip" hidden></div>
    </div>
    <p class="sub hist-chart-meta">
      ${sorted.length} ${t("history.samples", "samples")}
    </p>
  `;
}

function historyTable(points, meta) {
  if (!points || !points.length) {
    return html`<p class="persist-note">${t("history.no_points", "No samples in this range")}</p>`;
  }
  const rows = points.slice().reverse().slice(0, 400);
  return html`<div class="table-scroll" style="max-height:360px;overflow:auto">
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
  ensureHistoryDefaults();
  const id = state.historySelected || (state.historyEntities && state.historyEntities[0]);
  if (!id) {
    patch({ historyPoints: [] });
    return;
  }
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

/** Default entity/view via patch — never mutate store during render. */
function ensureHistoryDefaults() {
  const entities = state.historyEntities || [];
  const updates = {};
  if (!state.historySelected && entities[0]) {
    updates.historySelected = entities[0];
  }
  const selected = updates.historySelected || state.historySelected || "";
  const meta = historyEntityMeta(selected);
  const views = historyViewsFor(meta);
  const view =
    state.historyView && views.indexOf(state.historyView) >= 0
      ? state.historyView
      : views[0];
  if (view && state.historyView !== view) {
    updates.historyView = view;
  }
  if (Object.keys(updates).length) patch(updates);
}

export function pageHistory() {
  ensureHistoryDefaults();
  const entities = state.historyEntities || [];
  const selected = state.historySelected || "";
  const hours = state.historyRangeHours || 24;
  const points = state.historyPoints || [];
  const meta = historyEntityMeta(selected);
  const views = historyViewsFor(meta);
  const view =
    state.historyView && views.indexOf(state.historyView) >= 0
      ? state.historyView
      : views[0];

  const rangeOpts = [
    { value: "6", label: t("history.range.6h", "6h") },
    { value: "24", label: t("history.range.24h", "24h") },
    { value: "72", label: t("history.range.72h", "3d") },
    { value: "168", label: t("history.range.7d", "7d") },
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
        <div class="hist-toolbar">
          <div class="hist-field hist-entity">
            <span class="hist-field-label">${t("history.entity", "Entity")}</span>
            ${choiceSelect({
              options: entities.map(function (id) {
                return { value: id, label: historyEntityLabel(id) };
              }),
              current: selected,
              choiceKey: "hist-entity",
              onSelect: async function (next) {
                patch({ historySelected: next });
                await loadHistoryPoints();
              },
            })}
          </div>
          <div class="hist-field">
            <span class="hist-field-label">${t("history.range", "Range")}</span>
            <div class="hist-seg">${prefSegment("hist-range", rangeOpts, String(hours))}</div>
          </div>
          <div class="hist-field">
            <span class="hist-field-label">${t("history.view", "View")}</span>
            <div class="hist-seg">${prefSegment("hist-view", viewOpts, view)}</div>
          </div>
        </div>
        ${view === "graph"
          ? historyGraph(points, meta)
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
