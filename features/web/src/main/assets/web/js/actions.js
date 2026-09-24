import { api } from "./api.js";
import { state, patch, notify, findControl } from "./store.js";
import { setTheme } from "./theme.js";
import { setLocale, t } from "./i18n.js";
import { presetUnits, unitPrefs } from "./units.js";

async function saveUnitPrefs(unitsObj) {
  const json = JSON.stringify(unitsObj);
  try {
    await api("/api/prefs", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: "units=" + encodeURIComponent(json),
    });
  } catch (e) {}
  try {
    localStorage.setItem("oca_units", json);
  } catch (e) {}
  patch({ prefs: Object.assign({}, state.prefs || {}, { units: unitsObj }) });
}

/** Soft reload of live entity/control values after a write. */
export async function reloadEntities() {
  try {
    const pair = await Promise.all([api("/api/entities"), api("/api/controls")]);
    patch({ entities: pair[0], controls: pair[1] });
  } catch (e) {
    notify();
  }
}

export async function setControl(id, val) {
  await api("/api/controls/" + encodeURIComponent(id), {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "value=" + encodeURIComponent(val),
  });
  await reloadEntities();
}

export async function setPersist(id, opts) {
  const body = new URLSearchParams();
  if (opts.enabled != null) body.set("enabled", opts.enabled ? "1" : "0");
  if (opts.value != null) body.set("value", String(opts.value));
  await api("/api/controls/" + encodeURIComponent(id) + "/persist", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
  await reloadEntities();
}

export async function hideEntity(id) {
  await api("/api/entities/" + encodeURIComponent(id) + "/visibility", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "hidden=1",
  });
  await reloadEntities();
}

export async function unhideEntity(id) {
  await api("/api/entities/" + encodeURIComponent(id) + "/visibility", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "hidden=0",
  });
  try {
    const res = await api("/api/entities/hidden");
    patch({ hiddenEntities: (res && res.entities) || [] });
  } catch (e) {
    notify();
  }
}

/**
 * Pref handlers previously in bindSection.
 * @param {string} pref
 * @param {string} next
 * @param {object} [extra]
 */
export async function runPref(pref, next, extra) {
  extra = extra || {};
  if (pref === "theme") {
    setTheme(next);
    notify();
    return;
  }
  if (pref === "locale") {
    await setLocale(next);
    notify();
    return;
  }
  if (pref === "units") {
    // Legacy single toggle → preset
    const nextPrefs = next === "imperial" ? presetUnits("imperial") : presetUnits("metric");
    await saveUnitPrefs(nextPrefs);
    return;
  }
  if (pref === "units_preset") {
    if (next !== "metric" && next !== "imperial") return;
    await saveUnitPrefs(presetUnits(next));
    return;
  }
  if (pref.indexOf("unit_") === 0) {
    const dim = pref.slice("unit_".length);
    const cur = Object.assign({}, unitPrefs());
    cur[dim] = next;
    await saveUnitPrefs(cur);
    return;
  }
  if (pref === "adb") {
    if (next === "0" && !confirm(t("system.adb.warn", "Disable wireless ADB?"))) return;
    const res = await api("/api/adb", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: next === "1" ? "enabled=1&port=5566" : "enabled=0",
    });
    if (res && res.ok === false) {
      alert(res.message || t("system.adb.failed", "Wireless ADB toggle failed"));
    }
    try {
      const adb = await api("/api/adb");
      patch({ adb: adb, adbMessage: null });
    } catch (e) {
      notify();
    }
    return;
  }
  if (pref === "ha-enabled" || pref === "sc-enabled") {
    // Editor-only toggles: caller updates draft; just re-render.
    notify();
    return;
  }
  if (pref === "lab-tab") {
    const updates = { labTab: next };
    if (next === "obd2" && !state.obd2) {
      try {
        updates.obd2 = await api(
          "/debug/obd2?token=" + encodeURIComponent(state.token),
        );
      } catch (e) {
        updates.obd2 = { results: [], summary: { available: false } };
      }
    }
    patch(updates);
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
    const { loadRecordings, stopCameraLive } = await import("./sections/cameras.js");
    await loadRecordings();
    await stopCameraLive();
    return;
  }
  if (pref === "hist-range") {
    patch({ historyRangeHours: parseInt(next, 10) || 24 });
    const { loadHistoryPoints } = await import("./sections/history.js");
    await loadHistoryPoints();
    notify();
    return;
  }
  if (pref === "hist-view") {
    patch({ historyView: next });
    return;
  }
  if (pref === "cam-rec") {
    const storage =
      (state.status && state.status.dvr && state.status.dvr.storageId) || "";
    await api("/api/dvr/toggle?storage=" + encodeURIComponent(storage || ""), {
      method: "POST",
    });
    try {
      const s = await api("/api/status");
      patch({ status: s });
    } catch (e) {
      notify();
    }
    return;
  }
  if (pref === "sc-overlay") {
    await api("/api/shortcuts/overlay", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: next === "1" }),
    });
    const { loadShortcuts } = await import("./sections/shortcuts.js");
    await loadShortcuts();
    notify();
    return;
  }
  if (pref === "sc-slot") {
    const slot = parseInt(extra.slot, 10);
    await api("/api/shortcuts/slots", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        slot: isNaN(slot) ? 0 : slot,
        shortcutId: next || null,
      }),
    });
    const { loadShortcuts } = await import("./sections/shortcuts.js");
    await loadShortcuts();
    notify();
    return;
  }
}
