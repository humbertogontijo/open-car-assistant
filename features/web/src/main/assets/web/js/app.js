import { api, $ } from "./api.js";
import { state, patch, notify, subscribe } from "./store.js";
import { render as litRender } from "./lit.js";
import { pageView } from "./pages/index.js";
import {
  loadHistoryPoints,
  loadEnergyDash,
  loadRecordings,
  loadSounds,
  startCameraLive,
  stopCameraLive,
  applyCameraPlayerSrc,
  ensureStoreLoaded,
} from "./pages/index.js";
import { loadShortcuts } from "./pages/shortcuts.js";
import { shouldShowSetup, renderSetupOverlay } from "./ui/setup.js";
import { setTheme } from "./theme.js";
import { loadI18n } from "./i18n.js";
import { loadIcons, mountNavIcons } from "./icons.js";
import { stashCurrentScroll, restorePageScroll, rememberScroll } from "./nav.js";
import { connectEvents } from "./events.js";
import {
  applyLegacyPageQuery,
  setRouteEnterHandler,
  startRouter,
  gotoPage,
  router,
} from "./router.js";

function updateNavActive(sec) {
  document.querySelectorAll(".nav-item").forEach(function (n) {
    n.classList.toggle("active", n.getAttribute("data-page") === sec);
  });
}

/** Hide nav items that require capabilities (e.g. Energia needs CHARGING|HYBRID_ENERGY). */
function applyCapabilityNav() {
  const caps = state.capabilities || [];
  const has = function (name) {
    return caps.indexOf(name) >= 0;
  };
  document.querySelectorAll(".nav-item[data-cap]").forEach(function (el) {
    const needed = (el.getAttribute("data-cap") || "").split(",");
    const ok = needed.some(function (c) {
      return c && has(c.trim());
    });
    el.style.display = ok ? "" : "none";
    if (!ok && state.page === el.getAttribute("data-page")) {
      goPage("home", { replace: true });
    }
  });
}

function paint() {
  const main = $("main");
  if (!main) return;
  const probeScrollEl = document.getElementById("probeScroll");
  const prevProbeScroll = probeScrollEl ? probeScrollEl.scrollTop : 0;

  var outlet = router.outlet();
  litRender(outlet != null ? outlet : pageView(state.page), main);
  renderSetupOverlay();
  mountNavIcons();
  updateNavActive(state.page);

  if (state.page === "store") ensureStoreLoaded();

  if (state.page === "cameras" || state.page === "dvr") {
    if (state.cameraPlayerMode !== "dvr") {
      Promise.resolve(startCameraLive()).then(function () {
        return applyCameraPlayerSrc();
      });
    }
  } else if (state.cameraPreviewActive || state.cameraPlayerMode === "dvr") {
    stopCameraLive();
  }

  restorePageScroll(state.page);
  const probeAfter = document.getElementById("probeScroll");
  if (probeAfter) probeAfter.scrollTop = prevProbeScroll;
}

// Lit signals only: store.subscribe bumps version → re-run paint.
subscribe(function () {
  try {
    paint();
  } catch (e) {}
});

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
    if (prefs.homeLat != null) updatesPrefs.homeLat = prefs.homeLat;
    if (prefs.homeLon != null) updatesPrefs.homeLon = prefs.homeLon;
    if (prefs.homeRadiusM != null) updatesPrefs.homeRadiusM = prefs.homeRadiusM;
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
    capabilities: (status && status.capabilities) || state.capabilities || [],
  };
  if (state._setupInit == null) {
    updates._setupInit = true;
    updates.showSetup = shouldShowSetup(setup);
  }
  Object.assign(state, updates);
  applyCapabilityNav();

  if (state.page === "history") {
    if (!state.historySelected && state.historyEntities && state.historyEntities.length) {
      patch({ historySelected: state.historyEntities[0] });
    }
    if (state.historySelected) {
      await loadHistoryPoints();
    }
  }
  if (state.page === "energy") {
    await loadEnergyDash();
  }
  if (state.page === "sound" || !state._soundsLoaded) {
    state._soundsLoaded = true;
    await loadSounds();
  }
  if (state.page === "cameras" || state.page === "dvr") {
    await loadRecordings();
  }
  if (state.page === "shortcuts" || state.page === "settings" || state.page === "system" || !state._shortcutsLoaded) {
    state._shortcutsLoaded = true;
    await loadShortcuts();
  }
  notify();
}

function onPageEnter(page, prev) {
  if (prev && prev !== page) {
    stashCurrentScroll();
    if (
      (prev === "cameras" || prev === "dvr") &&
      page !== "cameras" &&
      page !== "dvr"
    ) {
      stopCameraLive();
    }
  }

  updateNavActive(page);

  const updates = {
    page: page,
    openChoiceId: null,
    choiceSearchQuery: "",
    showHiddenGroup: null,
  };
  if (page === "store") updates._storeLoaded = false;
  patch(updates);

  if (page === "shortcuts" || page === "settings" || page === "system") {
    loadShortcuts().then(function () {
      notify();
    });
  } else if (page === "history") {
    if (!state.historySelected && state.historyEntities && state.historyEntities.length) {
      patch({ historySelected: state.historyEntities[0], historyView: null });
    } else {
      patch({ historyView: null });
    }
    loadHistoryPoints().then(function () {
      notify();
    });
  } else if (page === "energy") {
    loadEnergyDash().then(function () {
      notify();
    });
  } else if (page === "cameras" || page === "dvr") {
    patch({
      cameraPreviewActive: false,
      cameraPreviewSrc: "",
      cameraPlayerMode: "live",
      cameraPlayingName: "",
      cameraPlayingKind: "",
      cameraPlaybackPaused: false,
      cameraPlaybackLoading: false,
    });
    loadRecordings().then(function () {
      notify();
    });
  } else if (page === "sound") {
    loadSounds().then(function () {
      notify();
    });
  } else if (page === "lab") {
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

function goPage(page, options) {
  if (!page) return;
  return gotoPage(page, options);
}

window.__ocaGoPage = goPage;

applyLegacyPageQuery();
setRouteEnterHandler(onPageEnter);
startRouter(function () {
  notify();
});

/**
 * Bootstrap once over HTTP, then live updates via `/api/events` WebSocket
 * (see events.js). No softRefresh interval — that stacked with camera live
 * preview and pegged the SoC.
 */
refresh().then(function () {
  connectEvents();
});

// Persist in-session scroll while scrolling.
document.addEventListener(
  "scroll",
  function (ev) {
    if (ev.target && ev.target.id === "main") {
      rememberScroll(state.page, ev.target.scrollTop);
    }
  },
  true,
);
