import { api, $, fmt } from "./api.js";
import { state, entitiesByGroup, groupByEntity } from "./state.js";
import { theme, setTheme } from "./theme.js";
import {
  renderEntityGrid,
  entityLabel,
  segmentToggleHtml,
  boolToggleHtml,
  prefCard,
  decodeOpts,
  cycleNext,
} from "./cards.js";
import { faceValue } from "./persist.js";
import { aboutPrivilegeTipHtml, bindPrivilegeTip } from "./setup.js";
import { t, setLocale } from "./i18n.js";
import { mountNavIcons } from "./icons.js";
import { quickEntryCardHtml } from "./shortcuts.js";

function subHtml(text) {
  return text ? '<p class="sub">' + text + "</p>" : "";
}

export function sectionHome() {
  const home = state.entities.filter(function (e) {
    if (e.group === "home" && e.entity === "sensor") return e.status === "ok";
    return e.entity === "drive_mode" || e.entity === "regen";
  });
  const histN = (state.historyEntities || []).length;
  return (
    "<h1>" +
    t("section.home.title", "Início") +
    "</h1>" +
    renderEntityGrid(home) +
    '<div class="card" style="margin-top:18px">' +
    "<h2>" +
    t("section.history.title", "History") +
    "</h2>" +
    '<p class="sub" style="margin:0 0 12px">' +
    (histN
      ? t("history.home.summary", "{n} entities tracked").replace("{n}", String(histN))
      : t("history.empty", "No history yet")) +
    "</p>" +
    '<button class="btn primary" type="button" id="goHistory">' +
    t("history.open", "Open history") +
    "</button></div>"
  );
}

export function sectionAndroid() {
  return (
    "<h1>" +
    t("section.android.title", "Android") +
    "</h1>" +
    renderEntityGrid(entitiesByGroup("android")) +
    '<div class="grid" style="margin-top:18px">' +
    prefCard({
      icon: "system",
      title: t("system.android_settings", "Configurações do Android"),
      bodyHtml:
        '<button class="btn primary" id="openAndroidSettings" style="width:100%">' +
        t("system.android_settings", "Configurações do Android") +
        "</button>",
    }) +
    "</div>"
  );
}

/** Stable family order for subsection headers within a nav tab. */
var FAMILY_ORDER = [
  "drive_mode",
  "regen",
  "steering",
  "brake",
  "climate",
  "seat",
  "energy",
  "charging",
  "adas",
  "lock",
  "light",
  "window",
  "hud",
  "sensor",
  "android",
  "extra",
];

function renderFamilySections(items) {
  if (!items || !items.length) {
    return renderEntityGrid(items);
  }
  var buckets = groupByEntity(items);
  var keys = Object.keys(buckets);
  keys.sort(function (a, b) {
    var ia = FAMILY_ORDER.indexOf(a);
    var ib = FAMILY_ORDER.indexOf(b);
    if (ia < 0) ia = FAMILY_ORDER.length;
    if (ib < 0) ib = FAMILY_ORDER.length;
    if (ia !== ib) return ia - ib;
    return a < b ? -1 : a > b ? 1 : 0;
  });
  return keys
    .map(function (fam) {
      return (
        '<h2 class="section-label" style="margin:20px 0 10px">' +
        escAttr(entityLabel(fam)) +
        "</h2>" +
        renderEntityGrid(buckets[fam])
      );
    })
    .join("");
}

export function sectionGroup(title, sub, group) {
  return (
    "<h1>" +
    title +
    "</h1>" +
    subHtml(sub) +
    renderFamilySections(entitiesByGroup(group))
  );
}

export function sectionCabin() {
  return (
    "<h1>" +
    t("section.cabin.title", "Cabine") +
    "</h1>" +
    renderFamilySections(entitiesByGroup("cabin")) +
    soundsCardsHtml()
  );
}

export function sectionHistory() {
  const entities = state.historyEntities || [];
  const selected = state.historySelected || (entities[0] || "");
  const hours = state.historyRangeHours || 24;
  const points = state.historyPoints || [];
  const meta = historyEntityMeta(selected);
  const views = historyViewsFor(meta);
  const view =
    state.historyView && views.indexOf(state.historyView) >= 0
      ? state.historyView
      : views[0];
  state.historyView = view;
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
  const opts = entities
    .map(function (id) {
      return (
        '<option value="' +
        escAttr(id) +
        '"' +
        (id === selected ? " selected" : "") +
        ">" +
        escAttr(historyEntityLabel(id)) +
        "</option>"
      );
    })
    .join("");
  let body;
  if (!entities.length) {
    body = '<p class="persist-note">' + t("history.empty", "No history yet") + "</p>";
  } else {
    body =
      '<div class="row" style="gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:14px">' +
      "<label style=\"flex:1;min-width:160px\">" +
      t("history.entity", "Entity") +
      '<select class="field" id="histEntity" style="width:100%;margin-top:6px">' +
      opts +
      "</select></label>" +
      "<div>" +
      t("history.range", "Range") +
      '<div style="margin-top:6px">' +
      segmentToggleHtml(rangeOpts, String(hours), 'data-pref="hist-range"') +
      "</div></div>" +
      "<div>" +
      t("history.view", "View") +
      '<div style="margin-top:6px">' +
      segmentToggleHtml(viewOpts, view, 'data-pref="hist-view"') +
      "</div></div>" +
      '<button class="btn primary" type="button" id="histLoad">' +
      t("history.load", "Load") +
      "</button></div>" +
      (view === "graph"
        ? historyGraphHtml(points)
        : view === "timeline"
          ? historyTimelineHtml(points, meta)
          : historyTableHtml(points, meta));
  }
  return (
    "<h1>" +
    t("section.history.title", "History") +
    '</h1><p class="sub">' +
    t("section.history.sub", "Samples only when a value changes") +
    "</p>" +
    '<div class="card">' +
    body +
    "</div>"
  );
}

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

/** Views available for an entity: graph for numeric classes; timeline for discrete; list always. */
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
    const key = "sensor." + String(id).slice("sensor_".length);
    return t(key, id);
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
  if (meta && meta.unitLabel) return fmt(value) + " " + meta.unitLabel;
  return fmt(value);
}

function historyGraphHtml(points) {
  if (!points || !points.length) {
    return (
      '<p class="persist-note">' +
      t("history.no_points", "No samples in this range") +
      "</p>"
    );
  }
  const nums = points
    .map(function (p) {
      const n = parseFloat(p.value);
      return isNaN(n) ? null : { n: n, ts: p.ts, value: p.value };
    })
    .filter(function (x) {
      return x != null;
    });
  if (nums.length < 2) {
    return (
      '<p class="persist-note">' +
      t("history.graph.need_numeric", "Need at least two numeric samples for a graph") +
      "</p>" +
      historyTableHtml(points)
    );
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
  return (
    '<svg viewBox="0 0 ' +
    w +
    " " +
    h +
    '" width="100%" height="180" style="display:block;margin:8px 0 14px;background:var(--surface-2);border-radius:8px">' +
    '<polyline fill="none" stroke="var(--accent, #3af)" stroke-width="2.5" points="' +
    coords +
    '"/></svg>' +
    '<p class="sub" style="margin:0 0 10px">' +
    escAttr(String(min)) +
    " … " +
    escAttr(String(max)) +
    " · " +
    points.length +
    " " +
    t("history.samples", "samples") +
    "</p>"
  );
}

function historyTimelineHtml(points, meta) {
  if (!points || !points.length) {
    return (
      '<p class="persist-note">' +
      t("history.no_points", "No samples in this range") +
      "</p>"
    );
  }
  const rows = points
    .slice()
    .reverse()
    .slice(0, 300)
    .map(function (p) {
      return (
        '<div style="display:flex;gap:14px;padding:10px 0;border-bottom:1px solid var(--border);align-items:flex-start">' +
        '<span class="mono sub" style="flex-shrink:0;min-width:9.5rem">' +
        escAttr(fmtTs(p.ts)) +
        "</span>" +
        '<span style="font-weight:600">' +
        escAttr(historyFormatValue(p.value, meta)) +
        "</span></div>"
      );
    })
    .join("");
  return '<div style="max-height:420px;overflow:auto">' + rows + "</div>";
}

function historyTableHtml(points, meta) {
  if (!points || !points.length) {
    return (
      '<p class="persist-note">' +
      t("history.no_points", "No samples in this range") +
      "</p>"
    );
  }
  const rows = points
    .slice()
    .reverse()
    .slice(0, 400)
    .map(function (p) {
      return (
        "<tr><td class=\"mono\">" +
        escAttr(fmtTs(p.ts)) +
        '</td><td class="mono">' +
        escAttr(historyFormatValue(p.value, meta)) +
        "</td></tr>"
      );
    })
    .join("");
  return (
    '<div style="max-height:360px;overflow:auto"><table class="table"><thead><tr><th>' +
    t("history.col.time", "Time") +
    "</th><th>" +
    t("history.col.value", "Value") +
    "</th></tr></thead><tbody>" +
    rows +
    "</tbody></table></div>"
  );
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

function fmtBytes(n) {
  const v = Number(n) || 0;
  if (v < 1024) return v + " B";
  if (v < 1024 * 1024) return (v / 1024).toFixed(1) + " KB";
  return (v / (1024 * 1024)).toFixed(1) + " MB";
}

function soundsCardsHtml() {
  const snap = state.sounds || {};
  const note =
    snap.note ||
    t(
      "sounds.note",
      "Custom files play via app MediaPlayer; OEM AVAS still uses esm_sound / esm_volume.",
    );
  return (
    '<h2 class="section-label" style="margin:28px 0 10px">' +
    t("sounds.title", "Custom sounds") +
    '</h2><p class="sub" style="margin:0 0 12px">' +
    escAttr(note) +
    '</p><div class="grid">' +
    soundKindCard("avas", t("sounds.avas", "AVAS")) +
    soundKindCard("lock", t("sounds.lock", "Lock")) +
    "</div>"
  );
}

function soundKindCard(kind, title) {
  const snap = (state.sounds && state.sounds[kind]) || {};
  const files = snap.files || [];
  const active = snap.active;
  const rows = files.length
    ? '<ul style="list-style:none;margin:0;padding:0;width:100%">' +
      files
        .map(function (f) {
          const on = f.active || f.name === active;
          return (
            '<li style="display:flex;align-items:center;justify-content:space-between;gap:8px;padding:8px 0;border-bottom:1px solid var(--border)">' +
            "<div style=\"min-width:0\">" +
            "<strong" +
            (on ? ' style="color:var(--accent)"' : "") +
            ">" +
            escAttr(f.name) +
            (on
              ? ' <span class="chip">' + t("sounds.active", "active") + "</span>"
              : "") +
            '</strong><p class="sub" style="margin:2px 0 0">' +
            escAttr(fmtBytes(f.size)) +
            "</p></div>" +
            '<div class="row" style="margin:0;gap:6px;flex-shrink:0">' +
            '<button type="button" class="btn ghost" data-sound-preview="' +
            escAttr(kind) +
            '" data-name="' +
            escAttr(f.name) +
            '">' +
            t("sounds.preview", "Play") +
            "</button>" +
            (on
              ? ""
              : '<button type="button" class="btn" data-sound-apply="' +
                escAttr(kind) +
                '" data-name="' +
                escAttr(f.name) +
                '">' +
                t("sounds.apply", "Use") +
                "</button>") +
            '<button type="button" class="btn ghost" data-sound-del="' +
            escAttr(kind) +
            '" data-name="' +
            escAttr(f.name) +
            '">' +
            t("sounds.delete", "Delete") +
            "</button></div></li>"
          );
        })
        .join("") +
      "</ul>"
    : '<p class="persist-note" style="margin:0">' +
      t("sounds.empty", "No custom files yet") +
      "</p>";
  return prefCard({
    icon: kind === "lock" ? "lock" : "system",
    title: title,
    bodyHtml:
      rows +
      '<div class="row" style="width:100%;margin:12px 0 0;gap:8px">' +
      '<button type="button" class="btn primary" data-sound-upload="' +
      escAttr(kind) +
      '" style="flex:1">' +
      t("sounds.upload", "Upload") +
      "</button>" +
      (active
        ? '<button type="button" class="btn ghost" data-sound-clear="' +
          escAttr(kind) +
          '">' +
          t("sounds.clear", "Clear active") +
          "</button>"
        : "") +
      '</div><input class="hidden" type="file" accept=".wav,.mp3,.ogg,.m4a,audio/*" data-sound-file="' +
      escAttr(kind) +
      '">',
  });
}

export function sectionCameras() {
  const dvr = (state.status && state.status.dvr) || {};
  const recording = !!dvr.recording;
  const storages = dvr.storages || [];
  const storageId = dvr.storageId || "app";
  const storageOpts = storages.map(function (s) {
    const label =
      s.labelKey === "cameras.storage.sd"
        ? t("cameras.storage.sd", "SD / USB ({label})").replace("{label}", s.label || "")
        : t(s.labelKey || "cameras.storage.app", s.label || s.id);
    return { value: s.id, label: label };
  });
  if (!storageOpts.length) {
    storageOpts.push({ value: "app", label: t("cameras.storage.app", "App") });
  }
  const recOpts = [
    { value: "0", label: t("cameras.live", "Ao vivo") },
    { value: "1", label: t("cameras.recording", "Gravando…") },
  ];
  const recordings = state.recordings || [];
  const recRows = recordings.length
    ? '<ul style="list-style:none;margin:0;padding:0">' +
      recordings
        .map(function (r) {
          return (
            '<li style="display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid var(--border)">' +
            "<div style=\"min-width:0\">" +
            '<strong class="mono" style="word-break:break-all">' +
            escAttr(r.name) +
            '</strong><p class="sub" style="margin:2px 0 0">' +
            escAttr(fmtTs(r.mtime)) +
            " · " +
            escAttr(fmtBytes(r.size)) +
            "</p></div>" +
            '<div class="row" style="margin:0;gap:6px;flex-shrink:0">' +
            '<a class="btn" href="/api/dvr/recordings/' +
            encodeURIComponent(r.name) +
            '" download="' +
            escAttr(r.name) +
            '">' +
            t("cameras.download", "Download") +
            "</a>" +
            '<button type="button" class="btn ghost" data-rec-del="' +
            escAttr(r.name) +
            '">' +
            t("cameras.delete", "Delete") +
            "</button></div></li>"
          );
        })
        .join("") +
      "</ul>"
    : '<p class="persist-note">' + t("cameras.recordings.empty", "No recordings yet") + "</p>";
  return (
    "<h1>" +
    t("section.cameras.title", "Câmeras") +
    "</h1>" +
    '<div class="card" style="margin-bottom:16px">' +
    "<h2 style=\"margin:0 0 8px\">" +
    t("cameras.storage", "Salvar em") +
    '</h2><p class="sub" style="margin:0 0 10px">' +
    t("cameras.storage.shared", "Used for new recordings and the list below") +
    "</p>" +
    segmentToggleHtml(storageOpts, storageId, 'data-pref="cam-storage"') +
    "</div>" +
    '<div class="grid">' +
    prefCard({
      icon: "camera",
      title: t("cameras.rec_toggle", "Gravação"),
      bodyHtml: segmentToggleHtml(recOpts, recording ? "1" : "0", 'data-pref="cam-rec"'),
    }) +
    '</div><div class="card" style="margin-top:18px">' +
    '<img class="preview" id="preview" alt="" style="display:block;width:100%;max-height:520px;object-fit:contain;background:#000">' +
    '<p class="sub" id="prevHint"></p>' +
    (dvr.lastError ? '<p class="sub" style="color:var(--warn)">' + escAttr(dvr.lastError) + "</p>" : "") +
    '</div><div class="card" style="margin-top:18px"><div class="row" style="justify-content:space-between;align-items:center;margin:0 0 10px">' +
    "<h2 style=\"margin:0\">" +
    t("cameras.recordings", "Recordings") +
    '</h2><button type="button" class="btn" id="recRefresh">' +
    t("cameras.recordings.refresh", "Refresh") +
    "</button></div>" +
    recRows +
    "</div>"
  );
}

function escAttr(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/"/g, "&quot;");
}

export async function startCameraLive() {
  const img = $("preview");
  const hint = $("prevHint");
  if (!img) return;
  // Already streaming — do not open another MJPEG connection.
  if (
    state.cameraPreviewActive &&
    img.dataset.ocaLive === "1" &&
    img.src &&
    img.src.indexOf("preview.mjpeg") >= 0
  ) {
    return;
  }
  try {
    img.removeAttribute("src");
    img.dataset.ocaLive = "";
    await api("/api/dvr/preview/start", { method: "POST" });
    img.src = "/api/dvr/preview.mjpeg?t=" + Date.now();
    img.dataset.ocaLive = "1";
    state.cameraPreviewActive = true;
    if (hint) hint.textContent = "";
  } catch (e) {
    state.cameraPreviewActive = false;
    if (hint) hint.textContent = String(e && e.message ? e.message : e);
  }
}

export async function stopCameraLive() {
  const img = $("preview");
  if (img) {
    img.removeAttribute("src");
    img.dataset.ocaLive = "";
  }
  state.cameraPreviewActive = false;
  try {
    await api("/api/dvr/preview/stop", { method: "POST" });
  } catch (e) {}
}

export function sectionStore() {
  const q = state.storeQuery || "";
  const results = state.storeResults || [];
  const detail = state.storeDetail;
  const busy = !!state.storeBusy;
  const msg = state.storeMessage;

  let body = "";
  if (detail) {
    const versions = detail.versions || [];
    const suggested = detail.suggestedVersionCode;
    const ready = detail.installReady !== false;
    const opts = versions
      .map(function (v) {
        const sel = String(v.versionCode) === String(suggested) ? " selected" : "";
        return (
          '<option value="' +
          escAttr(v.versionCode) +
          '"' +
          sel +
          ">" +
          escAttr(v.versionName || v.versionCode) +
          " (" +
          escAttr(v.versionCode) +
          ")</option>"
        );
      })
      .join("");
    body =
      '<div class="card" style="margin-top:12px">' +
      '<button class="btn" id="storeBack" type="button">' +
      t("store.back", "Voltar") +
      "</button>" +
      '<div style="display:flex;gap:16px;margin-top:14px;align-items:flex-start">' +
      (detail.iconUrl
        ? '<img src="' +
          escAttr(detail.iconUrl) +
          '" alt="" width="72" height="72" style="border-radius:16px;object-fit:cover;background:var(--surface-2)">'
        : "") +
      "<div style=\"flex:1\">" +
      "<h2 style=\"margin:0 0 6px\">" +
      escAttr(detail.name || detail.packageName) +
      "</h2>" +
      '<p class="mono sub" style="margin:0 0 8px">' +
      escAttr(detail.packageName) +
      "</p>" +
      '<p class="sub">' +
      escAttr(detail.summary || "") +
      "</p>" +
      (!ready
        ? '<p class="sub" style="color:var(--warn)">' +
          t(
            "store.need_mirror",
            "Sem URL de download. Configure apkUrl em store/extras.json (espelho próprio).",
          ) +
          "</p>"
        : "") +
      "</div></div>" +
      (versions.length
        ? '<label class="sub" style="display:block;margin-top:14px">' +
          t("store.version", "Versão") +
          '</label><select class="field" id="storeVer" style="width:100%;margin-top:6px">' +
          opts +
          "</select>"
        : "") +
      '<button class="btn primary" id="storeInstall" style="width:100%;margin-top:14px"' +
      (busy || !ready ? " disabled" : "") +
      ">" +
      (busy
        ? t("store.installing", "Baixando e instalando…")
        : t("store.install", "Instalar")) +
      "</button>" +
      (msg
        ? '<p class="persist-note" id="storeOut" style="margin-top:12px">' + escAttr(msg) + "</p>"
        : "") +
      "</div>";
  } else {
    const rows = results
      .map(function (a) {
        return (
          '<button type="button" class="card store-hit" data-pkg="' +
          escAttr(a.packageName) +
          '" style="display:flex;gap:14px;align-items:center;width:100%;text-align:left;cursor:pointer;margin-bottom:10px">' +
          (a.iconUrl
            ? '<img src="' +
              escAttr(a.iconUrl) +
              '" alt="" width="48" height="48" style="border-radius:12px;object-fit:cover;background:var(--surface-2);flex-shrink:0">'
            : '<span class="ico" data-icon="store" style="flex-shrink:0"></span>') +
          "<div style=\"min-width:0;flex:1\">" +
          "<strong>" +
          escAttr(a.name) +
          (a.installReady === false
            ? ' <span class="chip" style="font-size:0.7rem">' +
              t("store.no_url", "sem URL") +
              "</span>"
            : "") +
          "</strong>" +
          '<p class="sub" style="margin:0.25rem 0 0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
          escAttr(a.summary || a.packageName) +
          "</p></div></button>"
        );
      })
      .join("");
    body =
      '<div style="display:flex;gap:10px;margin-top:8px">' +
      '<input class="field" id="storeQ" type="search" placeholder="' +
      escAttr(t("store.search_ph", "Buscar apps")) +
      '" value="' +
      escAttr(q) +
      '" style="flex:1">' +
      '<button class="btn primary" id="storeGo" type="button"' +
      (busy ? " disabled" : "") +
      ">" +
      t("store.search", "Buscar") +
      "</button></div>" +
      (msg && !results.length
        ? '<p class="sub" style="margin-top:12px">' + escAttr(msg) + "</p>"
        : "") +
      '<div style="margin-top:16px">' +
      (busy && !results.length
        ? '<p class="sub">' + t("store.searching", "Buscando…") + "</p>"
        : rows ||
          (q
            ? '<p class="sub">' + t("store.empty", "Nenhum resultado") + "</p>"
            : "")) +
      "</div>";
  }

  return (
    '<div class="section-head">' +
    "<h1>" +
    t("section.store.title", "Loja") +
    "</h1>" +
    '<button class="btn" id="apkPick" type="button">' +
    t("install.title", "Instalar APK") +
    "</button>" +
    '<input class="hidden" type="file" id="apk" accept=".apk">' +
    "</div>" +
    '<p class="persist-note hidden" id="apkOut" style="margin:0 0 12px"></p>' +
    body
  );
}

export function sectionSystem() {
  const th = theme();
  const locale = (state.i18n && state.i18n.locale) || "pt-BR";
  const locales = (state.i18n && state.i18n.locales) || ["pt-BR", "en"];
  const adb = state.adb || {};

  const themeOpts = [
    { value: "dark", label: t("theme.dark", "Dark") },
    { value: "light", label: t("theme.light", "Light") },
    { value: "contrast", label: t("theme.contrast", "Contrast") },
  ];
  const localeOpts = locales.map(function (loc) {
    return {
      value: loc,
      label: loc === "pt-BR" ? t("locale.pt-BR", "Português") : t("locale." + loc, loc),
    };
  });
  const adbBody =
    boolToggleHtml(adb.enabled, 'data-pref="adb"') +
    (adb.enabled && adb.port
      ? '<p class="persist-note" style="margin:10px 0 0">' +
        t("system.adb.port", "Porta") +
        " " +
        adb.port +
        "</p>"
      : "") +
    (adb.canToggle === false
      ? '<p class="persist-note">' +
        t("system.adb.unavailable", "Toggle unavailable on this build") +
        "</p>"
      : "");

  return (
    "<h1>" +
    t("nav.system", "Sistema") +
    "</h1>" +
    '<div class="grid">' +
    quickEntryCardHtml() +
    prefCard({
      icon: "system",
      title: t("prefs.theme", "Tema"),
      bodyHtml: segmentToggleHtml(themeOpts, th, 'data-pref="theme"'),
    }) +
    prefCard({
      icon: "about",
      title: t("prefs.locale", "Idioma"),
      bodyHtml: segmentToggleHtml(localeOpts, locale, 'data-pref="locale"'),
    }) +
    prefCard({
      icon: "lab",
      title: t("system.adb.title", "ADB sem fio"),
      bodyHtml: adbBody,
    }) +
    prefCard({
      icon: "system",
      title: t("setup.title", "Configuração"),
      bodyHtml:
        '<div class="row" style="width:100%;margin:0">' +
        '<button class="btn" id="openSetupBtn" style="flex:1">' +
        t("setup.open", "Abrir setup") +
        "</button>" +
        '<button class="btn ghost" id="resetSetup" style="flex:1">' +
        t("setup.reset", "Resetar setup") +
        "</button></div>",
    }) +
    prefCard({
      icon: "hide",
      title: t("entity.hidden.title", "Hidden cards"),
      bodyHtml: hiddenCardsBody(),
    }) +
    "</div>"
  );
}

function hiddenCardsBody() {
  const list = state.hiddenEntities || [];
  if (!list.length) {
    return (
      '<p class="persist-note" style="margin:0">' +
      escAttr(t("entity.hidden.empty", "No hidden cards")) +
      "</p>"
    );
  }
  return (
    '<ul class="hidden-entity-list" style="list-style:none;margin:0;padding:0;width:100%">' +
    list
      .map(function (e) {
        return (
          '<li style="display:flex;align-items:center;justify-content:space-between;gap:8px;padding:6px 0;border-bottom:1px solid var(--border)">' +
          '<span>' +
          escAttr(e.label || e.id) +
          '</span><button type="button" class="btn ghost" data-entity-unhide="' +
          escAttr(e.id) +
          '">' +
          escAttr(t("entity.unhide", "Show card")) +
          "</button></li>"
        );
      })
      .join("") +
    "</ul>"
  );
}

export function sectionLab() {
  const lab = state.lab || {};
  const tab = state.labTab || "vhal";
  const p =
    tab === "obd2" ? state.obd2 : tab === "entities" ? null : state.probe;
  const sum =
    tab === "entities"
      ? {
          source: "ControlCatalog /api/entities",
          count: (state.entities || []).length,
        }
      : p && p.summary
        ? p.summary
        : p;
  const sourceHint =
    tab === "obd2"
      ? t("lab.source.obd2", "OBD2_LIVE_FRAME / OBD2_FREEZE_FRAME (VHAL)")
      : tab === "entities"
        ? t("lab.source.entities", "Bound product entities (ControlCatalog)")
        : t("lab.source.vhal", "VHAL catalog (CarPropertyManager / gRPC)");
  const contributorOn = !!lab.contributor;
  const token = (contributorOn && (lab.token || state.token)) || "";
  const override = lab.integrationOverride || "";
  const integrations = lab.integrations || [];
  const opts =
    '<option value="">' +
    escAttr(t("lab.override.auto", "Auto (fingerprint match)")) +
    "</option>" +
    integrations
      .map(function (id) {
        return (
          '<option value="' +
          escAttr(id) +
          '"' +
          (id === override ? " selected" : "") +
          ">" +
          escAttr(id) +
          "</option>"
        );
      })
      .join("");
  const tabOpts = [
    { value: "vhal", label: t("lab.tab.vhal", "VHAL catalog") },
    { value: "obd2", label: t("lab.tab.obd2", "OBD2") },
    { value: "entities", label: t("lab.tab.entities", "Product entities") },
  ];
  return (
    "<h1>" +
    t("lab.title", "Lab / Contributor") +
    '</h1><p class="sub">' +
    t("lab.sub", "Probe data sources · enable Contributor mode for /debug writes") +
    '</p><div class="card" style="margin-bottom:16px"><div class="row" style="align-items:center;gap:12px;flex-wrap:wrap">' +
    "<label style=\"display:flex;align-items:center;gap:8px\">" +
    '<input type="checkbox" id="labContributor"' +
    (contributorOn ? " checked" : "") +
    "> " +
    t("lab.contributor", "Contributor mode") +
    "</label>" +
    '<span class="mono">' +
    t("lab.token", "Token") +
    ": " +
    (token ? "<code>" + escAttr(token) + "</code>" : "—") +
    "</span></div>" +
    '<div class="row" style="align-items:center;gap:12px;margin-top:12px;flex-wrap:wrap">' +
    "<label>" +
    t("lab.override", "Integration override") +
    ' <select id="labOverride">' +
    opts +
    "</select></label>" +
    '<button class="btn" id="labOverrideApply">' +
    t("lab.override.apply", "Apply") +
    "</button></div>" +
    (lab.restartHint
      ? '<p class="sub" style="margin:8px 0 0;color:var(--warn, #c90)">' + escAttr(lab.restartHint) + "</p>"
      : '<p class="sub" style="margin:8px 0 0">' +
        t(
          "lab.override.hint",
          "Override applies after force-stop or reboot. Matched now: ",
        ) +
        '<code class="mono">' +
        escAttr(lab.integration || "—") +
        "</code></p>") +
    '</div><div class="card" style="margin-bottom:12px">' +
    segmentToggleHtml(tabOpts, tab, 'data-pref="lab-tab"') +
    '<p class="sub" style="margin:10px 0 0">' +
    escAttr(sourceHint) +
    "</p></div>" +
    '<div class="card"><div class="row">' +
    '<button class="btn primary" id="probeRun">' +
    t("lab.probe", "Re-probe") +
    "</button>" +
    '<a class="btn" href="/debug/export?token=' +
    encodeURIComponent(token) +
    '">' +
    t("lab.export", "Export zip") +
    '</a>' +
    '<input id="probeQ" type="text" placeholder="' +
    escAttr(t("lab.filter", "filter…")) +
    '" value="' +
    escAttr(state.probeFilter || "") +
    '" style="flex:1"></div>' +
    '<pre class="mono" id="probeSum">' +
    JSON.stringify(sum || { tip: t("lab.probe.tip", "Click Re-probe") }, null, 2) +
    '</pre><div id="probeScroll" style="max-height:420px;overflow:auto"><table class="table" id="probeTable"><thead><tr><th>Nome</th><th>Família</th><th>Status</th><th>Valor</th><th>Perm</th></tr></thead><tbody></tbody></table></div></div>'
  );
}

export function sectionAbout() {
  const s = state.status || {};
  const setup = state.setup || {};
  const rows = [
    ["integration", s.integration],
    ["variant", s.variant],
    ["webPort", s.webPort],
    ["privileged", setup.privilegedOk ? "ok" : "optional"],
  ]
    .filter(function (r) {
      return r[1] != null && r[1] !== "";
    })
    .map(function (r) {
      return (
        '<div style="display:flex;justify-content:space-between;gap:12px;padding:6px 0;border-bottom:1px solid var(--border)">' +
        "<span>" +
        escAttr(r[0]) +
        '</span><span class="mono">' +
        escAttr(String(r[1])) +
        "</span></div>"
      );
    })
    .join("");
  return (
    "<h1>" +
    t("nav.about", "Sobre") +
    "</h1>" +
    aboutPrivilegeTipHtml() +
    '<div class="card"><p style="margin:0 0 12px">' +
    t("about.blurb", "Open Car Assistant — shell unificado HU + web.") +
    "</p>" +
    rows +
    "</div>"
  );
}

export function fillProbeTable() {
  const tab = state.labTab || "vhal";
  const q = (($("probeQ") && $("probeQ").value) || state.probeFilter || "").toLowerCase();
  if ($("probeQ")) state.probeFilter = $("probeQ").value || "";
  let rows;
  if (tab === "entities") {
    rows = (state.entities || []).map(function (e) {
      return {
        name: e.id || e.label || "",
        family: e.entity || e.group || "",
        status: e.status || "",
        value: e.valueLabel || e.value || "",
        permission: e.group || "",
      };
    });
  } else {
    const src = tab === "obd2" ? state.obd2 : state.probe;
    rows = (src && src.results) || [];
  }
  rows = rows.filter(function (r) {
    if (!q) return true;
    return (r.name + r.family + r.status + (r.permission || "") + (r.value || ""))
      .toLowerCase()
      .indexOf(q) >= 0;
  });
  const tb = document.querySelector("#probeTable tbody");
  if (!tb) return;
  const scroller = $("probeScroll");
  const prevScroll = scroller ? scroller.scrollTop : 0;
  tb.innerHTML = rows
    .slice(0, 400)
    .map(function (r) {
      return (
        '<tr><td class="mono">' +
        escAttr(r.name) +
        "</td><td>" +
        escAttr(r.family) +
        "</td><td>" +
        escAttr(r.status) +
        '</td><td class="mono">' +
        escAttr(fmt(r.value)) +
        '</td><td class="mono">' +
        escAttr(fmt(r.permission)) +
        "</td></tr>"
      );
    })
    .join("");
  if (scroller) scroller.scrollTop = prevScroll;
}

export async function setControl(id, val) {
  await api("/api/controls/" + encodeURIComponent(id), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "value=" + encodeURIComponent(val),
  });
}

async function setPersist(id, opts) {
  const body = new URLSearchParams();
  if (opts.enabled != null) body.set("enabled", opts.enabled ? "1" : "0");
  if (opts.value != null) body.set("value", String(opts.value));
  await api("/api/controls/" + encodeURIComponent(id) + "/persist", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
}

function findControl(id) {
  return (state.controls || []).concat(state.entities || []).find(function (c) {
    return c.id === id;
  });
}

function applyControlValue(id, val) {
  return setControl(id, val);
}

export function bindSection(refresh) {
  if (!window.__ocaChoiceCloseBound) {
    window.__ocaChoiceCloseBound = true;
    document.addEventListener("click", function () {
      document.querySelectorAll(".choice-select.open").forEach(function (root) {
        root.classList.remove("open");
        const trigger = root.querySelector("[data-choice-trigger]");
        const menu = root.querySelector(".choice-menu");
        if (trigger) trigger.setAttribute("aria-expanded", "false");
        if (menu) menu.setAttribute("hidden", "");
      });
    });
  }
  document.querySelectorAll("[data-choice-trigger]").forEach(function (el) {
    el.onclick = function (ev) {
      ev.stopPropagation();
      const root = el.closest("[data-choice-select]");
      if (!root || el.disabled) return;
      const willOpen = !root.classList.contains("open");
      document.querySelectorAll(".choice-select.open").forEach(function (other) {
        other.classList.remove("open");
        const t = other.querySelector("[data-choice-trigger]");
        const m = other.querySelector(".choice-menu");
        if (t) t.setAttribute("aria-expanded", "false");
        if (m) m.setAttribute("hidden", "");
      });
      if (willOpen) {
        root.classList.add("open");
        el.setAttribute("aria-expanded", "true");
        const menu = root.querySelector(".choice-menu");
        if (menu) menu.removeAttribute("hidden");
      }
    };
  });
  document.querySelectorAll("[data-ctrl]").forEach(function (el) {
    el.onclick = function (ev) {
      if (el.closest(".choice-menu")) ev.stopPropagation();
      const id = el.getAttribute("data-ctrl");
      const val = el.getAttribute("data-val");
      applyControlValue(id, val).then(refresh);
    };
  });
  document.querySelectorAll("[data-ctrl-select]").forEach(function (el) {
    el.onchange = function () {
      const id = el.getAttribute("data-ctrl-select");
      applyControlValue(id, el.value).then(refresh);
    };
  });
  document.querySelectorAll("[data-step]").forEach(function (el) {
    el.onclick = function () {
      const id = el.getAttribute("data-step");
      const delta = parseFloat(el.getAttribute("data-delta") || "1");
      const c = findControl(id) || {};
      const curRaw = faceValue(c);
      let cur = parseFloat(curRaw);
      if (isNaN(cur)) cur = c.min != null ? Number(c.min) : 0;
      let next = cur + delta;
      if (delta < 0 && c.min != null) next = Math.max(Number(c.min), next);
      if (delta > 0 && c.max != null) next = Math.min(Number(c.max), next);
      // Never let + decrease (or − increase) when live value is outside [min,max].
      if (delta > 0 && next < cur) next = cur;
      if (delta < 0 && next > cur) next = cur;
      if (next === cur) return;
      if (c.input === "int" || (c.step && Number(c.step) === 1)) next = Math.round(next);
      else next = Math.round(next * 10) / 10;
      applyControlValue(id, String(next)).then(refresh);
    };
  });
  document.querySelectorAll("[data-persist-pin]").forEach(function (el) {
    el.onclick = function (ev) {
      ev.stopPropagation();
      const id = el.getAttribute("data-persist-pin");
      const c = findControl(id) || {};
      if (c.persistEnabled) {
        setPersist(id, { enabled: false }).then(refresh);
        return;
      }
      if (c.value == null || c.value === "") return;
      setPersist(id, { enabled: true, value: String(c.value) }).then(refresh);
    };
  });
  document.querySelectorAll("[data-entity-hide]").forEach(function (el) {
    el.onclick = function (ev) {
      ev.stopPropagation();
      const id = el.getAttribute("data-entity-hide");
      api("/api/entities/" + encodeURIComponent(id) + "/visibility", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "hidden=1",
      }).then(refresh);
    };
  });
  document.querySelectorAll("[data-entity-unhide]").forEach(function (el) {
    el.onclick = function (ev) {
      ev.stopPropagation();
      const id = el.getAttribute("data-entity-unhide");
      api("/api/entities/" + encodeURIComponent(id) + "/visibility", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "hidden=0",
      }).then(function () {
        return api("/api/entities/hidden");
      }).then(function (res) {
        state.hiddenEntities = (res && res.entities) || [];
        refresh();
      });
    };
  });
  document.querySelectorAll("[data-pref]").forEach(function (el) {
    const runPref = async function (next) {
      const pref = el.getAttribute("data-pref");
      if (pref === "theme") {
        setTheme(next);
        await refresh();
        return;
      }
      if (pref === "locale") {
        await setLocale(next);
        await refresh();
        return;
      }
      if (pref === "adb") {
        if (next === "0" && !confirm(t("system.adb.warn", "Disable wireless ADB?"))) return;
        const res = await api("/api/adb", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: next === "1" ? "enabled=1&port=5566" : "enabled=0",
        });
        state.adbMessage = null;
        if (res && res.ok === false) {
          alert(res.message || t("system.adb.failed", "Wireless ADB toggle failed"));
        }
        await refresh();
        return;
      }
      if (pref === "ha-enabled") {
        const group = el.closest(".toggle-group");
        if (group) {
          group.querySelectorAll(".toggle-seg").forEach(function (seg) {
            const on = String(seg.getAttribute("data-val")) === String(next);
            seg.classList.toggle("active", on);
            seg.setAttribute("aria-pressed", on ? "true" : "false");
          });
        }
        return;
      }
      if (pref === "lab-tab") {
        state.labTab = next;
        if (next === "obd2" && !state.obd2) {
          try {
            state.obd2 = await api("/debug/obd2?token=" + encodeURIComponent(state.token));
          } catch (e) {
            state.obd2 = { results: [], summary: { available: false } };
          }
        }
        await refresh();
        return;
      }
      if (pref === "cam-storage") {
        await api("/api/dvr/storage", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: "id=" + encodeURIComponent(next),
        });
        if (state.status && state.status.dvr) {
          state.status.dvr.storageId = next;
        }
        await loadRecordings();
        // One intentional remount to refresh the list; preview restarts once.
        state.cameraPreviewActive = false;
        await refresh();
        return;
      }
      if (pref === "hist-range") {
        state.historyRangeHours = parseInt(next, 10) || 24;
        await loadHistoryPoints();
        await refresh();
        return;
      }
      if (pref === "hist-view") {
        state.historyView = next;
        await refresh();
        return;
      }
      if (pref === "cam-rec") {
        const storageEl =
          document.querySelector('.toggle-seg.active[data-pref="cam-storage"]') ||
          document.querySelector('.choice-opt.active[data-pref="cam-storage"]') ||
          document.querySelector('select[data-pref="cam-storage"]');
        const storage = storageEl
          ? storageEl.tagName === "SELECT"
            ? storageEl.value
            : storageEl.getAttribute("data-val")
          : "";
        await api("/api/dvr/toggle?storage=" + encodeURIComponent(storage || ""), {
          method: "POST",
        });
        await refresh();
        return;
      }
      if (pref === "sc-overlay") {
        await api("/api/shortcuts/overlay", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ enabled: next === "1" }),
        });
        const { loadShortcuts } = await import("./shortcuts.js");
        await loadShortcuts();
        await refresh();
        return;
      }
      if (pref === "sc-slot") {
        const slot = parseInt(el.getAttribute("data-slot"), 10);
        await api("/api/shortcuts/slots", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            slot: isNaN(slot) ? 0 : slot,
            shortcutId: next || null,
          }),
        });
        const { loadShortcuts } = await import("./shortcuts.js");
        await loadShortcuts();
        await refresh();
        return;
      }
      if (pref === "sc-enabled") {
        // Editor-only toggle; value read on Save.
        const group = el.closest(".toggle-group");
        if (group) {
          group.querySelectorAll(".toggle-seg").forEach(function (seg) {
            const on = String(seg.getAttribute("data-val")) === String(next);
            seg.classList.toggle("active", on);
            seg.setAttribute("aria-pressed", on ? "true" : "false");
          });
        }
      }
    };
    if (el.tagName === "SELECT") {
      el.onchange = function () {
        runPref(el.value);
      };
      return;
    }
    el.onclick = function (ev) {
      if (el.closest(".choice-menu")) ev.stopPropagation();
      const next = el.getAttribute("data-val");
      if (next == null) return;
      runPref(next);
    };
  });
  bindPrivilegeTip();
  if ($("scOverlayGrant")) {
    $("scOverlayGrant").onclick = async function () {
      await api("/api/shortcuts/overlay/request", { method: "POST" });
    };
  }
  if ($("openSetupBtn")) {
    $("openSetupBtn").onclick = function () {
      state.showSetup = true;
      import("./setup.js").then(function (m) {
        m.renderSetupOverlay();
      });
    };
  }
  if ($("resetSetup")) {
    $("resetSetup").onclick = async function () {
      state.setup = await api("/api/setup", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "reset=1",
      });
      state.showSetup = true;
      const m = await import("./setup.js");
      m.renderSetupOverlay();
    };
  }
  if ($("apkPick") && $("apk")) {
    $("apkPick").onclick = function () {
      $("apk").click();
    };
    $("apk").onchange = async function () {
      const f = $("apk").files && $("apk").files[0];
      if (!f) return;
      const out = $("apkOut");
      if (out) {
        out.classList.remove("hidden");
        out.textContent = t("store.installing", "Baixando e instalando…");
      }
      try {
        const buf = await f.arrayBuffer();
        const r = await fetch("/api/install/binary", {
          method: "POST",
          headers: { "Content-Type": "application/octet-stream" },
          body: buf,
        });
        const json = await r.json();
        if (out) {
          out.textContent =
            json && json.ok === false
              ? json.message || json.error || t("store.install_failed", "Install failed")
              : t("store.installed", "Installed");
        }
      } catch (e) {
        if (out) out.textContent = String(e && e.message ? e.message : e);
      }
      $("apk").value = "";
    };
  }
  if ($("storeGo") || $("storeQ")) {
    const runSearch = async function () {
      const q = (($("storeQ") && $("storeQ").value) || "").trim();
      state.storeQuery = q;
      state.storeDetail = null;
      state.storeBusy = true;
      state.storeMessage = null;
      refresh();
      try {
        const res = await api("/api/store/search?q=" + encodeURIComponent(q));
        state.storeResults = (res && res.apps) || [];
        if (!state.storeResults.length) {
          state.storeMessage = t("store.empty", "Nenhum resultado");
        }
      } catch (e) {
        state.storeResults = [];
        state.storeMessage = String(e && e.message ? e.message : e);
      }
      state.storeBusy = false;
      refresh();
    };
    if ($("storeGo")) $("storeGo").onclick = runSearch;
    if ($("storeQ"))
      $("storeQ").onkeydown = function (ev) {
        if (ev.key === "Enter") runSearch();
      };
  }
  document.querySelectorAll(".store-hit[data-pkg]").forEach(function (el) {
    el.onclick = async function () {
      const pkg = el.getAttribute("data-pkg");
      state.storeBusy = true;
      state.storeMessage = null;
      refresh();
      try {
        const res = await api("/api/store/package/" + encodeURIComponent(pkg));
        state.storeDetail = res;
      } catch (e) {
        state.storeMessage = String(e && e.message ? e.message : e);
      }
      state.storeBusy = false;
      refresh();
    };
  });
  if ($("storeBack"))
    $("storeBack").onclick = function () {
      state.storeDetail = null;
      state.storeMessage = null;
      refresh();
    };
  if ($("storeInstall"))
    $("storeInstall").onclick = async function () {
      const detail = state.storeDetail;
      if (!detail) return;
      const vc = $("storeVer") && $("storeVer").value;
      state.storeBusy = true;
      state.storeMessage = t("store.installing", "Baixando e instalando…");
      refresh();
      try {
        const body = new URLSearchParams({
          packageName: detail.packageName,
        });
        if (vc) body.set("versionCode", vc);
        const res = await api("/api/store/install", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: body.toString(),
        });
        if (res && res.ok === false) {
          state.storeMessage = res.message || res.error || t("store.install_failed", "Install failed");
        } else {
          state.storeMessage = t("store.installed", "Installed");
        }
      } catch (e) {
        state.storeMessage = String(e && e.message ? e.message : e);
      }
      state.storeBusy = false;
      refresh();
    };
  if ($("openAndroidSettings")) {
    $("openAndroidSettings").onclick = async function () {
      await api("/api/system/open-android-settings", { method: "POST" });
    };
  }
  if ($("goHistory")) {
    $("goHistory").onclick = function () {
      if (typeof window.__ocaGoSection === "function") window.__ocaGoSection("history");
    };
  }
  if ($("histLoad") || $("histEntity")) {
    const runHist = async function () {
      const id = ($("histEntity") && $("histEntity").value) || state.historySelected;
      state.historySelected = id || null;
      await loadHistoryPoints();
      refresh();
    };
    if ($("histLoad")) $("histLoad").onclick = runHist;
    if ($("histEntity"))
      $("histEntity").onchange = function () {
        state.historySelected = $("histEntity").value;
        runHist();
      };
  }
  if ($("recRefresh")) {
    $("recRefresh").onclick = async function () {
      await loadRecordings();
      refresh();
    };
  }
  document.querySelectorAll("[data-rec-del]").forEach(function (el) {
    el.onclick = async function () {
      const name = el.getAttribute("data-rec-del");
      if (!name || !confirm(t("cameras.delete.confirm", "Delete this recording?"))) return;
      await api("/api/dvr/recordings/" + encodeURIComponent(name), { method: "DELETE" });
      await loadRecordings();
      refresh();
    };
  });
  document.querySelectorAll("[data-sound-upload]").forEach(function (el) {
    el.onclick = function () {
      const kind = el.getAttribute("data-sound-upload");
      const input = document.querySelector('[data-sound-file="' + kind + '"]');
      if (input) input.click();
    };
  });
  document.querySelectorAll("[data-sound-file]").forEach(function (input) {
    input.onchange = async function () {
      const kind = input.getAttribute("data-sound-file");
      const f = input.files && input.files[0];
      if (!f || !kind) return;
      try {
        const buf = await f.arrayBuffer();
        const r = await fetch(
          "/api/sounds/upload?kind=" +
            encodeURIComponent(kind) +
            "&name=" +
            encodeURIComponent(f.name),
          {
            method: "POST",
            headers: { "Content-Type": "application/octet-stream", "X-Filename": f.name },
            body: buf,
          },
        );
        const json = await r.json();
        if (json && json.ok === false) {
          alert(json.error || t("sounds.upload_failed", "Upload failed"));
        }
      } catch (e) {
        alert(String(e && e.message ? e.message : e));
      }
      input.value = "";
      await loadSounds();
      refresh();
    };
  });
  document.querySelectorAll("[data-sound-apply]").forEach(function (el) {
    el.onclick = async function () {
      const kind = el.getAttribute("data-sound-apply");
      const name = el.getAttribute("data-name");
      await api("/api/sounds/apply", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body:
          "kind=" + encodeURIComponent(kind) + "&name=" + encodeURIComponent(name || ""),
      });
      await loadSounds();
      refresh();
    };
  });
  document.querySelectorAll("[data-sound-clear]").forEach(function (el) {
    el.onclick = async function () {
      const kind = el.getAttribute("data-sound-clear");
      await api("/api/sounds/apply", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "kind=" + encodeURIComponent(kind) + "&name=",
      });
      await loadSounds();
      refresh();
    };
  });
  document.querySelectorAll("[data-sound-preview]").forEach(function (el) {
    el.onclick = async function () {
      const kind = el.getAttribute("data-sound-preview");
      const name = el.getAttribute("data-name");
      await api("/api/sounds/preview", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body:
          "kind=" + encodeURIComponent(kind) + "&name=" + encodeURIComponent(name || ""),
      });
    };
  });
  document.querySelectorAll("[data-sound-del]").forEach(function (el) {
    el.onclick = async function () {
      const kind = el.getAttribute("data-sound-del");
      const name = el.getAttribute("data-name");
      if (!name || !confirm(t("sounds.delete.confirm", "Delete this sound?"))) return;
      await api("/api/sounds/" + encodeURIComponent(kind) + "/" + encodeURIComponent(name), {
        method: "DELETE",
      });
      await loadSounds();
      refresh();
    };
  });
  if ($("labContributor")) {
    $("labContributor").onchange = async function () {
      const enabled = $("labContributor").checked ? "1" : "0";
      try {
        state.lab = await api("/api/lab/contributor", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: "enabled=" + enabled,
        });
        if (state.lab && state.lab.token) state.token = state.lab.token;
        else state.token = "";
      } catch (e) {
        state.lab = state.lab || {};
        state.lab.restartHint = String(e && e.message ? e.message : e);
      }
      refresh();
    };
  }
  if ($("labOverrideApply")) {
    $("labOverrideApply").onclick = async function () {
      const id = ($("labOverride") && $("labOverride").value) || "";
      try {
        const res = await api("/api/lab/integration-override", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: "id=" + encodeURIComponent(id),
        });
        state.lab = res;
        if (res && res.hint) state.lab.restartHint = res.hint;
      } catch (e) {
        state.lab = state.lab || {};
        state.lab.restartHint = String(e && e.message ? e.message : e);
      }
      refresh();
    };
  }
  if ($("probeRun"))
    $("probeRun").onclick = async function () {
      $("probeSum").textContent = t("lab.probe.running", "Running probe…");
      const tab = state.labTab || "vhal";
      const tok = encodeURIComponent(state.token);
      if (tab === "obd2") {
        state.obd2 = await api("/debug/obd2?force=1&token=" + tok);
      } else if (tab === "entities") {
        state.entities = await api("/api/entities");
      } else {
        state.probe = await api("/debug/probe?force=1&token=" + tok);
      }
      refresh();
    };
  if ($("probeQ")) $("probeQ").oninput = fillProbeTable;
  mountNavIcons();
}

export async function loadHistoryPoints() {
  const id = state.historySelected || (state.historyEntities && state.historyEntities[0]);
  if (!id) {
    state.historyPoints = [];
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
    state.historyPoints = (res && res.points) || [];
  } catch (e) {
    state.historyPoints = [];
  }
}

export async function loadRecordings() {
  try {
    const res = await api("/api/dvr/recordings");
    state.recordings = (res && res.recordings) || [];
  } catch (e) {
    state.recordings = [];
  }
}

export async function loadSounds() {
  try {
    state.sounds = await api("/api/sounds");
  } catch (e) {
    state.sounds = null;
  }
}
