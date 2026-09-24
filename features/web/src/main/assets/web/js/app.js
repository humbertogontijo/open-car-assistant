import { api, $ } from "./api.js";
import { state, patch, subscribe, notify } from "./store.js";
import { render as litRender } from "./lit.js";
import { sectionView } from "./sections/index.js";
import {
  loadHistoryPoints,
  loadRecordings,
  loadSounds,
  startCameraLive,
  stopCameraLive,
  applyCameraPlayerSrc,
  ensureStoreLoaded,
} from "./sections/index.js";
import { loadShortcuts } from "./sections/shortcuts.js";
import { shouldShowSetup, renderSetupOverlay } from "./ui/setup.js";
import { setTheme } from "./theme.js";
import { loadI18n } from "./i18n.js";
import { loadIcons, mountNavIcons } from "./icons.js";
import { stashCurrentScroll, restoreSectionScroll, rememberScroll } from "./nav.js";

function updateNavActive(sec) {
  document.querySelectorAll(".nav-item").forEach(function (n) {
    n.classList.toggle("active", n.getAttribute("data-sec") === sec);
  });
}

function shouldSkipSoftRender() {
  if (document.querySelector(".choice-select.open") || state.openChoiceId) return true;
  if (state.shortcutEdit) return true;
  if (state.pluginEditId) return true;
  const ae = document.activeElement;
  if (
    ae &&
    (ae.matches("input, textarea, select") || ae.closest(".choice-select, .choice-menu"))
  ) {
    return true;
  }
  return false;
}

function paint() {
  const main = $("main");
  if (!main) return;
  const probeScrollEl = document.getElementById("probeScroll");
  const prevProbeScroll = probeScrollEl ? probeScrollEl.scrollTop : 0;

  litRender(sectionView(state.section), main);
  renderSetupOverlay();
  mountNavIcons();
  updateNavActive(state.section);

  if (state.section === "store") ensureStoreLoaded();

  if (state.section === "cameras" || state.section === "dvr") {
    if (state.cameraPlayerMode !== "recording") {
      Promise.resolve(startCameraLive()).then(function () {
        return applyCameraPlayerSrc();
      });
    }
  } else if (state.cameraPreviewActive || state.cameraPlayerMode === "recording") {
    stopCameraLive();
  }

  restoreSectionScroll(state.section);
  const probeAfter = document.getElementById("probeScroll");
  if (probeAfter) probeAfter.scrollTop = prevProbeScroll;
}

subscribe(paint);

export async function refresh() {
  await loadIcons();
  await loadI18n();
  const status = await api("/api/status");
  const setup = status.setup || (await api("/api/setup"));
  const entities = await api("/api/entities");
  const controls = await api("/api/controls");
  let historyEntities = [];
  try {
    const hist = await api("/api/history");
    historyEntities = (hist && hist.entities) || [];
  } catch (e) {}
  let hiddenEntities = [];
  try {
    const hidden = await api("/api/entities/hidden");
    hiddenEntities = (hidden && hidden.entities) || [];
  } catch (e) {}
  let adb = null;
  try {
    adb = (status && status.adb) || (await api("/api/adb"));
  } catch (e) {}
  let lab = null;
  let token = state.token;
  try {
    lab = await api("/api/lab");
    if (lab && lab.token) token = lab.token;
    else if (lab && !lab.contributor) token = "";
  } catch (e) {}
  try {
    const hint = await api("/debug/adb-hint");
    if (!token && hint.contributor && hint.tokenHint) token = hint.tokenHint;
  } catch (e) {}
  let updatesPrefs = {
    units: {
      temperature: "celsius",
      distance: "km",
      speed: "km_h",
      fuel_economy: "l_100km",
      energy_economy: "kwh_100km",
    },
  };
  try {
    const prefs = await api("/api/prefs");
    if (prefs.theme) setTheme(prefs.theme);
    let units = prefs.units;
    if (typeof units === "string") {
      try {
        units = JSON.parse(units);
      } catch (e) {
        units = units === "imperial"
          ? {
              temperature: "fahrenheit",
              distance: "mi",
              speed: "mph",
              fuel_economy: "mpg",
              energy_economy: "kwh_100km",
            }
          : updatesPrefs.units;
      }
    }
    try {
      const local = localStorage.getItem("oca_units");
      if (local) {
        if (local.charAt(0) === "{") units = JSON.parse(local);
        else if (local === "imperial" || local === "metric") {
          units = local === "imperial"
            ? {
                temperature: "fahrenheit",
                distance: "mi",
                speed: "mph",
                fuel_economy: "mpg",
                energy_economy: "kwh_100km",
              }
            : updatesPrefs.units;
        }
      }
    } catch (e) {}
    if (units && typeof units === "object") {
      updatesPrefs = { units: units };
    }
  } catch (e) {}

  const updates = {
    status: status,
    setup: setup,
    entities: entities,
    controls: controls,
    historyEntities: historyEntities,
    hiddenEntities: hiddenEntities,
    adb: adb,
    lab: lab,
    token: token,
    prefs: updatesPrefs,
  };
  if (state._setupInit == null) {
    updates._setupInit = true;
    updates.showSetup = shouldShowSetup(setup);
  }
  Object.assign(state, updates);

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
  if (state.section === "shortcuts" || state.section === "settings" || state.section === "system" || !state._shortcutsLoaded) {
    state._shortcutsLoaded = true;
    await loadShortcuts();
  }
  notify();
}

function goSection(sec) {
  if (!sec) return;
  const prev = state.section;
  stashCurrentScroll();
  updateNavActive(sec);

  if (
    (prev === "cameras" || prev === "dvr") &&
    sec !== "cameras" &&
    sec !== "dvr"
  ) {
    stopCameraLive();
  }

  const updates = { section: sec, openChoiceId: null };
  if (sec === "store") updates._storeLoaded = false;
  patch(updates);

  if (sec === "shortcuts" || sec === "settings" || sec === "system") {
    loadShortcuts().then(function () {
      notify();
    });
  } else if (sec === "history") {
    if (!state.historySelected && state.historyEntities && state.historyEntities.length) {
      state.historySelected = state.historyEntities[0];
    }
    state.historyView = null;
    loadHistoryPoints().then(function () {
      notify();
    });
  } else if (sec === "cameras" || sec === "dvr") {
    patch({
      cameraPreviewActive: false,
      cameraPreviewSrc: "",
      cameraPlayerMode: "live",
      cameraPlayingName: "",
      cameraPlaybackPaused: false,
      cameraPlaybackLoading: false,
    });
    loadRecordings().then(function () {
      notify();
    });
  } else if (sec === "cabin") {
    loadSounds().then(function () {
      notify();
    });
  } else if (sec === "lab") {
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
        if (p) {
          if (state.labTab === "obd2") state.obd2 = p;
          else state.probe = p;
        }
        notify();
      })
      .catch(function () {
        notify();
      });
  }
}

window.__ocaGoSection = goSection;

document.querySelectorAll(".nav-item").forEach(function (el) {
  el.onclick = function () {
    goSection(el.getAttribute("data-sec"));
  };
});

(function applySectionQuery() {
  try {
    var params = new URLSearchParams(window.location.search || "");
    var sec = params.get("section");
    if (sec) {
      state.section = sec;
      updateNavActive(sec);
    }
  } catch (e) {}
})();

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
    if (shouldSkipSoftRender()) return;
    // Remember scroll before notify so paint can restore within-section position.
    const main = $("main");
    if (main) rememberScroll(state.section, main.scrollTop);
    notify();
  } catch (e) {}
}

refresh();
setInterval(softRefresh, 3000);
document.addEventListener("visibilitychange", function () {
  if (document.visibilityState === "visible") softRefresh();
});

// Persist in-session scroll while scrolling.
document.addEventListener(
  "scroll",
  function (ev) {
    if (ev.target && ev.target.id === "main") {
      rememberScroll(state.section, ev.target.scrollTop);
    }
  },
  true,
);
