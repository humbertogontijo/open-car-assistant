import { api, $, fmt } from "./api.js";
import { state, entitiesByGroup } from "./state.js";
import { theme, setTheme } from "./theme.js";
import {
  renderEntityGrid,
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
  const histEntities = (state.historyEntities || []).slice(0, 8);
  const histBody =
    histEntities.length === 0
      ? '<p class="persist-note">' + t("history.empty", "No history yet") + "</p>"
      : '<ul style="margin:0;padding-left:18px">' +
        histEntities
          .map(function (id) {
            return "<li>" + escAttr(id) + "</li>";
          })
          .join("") +
        "</ul>";
  return (
    "<h1>" +
    t("section.home.title", "Início") +
    "</h1>" +
    renderEntityGrid(home) +
    '<div class="card" style="margin-top:18px">' +
    "<h2>" +
    t("section.history.title", "History") +
    "</h2>" +
    histBody +
    "</div>"
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

export function sectionGroup(title, sub, group) {
  return (
    "<h1>" +
    title +
    "</h1>" +
    subHtml(sub) +
    renderEntityGrid(entitiesByGroup(group))
  );
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
  return (
    "<h1>" +
    t("section.cameras.title", "Câmeras") +
    "</h1>" +
    '<div class="grid">' +
    prefCard({
      icon: "camera",
      title: t("cameras.storage", "Salvar em"),
      bodyHtml: segmentToggleHtml(storageOpts, storageId, 'data-pref="cam-storage"'),
    }) +
    prefCard({
      icon: "camera",
      title: t("cameras.rec_toggle", "Gravação"),
      bodyHtml: segmentToggleHtml(recOpts, recording ? "1" : "0", 'data-pref="cam-rec"'),
    }) +
    '</div><div class="card" style="margin-top:18px">' +
    '<img class="preview" id="preview" alt="preview" style="display:block;width:100%;max-height:520px;object-fit:contain;background:#000">' +
    '<p class="sub" id="prevHint"></p>' +
    (dvr.lastError ? '<p class="sub" style="color:var(--warn)">' + escAttr(dvr.lastError) + "</p>" : "") +
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
  try {
    await api("/api/dvr/preview/start", { method: "POST" });
    img.src = "/api/dvr/preview.mjpeg?t=" + Date.now();
    if (hint) hint.textContent = "";
  } catch (e) {
    if (hint) hint.textContent = String(e && e.message ? e.message : e);
  }
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
  const p = state.probe;
  const sum = p && p.summary ? p.summary : p;
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
  return (
    "<h1>" +
    t("lab.title", "Lab / Contributor") +
    '</h1><p class="sub">' +
    t("lab.sub", "Probe VHAL · enable Contributor mode for /debug writes") +
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
    '</div><div class="card"><div class="row">' +
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
    '" style="flex:1"></div>' +
    '<pre class="mono" id="probeSum">' +
    JSON.stringify(sum || { tip: t("lab.probe.tip", "Click Re-probe") }, null, 2) +
    '</pre><div style="max-height:420px;overflow:auto"><table class="table" id="probeTable"><thead><tr><th>Nome</th><th>Família</th><th>Status</th><th>Valor</th><th>Perm</th></tr></thead><tbody></tbody></table></div></div>'
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
  const q = (($("probeQ") && $("probeQ").value) || "").toLowerCase();
  const rows = ((state.probe && state.probe.results) || []).filter(function (r) {
    if (!q) return true;
    return (r.name + r.family + r.status + (r.permission || "")).toLowerCase().indexOf(q) >= 0;
  });
  const tb = document.querySelector("#probeTable tbody");
  if (!tb) return;
  tb.innerHTML = rows
    .slice(0, 400)
    .map(function (r) {
      return (
        '<tr><td class="mono">' +
        r.name +
        "</td><td>" +
        r.family +
        "</td><td>" +
        r.status +
        '</td><td class="mono">' +
        fmt(r.value) +
        '</td><td class="mono">' +
        fmt(r.permission) +
        "</td></tr>"
      );
    })
    .join("");
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
      if (pref === "cam-storage") {
        await api("/api/dvr/storage", {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: "id=" + encodeURIComponent(next),
        });
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
      state.probe = await api("/debug/probe?force=1&token=" + encodeURIComponent(state.token));
      refresh();
    };
  if ($("probeQ")) $("probeQ").oninput = fillProbeTable;
  mountNavIcons();
}
