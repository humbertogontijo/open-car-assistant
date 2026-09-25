/**
 * Reactive app store: mutate via patch() / notify() so subscribers re-render.
 */
export const state = {
  status: null,
  controls: [],
  entities: [],
  setup: null,
  probe: null,
  lab: null,
  section: "home",
  token: "",
  showSetup: false,
  i18n: { locale: "pt-BR", locales: ["pt-BR", "en"], strings: {}, valueMaps: {} },
  /** UI prefs from /api/prefs (theme mirrored in DOM; units map here). */
  prefs: {
    units: {
      temperature: "celsius",
      distance: "km",
      speed: "km_h",
      fuel_economy: "l_100km",
      energy_economy: "kwh_100km",
    },
  },
  adb: null,
  adbMessage: null,
  storeQuery: "",
  storeResults: [],
  storeDetail: null,
  storeBusy: false,
  storeMessage: null,
  historyEntities: [],
  historyPoints: null,
  historySelected: null,
  historyRangeHours: 24,
  historyView: null,
  recordings: [],
  dvrTimeline: { segments: [], recording: false },
  /** Local calendar day for the DVR scrubber: "YYYY-MM-DD". null = today. */
  dvrTimelineDay: null,
  cameraTimelineAtMs: 0,
  cameraPreviewActive: false,
  /** Stable HLS URL while preview is active — avoid resetting video src on every render. */
  cameraPreviewSrc: "",
  cameraPreviewError: "",
  /** Shared player: "live" HLS or "dvr" clip playback. */
  cameraPlayerMode: "live",
  cameraPlayingName: "",
  cameraPlayingKind: "",
  cameraPlaybackPaused: false,
  /** Playback rate for live + DVR <video> (0.5–2). */
  cameraPlaybackRate: 1,
  cameraPlaybackLoading: false,
  cameraPlaybackDurationMs: 0,
  sounds: null,
  hiddenEntities: [],
  /** When set to a group id, that page shows only its hidden cards. */
  showHiddenGroup: null,
  labTab: "vhal",
  obd2: null,
  probeFilter: "",
  /** Open choice-select control id (lit-managed). */
  openChoiceId: null,
  shortcutEdit: null,
  pluginEditId: null,
  setupMsg: "",
  apkMessage: null,
};

const listeners = new Set();
let raf = 0;

export function subscribe(fn) {
  listeners.add(fn);
  return function () {
    listeners.delete(fn);
  };
}

/** Apply a shallow merge and schedule subscriber notification (rAF-coalesced). */
export function patch(partial) {
  if (partial) Object.assign(state, partial);
  scheduleNotify();
}

/** Notify after in-place mutations (e.g. nested object fields). */
export function notify() {
  scheduleNotify();
}

function scheduleNotify() {
  if (raf) return;
  raf = requestAnimationFrame(function () {
    raf = 0;
    listeners.forEach(function (fn) {
      try {
        fn();
      } catch (e) {}
    });
  });
}

export function entitiesByGroup(group) {
  return state.entities.filter(function (e) {
    return e.group === group;
  });
}

export function hiddenEntitiesByGroup(group) {
  return (state.hiddenEntities || []).filter(function (e) {
    return e.group === group;
  });
}

export function isShowingHidden(group) {
  return (
    state.showHiddenGroup === group && hiddenEntitiesByGroup(group).length > 0
  );
}

export function entitiesByType(type) {
  return state.entities.filter(function (e) {
    return e.entity === type;
  });
}

export function groupByEntity(list) {
  const map = {};
  list.forEach(function (e) {
    const k = e.entity || "extra";
    if (!map[k]) map[k] = [];
    map[k].push(e);
  });
  return map;
}

export function findControl(id) {
  return (state.controls || []).concat(state.entities || []).find(function (c) {
    return c.id === id;
  });
}
