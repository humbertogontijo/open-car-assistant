import { api, $ } from "./api.js";
import { state } from "./state.js";
import {
  sectionHome,
  sectionGroup,
  sectionCabin,
  sectionCameras,
  sectionHistory,
  sectionStore,
  sectionSystem,
  sectionAndroid,
  sectionLab,
  sectionAbout,
  bindSection,
  fillProbeTable,
  loadHistoryPoints,
  loadRecordings,
  loadSounds,
  startCameraLive,
  stopCameraLive,
} from "./sections.js";
import { sectionShortcuts, bindShortcuts, loadShortcuts } from "./shortcuts.js";
import { sectionPlugins, bindPlugins } from "./plugins.js";
import {
  shouldShowSetup,
  renderSetupOverlay,
  renderStatusBar,
} from "./setup.js";
import { setTheme } from "./theme.js";
import { loadI18n, t } from "./i18n.js";
import { loadIcons } from "./icons.js";

function render() {
  const map = {
    home: sectionHome,
    history: sectionHistory,
    drive: function () {
      return sectionGroup(t("section.drive.title", "Condução"), "", "drive");
    },
    climate: function () {
      return sectionGroup(t("section.climate.title", "Clima"), "", "climate");
    },
    energy: function () {
      return sectionGroup(t("section.energy.title", "Energia"), "", "energy");
    },
    safety: function () {
      return sectionGroup(t("section.safety.title", "Segurança"), "", "safety");
    },
    cabin: sectionCabin,
    cameras: sectionCameras,
    dvr: sectionCameras,
    store: sectionStore,
    shortcuts: sectionShortcuts,
    plugins: sectionPlugins,
    android: sectionAndroid,
    system: sectionSystem,
    lab: sectionLab,
    about: sectionAbout,
  };
  const main = $("main");
  const prevMainScroll = main ? main.scrollTop : 0;
  const probeScrollEl = document.getElementById("probeScroll");
  const prevProbeScroll = probeScrollEl ? probeScrollEl.scrollTop : 0;
  // Abort in-flight MJPEG before destroying the <img>, or requests pile up.
  const prevPreview = $("preview");
  if (prevPreview) {
    prevPreview.removeAttribute("src");
    prevPreview.dataset.ocaLive = "";
  }
  main.innerHTML = (map[state.section] || sectionHome)();
  bindSection(refresh);
  if (state.section === "shortcuts") bindShortcuts(refresh);
  if (state.section === "plugins") bindPlugins(refresh);
  if (state.section === "lab") fillProbeTable();
  if (state.section === "store" && !state.storeDetail && !state._storeLoaded) {
    state._storeLoaded = true;
    api("/api/store/search?q=")
      .then(function (res) {
        state.storeResults = (res && res.apps) || [];
        render();
      })
      .catch(function () {});
  }
  if (state.section === "cameras" || state.section === "dvr") {
    startCameraLive();
  } else if (state.cameraPreviewActive) {
    stopCameraLive();
  }
  if (main) main.scrollTop = prevMainScroll;
  const probeAfter = document.getElementById("probeScroll");
  if (probeAfter) probeAfter.scrollTop = prevProbeScroll;
  renderSetupOverlay();
}

async function refresh() {
  await loadIcons();
  await loadI18n();
  state.status = await api("/api/status");
  state.setup = state.status.setup || (await api("/api/setup"));
  state.entities = await api("/api/entities");
  state.controls = await api("/api/controls");
  try {
    const hist = await api("/api/history");
    state.historyEntities = (hist && hist.entities) || [];
  } catch (e) {
    state.historyEntities = [];
  }
  if (state.section === "history" && state.historySelected) {
    await loadHistoryPoints();
  }
  if (state.section === "cabin" || !state._soundsLoaded) {
    state._soundsLoaded = true;
    await loadSounds();
  }
  if (state.section === "cameras" || state.section === "dvr") {
    await loadRecordings();
  }
  try {
    const hidden = await api("/api/entities/hidden");
    state.hiddenEntities = (hidden && hidden.entities) || [];
  } catch (e) {
    state.hiddenEntities = [];
  }
  try {
    state.adb = (state.status && state.status.adb) || (await api("/api/adb"));
  } catch (e) {
    state.adb = null;
  }
  try {
    state.lab = await api("/api/lab");
    if (state.lab && state.lab.token) state.token = state.lab.token;
    else if (state.lab && !state.lab.contributor) state.token = "";
  } catch (e) {
    state.lab = null;
  }
  try {
    const hint = await api("/debug/adb-hint");
    if (!state.token && hint.contributor && hint.tokenHint) state.token = hint.tokenHint;
  } catch (e) {}
  try {
    const prefs = await api("/api/prefs");
    if (prefs.theme) setTheme(prefs.theme);
  } catch (e) {}
  if (state.section === "shortcuts" || state.section === "system" || !state._shortcutsLoaded) {
    state._shortcutsLoaded = true;
    await loadShortcuts();
  }
  if (state.showSetup === false && shouldShowSetup(state.setup)) {
    // only auto-open once per load when incomplete
  }
  if (state._setupInit == null) {
    state._setupInit = true;
    state.showSetup = shouldShowSetup(state.setup);
  }
  renderStatusBar();
  render();
}

document.querySelectorAll(".nav-item").forEach(function (el) {
  el.onclick = function () {
    goSection(el.getAttribute("data-sec"));
  };
});

function goSection(sec) {
  if (!sec) return;
  const prev = state.section;
  document.querySelectorAll(".nav-item").forEach(function (n) {
    n.classList.remove("active");
    if (n.getAttribute("data-sec") === sec) n.classList.add("active");
  });
  state.section = sec;
  if (
    (prev === "cameras" || prev === "dvr") &&
    sec !== "cameras" &&
    sec !== "dvr"
  ) {
    stopCameraLive();
  }
  if (state.section === "store") state._storeLoaded = false;
  if (state.section === "shortcuts" || state.section === "system") {
    loadShortcuts().then(function () {
      render();
    });
    return;
  }
  if (state.section === "history") {
    if (!state.historySelected && state.historyEntities && state.historyEntities.length) {
      state.historySelected = state.historyEntities[0];
    }
    state.historyView = null;
    loadHistoryPoints().then(function () {
      render();
    });
    return;
  }
  if (state.section === "cameras" || state.section === "dvr") {
    state.cameraPreviewActive = false;
    loadRecordings().then(function () {
      render();
    });
    return;
  }
  if (state.section === "cabin") {
    loadSounds().then(function () {
      render();
    });
    return;
  }
  render();
  if (state.section === "lab") {
    api("/api/lab")
      .then(function (lab) {
        state.lab = lab;
        if (lab && lab.token) state.token = lab.token;
        const tab = state.labTab || "vhal";
        if (tab === "obd2") {
          return api("/debug/obd2?token=" + encodeURIComponent(state.token));
        }
        if (tab === "entities") return null;
        return api("/debug/probe?token=" + encodeURIComponent(state.token));
      })
      .then(function (p) {
        if (!p) {
          render();
          return;
        }
        if (state.labTab === "obd2") state.obd2 = p;
        else state.probe = p;
        render();
      })
      .catch(function () {});
  }
}

window.__ocaGoSection = goSection;

(function applySectionQuery() {
  try {
    var params = new URLSearchParams(window.location.search || "");
    var sec = params.get("section");
    if (sec) goSection(sec);
  } catch (e) {}
})();

refresh();
setInterval(function () {
  softRefresh();
}, 3000);
document.addEventListener("visibilitychange", function () {
  if (document.visibilityState === "visible") softRefresh();
});

async function softRefresh() {
  try {
    const s = await api("/api/status");
    state.status = s;
    if (s.setup) state.setup = s.setup;
    try {
      state.adb = (s && s.adb) || state.adb;
    } catch (e) {}
    const pair = await Promise.all([api("/api/entities"), api("/api/controls")]);
    state.entities = pair[0];
    state.controls = pair[1];
    renderStatusBar();
    // Don't remount main while a dropdown is open or a shortcut/plugin is being edited —
    // soft refresh was closing choice menus ~every 3s and wiping the editor form.
    if (document.querySelector(".choice-select.open")) return;
    if (state.shortcutEdit) return;
    if (state.pluginEditId) return;
    const ae = document.activeElement;
    if (
      ae &&
      (ae.matches("input, textarea, select") || ae.closest(".choice-select, .choice-menu"))
    ) {
      return;
    }
    // Lab: keep scroll + filter; update table in place for product-entities tab only.
    if (state.section === "lab") {
      if (state.labTab === "entities") fillProbeTable();
      return;
    }
    // History / Cabin / Cameras: never remount on soft refresh (MJPEG pile-up / form wipe).
    if (
      state.section === "history" ||
      state.section === "cabin" ||
      state.section === "cameras" ||
      state.section === "dvr"
    ) {
      return;
    }
    render();
  } catch (e) {}
}
