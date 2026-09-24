import { api, $ } from "./api.js";
import { state } from "./state.js";
import { prefCard, segmentToggleHtml, choiceSelectHtml, boolToggleHtml } from "./cards.js";
import { t } from "./i18n.js";
function esc(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function ensureShortcutsState() {
  if (!state.shortcuts) state.shortcuts = [];
  if (!state.shortcutSlots) state.shortcutSlots = {};
  if (!state.shortcutOverlay) state.shortcutOverlay = {};
  if (!state.shortcutApps) state.shortcutApps = [];
  if (!state.shortcutWheelKeys) {
    state.shortcutWheelKeys = [
      "custom",
      "mute",
      "top",
      "left",
      "right",
      "bottom",
      "vr",
      "menu",
      "confirm",
    ];
  }
  if (state.shortcutEdit == null) state.shortcutEdit = null;
  if (state.shortcutMessage == null) state.shortcutMessage = null;
}

export async function loadShortcuts() {
  ensureShortcutsState();
  try {
    const res = await api("/api/shortcuts");
    state.shortcuts = (res && res.shortcuts) || [];
    state.shortcutSlots = (res && res.slots) || {};
    state.shortcutOverlay = (res && res.overlay) || {};
    state.shortcutWheelKeys = (res && res.wheelKeys) || state.shortcutWheelKeys;
  } catch (e) {
    state.shortcutMessage = String(e && e.message ? e.message : e);
  }
  try {
    const apps = await api("/api/apps");
    state.shortcutApps = (apps && apps.apps) || [];
  } catch (e) {
    state.shortcutApps = [];
  }
}

function pluginEntries() {
  return (state.status && state.status.plugins) || [];
}

function configuredPlugins() {
  return pluginEntries().filter(function (p) {
    const id = p.id || (p.status && p.status.id);
    return id && pluginConfigured(id);
  });
}

function pluginConfigured(pluginId) {
  const plugins = pluginEntries();
  for (var i = 0; i < plugins.length; i++) {
    const p = plugins[i];
    const id = p.id || (p.status && p.status.id);
    if (id !== pluginId) continue;
    const st = p.status || {};
    const cfg = p.config || {};
    if (st.enabled === false || cfg.enabled === false) return false;
    if (cfg.configured === true || st.configured === true) return true;
    if ((cfg.tokenSet || st.hasToken) && (cfg.baseUrl || st.baseUrl)) return true;
    return !!(st.enabled || cfg.enabled);
  }
  return false;
}

function pluginById(pluginId) {
  const plugins = pluginEntries();
  for (var i = 0; i < plugins.length; i++) {
    const p = plugins[i];
    const id = p.id || (p.status && p.status.id);
    if (id === pluginId) return p;
  }
  return null;
}

function pluginDisplayName(pluginId) {
  const p = pluginById(pluginId);
  if (!p) return pluginId || "?";
  const st = p.status || {};
  return p.displayName || st.displayName || pluginId;
}

function pluginActionNames(pluginId) {
  const p = pluginById(pluginId);
  const st = (p && p.status) || {};
  return st.actions || [];
}

function pluginTriggerNames(pluginId) {
  const p = pluginById(pluginId);
  const st = (p && p.status) || {};
  return st.triggers || [];
}

function pluginParamKeys(pluginId, kind, name) {
  const p = pluginById(pluginId);
  if (!p) return [];
  const st = p.status || p;
  const map = kind === "action" ? st.actionParams : st.triggerParams;
  if (map && map[name]) return map[name];
  return [];
}

/** Editor keeps API-shaped plugin actions/triggers. */
function normalizeTrigger(tr) {
  if (!tr || !tr.type) return tr;
  if (tr.type === "plugin") {
    return {
      type: "plugin",
      pluginId: tr.pluginId || "",
      trigger: tr.trigger || "",
      params: Object.assign({}, tr.params || {}),
    };
  }
  return tr;
}

function normalizeAction(a) {
  if (!a || !a.type) return a;
  if (a.type === "plugin") {
    var params = Object.assign({}, a.params || {});
    if (params.data != null && typeof params.data !== "string") {
      try {
        params = Object.assign({}, params, { data: JSON.stringify(params.data) });
      } catch (e) {
        /* keep */
      }
    }
    return {
      type: "plugin",
      pluginId: a.pluginId || "",
      action: a.action || "",
      params: params,
    };
  }
  return a;
}

function triggerLabel(tr) {
  tr = normalizeTrigger(tr);
  if (!tr || !tr.type) return "";
  if (tr.type === "boot") return t("shortcuts.trigger.boot", "Boot");
  if (tr.type === "screen") {
    const on = tr.on !== false;
    return (
      t("shortcuts.trigger.screen", "Screen") +
      ": " +
      (on
        ? t("shortcuts.trigger.screen.on", "On")
        : t("shortcuts.trigger.screen.off", "Off"))
    );
  }
  if (tr.type === "gear") {
    const g = tr.gear;
    const name =
      g === 4 ? "P" : g === 2 ? "R" : g === 1 ? "N" : g === 8 ? "D" : g != null ? String(g) : "?";
    return t("shortcuts.trigger.gear", "Gear") + ": " + name;
  }
  if (tr.type === "wheel_key") {
    const keyLabel = wheelKeyLabel(tr.key || "");
    return (
      t("shortcuts.trigger.wheel", "Wheel key") +
      ": " +
      keyLabel +
      (tr.longPress ? " (" + t("shortcuts.trigger.long_press", "long press") + ")" : "")
    );
  }
  if (tr.type === "wifi_ssid") {
    return (
      t("shortcuts.trigger.wifi", "Wi‑Fi SSID") +
      (tr.ssid ? ": " + tr.ssid : "")
    );
  }
  if (tr.type === "entity_state") {
    return (
      t("shortcuts.trigger.entity_state", "Entity") +
      ": " +
      (tr.entityId || "?") +
      (tr.value != null && tr.value !== "" ? "=" + tr.value : "")
    );
  }
  if (tr.type === "plugin") {
    const p = tr.params || {};
    const keys = Object.keys(p).filter(function (k) {
      return p[k];
    });
    const summary = keys
      .slice(0, 2)
      .map(function (k) {
        return p[k];
      })
      .join(" ");
    return (
      pluginDisplayName(tr.pluginId) +
      " " +
      (tr.trigger || "?") +
      (summary ? ": " + summary : "")
    );
  }
  return tr.type;
}

function actionLabel(a) {
  a = normalizeAction(a);
  if (!a || !a.type) return "";
  if (a.type === "set_control") return (a.entityId || "?") + "=" + (a.value || "");
  if (a.type === "launch_app") return a.packageName || "?";
  if (a.type === "delay_ms") return (a.ms || 0) + "ms";
  if (a.type === "plugin") {
    const p = a.params || {};
    return (
      pluginDisplayName(a.pluginId) +
      " " +
      (a.action || "?") +
      (p.domain || p.service ? " " + (p.domain || "") + "." + (p.service || "") : "")
    );
  }
  return a.type;
}

function triggerTypeOptions() {
  const opts = [
    { value: "boot", label: t("shortcuts.trigger.boot", "Boot") },
    { value: "screen", label: t("shortcuts.trigger.screen", "Screen") },
    { value: "gear", label: t("shortcuts.trigger.gear", "Gear") },
    { value: "wheel_key", label: t("shortcuts.trigger.wheel", "Wheel key") },
    { value: "wifi_ssid", label: t("shortcuts.trigger.wifi", "Wi‑Fi SSID") },
    { value: "entity_state", label: t("shortcuts.trigger.entity_state", "Entity") },
  ];
  if (configuredPlugins().length) {
    opts.push({ value: "plugin", label: t("shortcuts.trigger.plugin", "Plugin") });
  }
  return opts;
}

function screenStateOptions() {
  return [
    { value: "on", label: t("shortcuts.trigger.screen.on", "On") },
    { value: "off", label: t("shortcuts.trigger.screen.off", "Off") },
  ];
}

function wheelKeyLabel(k) {
  const map = {
    custom: t("wheel.key.custom", "Star / custom"),
    mute: t("wheel.key.mute", "Mute"),
    top: t("wheel.key.top", "D-pad up"),
    left: t("wheel.key.left", "D-pad left"),
    right: t("wheel.key.right", "D-pad right"),
    bottom: t("wheel.key.bottom", "D-pad down"),
    vr: t("wheel.key.vr", "Voice"),
    menu: t("wheel.key.menu", "Menu"),
    confirm: t("wheel.key.confirm", "OK / confirm"),
  };
  return map[k] || k;
}

function wheelKeyOptions() {
  return (state.shortcutWheelKeys || []).map(function (k) {
    return { value: k, label: wheelKeyLabel(k) };
  });
}

/** AAOS GEAR_SELECTION values commonly used on Flyme HUs. */
function gearOptions() {
  return [
    { value: "4", label: t("opt.gear.4", "P") },
    { value: "2", label: t("opt.gear.2", "R") },
    { value: "1", label: t("opt.gear.1", "N") },
    { value: "8", label: t("opt.gear.8", "D") },
  ];
}

function actionTypeOptions() {
  const opts = [
    { value: "set_control", label: t("shortcuts.action.set_control", "Set control") },
    { value: "launch_app", label: t("shortcuts.action.launch_app", "Launch app") },
    { value: "delay_ms", label: t("shortcuts.action.delay", "Delay") },
  ];
  if (configuredPlugins().length) {
    opts.push({ value: "plugin", label: t("shortcuts.action.plugin", "Plugin") });
  }
  return opts;
}

function pluginSelectOptions() {
  return configuredPlugins().map(function (p) {
    const id = p.id || (p.status && p.status.id);
    return { value: id, label: pluginDisplayName(id) };
  });
}

function pluginContributionOptions(pluginId, kind) {
  const names = kind === "action" ? pluginActionNames(pluginId) : pluginTriggerNames(pluginId);
  return names.map(function (n) {
    return { value: n, label: n };
  });
}

function pluginParamsHtml(pluginId, kind, name, params, indexAttr, index) {
  const keys = pluginParamKeys(pluginId, kind, name);
  const useKeys = keys.length ? keys : Object.keys(params || {});
  if (!pluginId || !name) {
    return (
      '<p class="persist-note">' +
      t("shortcuts.plugin.pick", "Choose a plugin and action") +
      "</p>"
    );
  }
  if (!useKeys.length) {
    return (
      '<input class="field sc-plugin-param" data-param-key="_raw" style="width:100%;margin-top:8px" value="" placeholder="params" ' +
      indexAttr +
      '="' +
      index +
      '">'
    );
  }
  return useKeys
    .map(function (key) {
      const val = params && params[key] != null ? String(params[key]) : "";
      return (
        '<input class="field sc-plugin-param" data-param-key="' +
        esc(key) +
        '" style="width:100%;margin-top:8px" value="' +
        esc(val) +
        '" placeholder="' +
        esc(key) +
        '" ' +
        indexAttr +
        '="' +
        index +
        '">'
      );
    })
    .join("");
}

function editorHtml(edit) {
  const controls = (state.controls || []).filter(function (c) {
    return c.writable !== false;
  });
  const apps = state.shortcutApps || [];
  const actions = edit.actions || [];
  const triggers = (edit.triggers || []).map(normalizeTrigger);

  const actionRows = actions
    .map(function (raw, i) {
      const a = normalizeAction(raw);
      var mid = "";
      if (a.type === "set_control") {
        mid =
          choiceSelectHtml(
            controls.map(function (c) {
              return { value: c.id, label: c.label || c.id };
            }),
            a.entityId || "",
            'data-sc="entity" data-ai="' + i + '"',
          ) +
          '<input class="field sc-value" style="width:88px;margin-top:8px" value="' +
          esc(a.value || "") +
          '" placeholder="value" data-ai="' +
          i +
          '">';
      } else if (a.type === "launch_app") {
        mid = choiceSelectHtml(
          apps.map(function (ap) {
            return { value: ap.packageName, label: ap.label || ap.packageName };
          }),
          a.packageName || "",
          'data-sc="pkg" data-ai="' + i + '"',
        );
      } else if (a.type === "plugin") {
        const pluginOpts = pluginSelectOptions();
        const pluginId = a.pluginId || (pluginOpts[0] && pluginOpts[0].value) || "";
        const actionOpts = pluginContributionOptions(pluginId, "action");
        const actionName = a.action || (actionOpts[0] && actionOpts[0].value) || "";
        mid =
          '<div style="margin-top:8px">' +
          choiceSelectHtml(pluginOpts, pluginId, 'data-sc="plugin-id" data-ai="' + i + '"') +
          "</div>" +
          (pluginId
            ? '<div style="margin-top:8px">' +
              choiceSelectHtml(
                actionOpts,
                actionName,
                'data-sc="plugin-action" data-ai="' + i + '"',
              ) +
              "</div>"
            : "") +
          pluginParamsHtml(pluginId, "action", actionName, a.params || {}, "data-ai", i);
      } else {
        mid =
          '<input class="field sc-ms" type="number" min="0" max="5000" style="width:100%;margin-top:8px" value="' +
          esc(a.ms != null ? a.ms : 500) +
          '" data-ai="' +
          i +
          '">';
      }
      return (
        '<div class="shortcut-action" data-ai="' +
        i +
        '" style="margin:10px 0;width:100%">' +
        segmentToggleHtml(actionTypeOptions(), a.type || "delay_ms", 'data-sc="atype" data-ai="' + i + '"') +
        '<div style="margin-top:8px">' +
        mid +
        '</div><button class="btn ghost sc-adel" type="button" data-ai="' +
        i +
        '" style="margin-top:8px">×</button></div>'
      );
    })
    .join("");

  const triggerRows = triggers
    .map(function (raw, i) {
      const tr = normalizeTrigger(raw);
      var extra = "";
      if (tr.type === "wheel_key") {
        extra =
          '<div style="margin-top:8px">' +
          choiceSelectHtml(
            wheelKeyOptions(),
            tr.key || "custom",
            'data-sc="wkey" data-ti="' + i + '"',
          ) +
          '</div><label style="display:flex;align-items:center;gap:8px;margin-top:8px">' +
          '<input type="checkbox" class="sc-longpress" data-ti="' +
          i +
          '"' +
          (tr.longPress ? " checked" : "") +
          "> " +
          t("shortcuts.trigger.long_press", "Long press") +
          "</label>";
      } else if (tr.type === "wifi_ssid") {
        extra =
          '<div style="margin-top:8px"><input class="field sc-wifi-ssid" data-ti="' +
          i +
          '" placeholder="' +
          esc(t("shortcuts.trigger.wifi.hint", "SSID (blank = any)")) +
          '" value="' +
          esc(tr.ssid || "") +
          '" style="width:100%"></div>';
      } else if (tr.type === "entity_state") {
        extra =
          '<div style="margin-top:8px">' +
          choiceSelectHtml(
            controls.map(function (c) {
              return { value: c.id, label: c.label || c.id };
            }),
            tr.entityId || "",
            'data-sc="entity-trig" data-ti="' + i + '"',
          ) +
          '</div><div style="margin-top:8px"><input class="field sc-entity-val" data-ti="' +
          i +
          '" placeholder="' +
          esc(t("shortcuts.trigger.entity_value", "Value (optional)")) +
          '" value="' +
          esc(tr.value || "") +
          '" style="width:100%"></div>';
      } else if (tr.type === "gear") {
        const gearVal = tr.gear != null ? String(tr.gear) : "4";
        extra =
          '<div style="margin-top:8px">' +
          choiceSelectHtml(gearOptions(), gearVal, 'data-sc="gear" data-ti="' + i + '"') +
          "</div>";
      } else if (tr.type === "screen") {
        const stateVal = tr.on === false ? "off" : "on";
        extra =
          '<div style="margin-top:8px">' +
          choiceSelectHtml(screenStateOptions(), stateVal, 'data-sc="screen" data-ti="' + i + '"') +
          "</div>";
      } else if (tr.type === "plugin") {
        const pluginOpts = pluginSelectOptions();
        const pluginId = tr.pluginId || (pluginOpts[0] && pluginOpts[0].value) || "";
        const triggerOpts = pluginContributionOptions(pluginId, "trigger");
        const triggerName = tr.trigger || (triggerOpts[0] && triggerOpts[0].value) || "";
        extra =
          '<div style="margin-top:8px">' +
          choiceSelectHtml(pluginOpts, pluginId, 'data-sc="plugin-id" data-ti="' + i + '"') +
          "</div>" +
          (pluginId
            ? '<div style="margin-top:8px">' +
              choiceSelectHtml(
                triggerOpts,
                triggerName,
                'data-sc="plugin-trigger" data-ti="' + i + '"',
              ) +
              "</div>"
            : "") +
          pluginParamsHtml(pluginId, "trigger", triggerName, tr.params || {}, "data-ti", i);
      }
      return (
        '<div class="shortcut-trigger" data-ti="' +
        i +
        '" style="margin:10px 0;width:100%">' +
        segmentToggleHtml(
          triggerTypeOptions(),
          tr.type || "boot",
          'data-sc="ttype" data-ti="' + i + '"',
        ) +
        extra +
        '<button class="btn ghost sc-tdel" type="button" data-ti="' +
        i +
        '" style="margin-top:8px">×</button></div>'
      );
    })
    .join("");

  return prefCard({
    icon: "drive",
    title: edit.id
      ? t("shortcuts.edit", "Edit shortcut")
      : t("shortcuts.new", "New shortcut"),
    bodyHtml:
      '<label class="hint">' +
      t("shortcuts.name", "Name") +
      '</label><input class="field" id="scName" value="' +
      esc(edit.name || "") +
      '" style="width:100%;margin:4px 0 12px">' +
      '<div style="margin-bottom:12px">' +
      boolToggleHtml(edit.enabled !== false, 'data-pref="sc-enabled"') +
      "</div>" +
      "<h3 style=\"margin:0 0 8px;font-size:1rem\">" +
      t("shortcuts.actions", "Actions") +
      '</h3><div id="scActions">' +
      actionRows +
      '</div><button class="btn" id="scAddAction" type="button" style="margin-top:4px">' +
      t("shortcuts.add_action", "Add action") +
      "</button>" +
      "<h3 style=\"margin:16px 0 8px;font-size:1rem\">" +
      t("shortcuts.triggers", "Triggers") +
      '</h3><div id="scTriggers">' +
      triggerRows +
      '</div><button class="btn" id="scAddTrigger" type="button" style="margin-top:4px">' +
      t("shortcuts.add_trigger", "Add trigger") +
      '</button><div class="row" style="gap:8px;margin-top:16px;width:100%">' +
      '<button class="btn primary" id="scSave" type="button" style="flex:1">' +
      t("shortcuts.save", "Save") +
      '</button><button class="btn ghost" id="scCancel" type="button" style="flex:1">' +
      t("shortcuts.cancel", "Cancel") +
      "</button></div>",
  });
}

function slotsGridHtml() {
  const list = state.shortcuts || [];
  const slots = state.shortcutSlots || {};
  const opts = [{ value: "", label: "—" }].concat(
    list.map(function (s) {
      return { value: s.id, label: s.name || s.id };
    }),
  );
  const cards = [];
  for (var i = 0; i < 8; i++) {
    cards.push(
      prefCard({
        icon: "drive",
        title: t("shortcuts.slot", "Slot") + " " + (i + 1),
        bodyHtml: choiceSelectHtml(
          opts,
          slots[String(i)] || "",
          'data-pref="sc-slot" data-slot="' + i + '"',
        ),
      }),
    );
  }
  return (
    "<h2 class=\"section-label\">" +
    t("shortcuts.slots.title", "Pin slots") +
    '</h2><div class="grid">' +
    cards.join("") +
    "</div>"
  );
}

/** HU entry control (status-bar icon / float chip) — shown on System. */
export function quickEntryCardHtml() {
  const overlay = state.shortcutOverlay || {};
  const on = overlay.overlayEnabled !== false;
  const style = overlay.style || "float_chip";
  const canDraw = overlay.canDrawOverlays !== false;
  const opts = [
    { value: "1", label: t("shortcuts.topbar.show", "Show") },
    { value: "0", label: t("shortcuts.topbar.hide", "Hide") },
  ];
  const grantHtml =
    style === "float_chip" && !canDraw
      ? '<button class="btn" id="scOverlayGrant" type="button" style="margin-top:10px;width:100%">' +
        t("shortcuts.overlay.grant", "Grant overlay permission") +
        "</button>"
      : "";
  return prefCard({
    icon: "system",
    title: t("shortcuts.topbar.title", "Quick entry"),
    bodyHtml: segmentToggleHtml(opts, on ? "1" : "0", 'data-pref="sc-overlay"') + grantHtml,
  });
}

export function sectionShortcuts() {
  ensureShortcutsState();
  const list = state.shortcuts || [];
  const edit = state.shortcutEdit;
  const editingId = edit && edit.id ? edit.id : null;
  const isNew = !!(edit && !edit.id);

  function shortcutCard(s) {
    const trig = (s.triggers || []).map(triggerLabel).join(", ");
    const acts = (s.actions || []).map(actionLabel).join(" → ");
    return prefCard({
      icon: s.icon || "drive",
      title: s.name || s.id,
      sub:
        (s.enabled === false ? t("value.off", "Off") + " · " : "") +
        (trig || t("shortcuts.no_triggers", "No auto triggers")),
      bodyHtml:
        '<p class="mono hint" style="margin:0 0 10px">' +
        esc(acts || "—") +
        '</p><div class="row" style="gap:8px;width:100%">' +
        '<button class="btn sc-run" type="button" data-id="' +
        esc(s.id) +
        '" style="flex:1">' +
        t("shortcuts.run", "Run") +
        '</button><button class="btn sc-edit" type="button" data-id="' +
        esc(s.id) +
        '" style="flex:1">' +
        t("shortcuts.edit", "Edit") +
        '</button><button class="btn ghost sc-del" type="button" data-id="' +
        esc(s.id) +
        '">' +
        t("shortcuts.delete", "Delete") +
        "</button></div>",
    });
  }

  const listCards = list
    .map(function (s) {
      if (editingId && s.id === editingId) {
        return '<div id="scEditorWrap">' + editorHtml(edit) + "</div>";
      }
      return shortcutCard(s);
    })
    .join("");

  const newEditor = isNew
    ? '<div id="scEditorWrap">' + editorHtml(edit) + "</div>"
    : "";

  let listHtml;
  if (list.length || isNew) {
    listHtml = listCards + newEditor;
  } else {
    listHtml = prefCard({
      icon: "drive",
      title: t("shortcuts.empty", "No shortcuts yet"),
      sub: t("shortcuts.empty.hint", "Create a shortcut, then assign it to a pin slot."),
      bodyHtml: "",
    });
  }

  return (
    '<div class="section-head"><h1>' +
    t("section.shortcuts.title", "Shortcuts") +
    '</h1><button class="btn primary" id="scNew" type="button">' +
    t("shortcuts.new", "New shortcut") +
    "</button></div>" +
    (state.shortcutMessage
      ? '<pre class="mono">' + esc(state.shortcutMessage) + "</pre>"
      : "") +
    '<div class="grid" style="margin-top:12px">' +
    listHtml +
    "</div>" +
    slotsGridHtml()
  );
}

function activeChoiceVal(row, sc) {
  const active =
    row.querySelector('.toggle-seg.active[data-sc="' + sc + '"]') ||
    row.querySelector('.choice-opt.active[data-sc="' + sc + '"]');
  if (active) return active.getAttribute("data-val") || "";
  const any = row.querySelector('[data-sc="' + sc + '"][data-val]');
  return any ? any.getAttribute("data-val") || "" : "";
}

function readEditorFromDom(base) {
  const name = (($("scName") && $("scName").value) || "").trim() || "Shortcut";
  const cycle = document.querySelector('[data-pref="sc-enabled"].toggle-seg.active');
  const enabled = cycle ? cycle.getAttribute("data-val") === "1" : true;
  const actions = [];
  document.querySelectorAll(".shortcut-action").forEach(function (row) {
    const type = activeChoiceVal(row, "atype") || "delay_ms";
    if (type === "set_control") {
      actions.push({
        type: "set_control",
        entityId: activeChoiceVal(row, "entity"),
        value: (row.querySelector(".sc-value") || {}).value || "",
      });
    } else if (type === "launch_app") {
      actions.push({
        type: "launch_app",
        packageName: activeChoiceVal(row, "pkg"),
      });
    } else if (type === "plugin") {
      const params = {};
      row.querySelectorAll(".sc-plugin-param").forEach(function (inp) {
        const key = inp.getAttribute("data-param-key");
        if (!key || key === "_raw") return;
        const val = (inp.value || "").trim();
        if (val) params[key] = val;
      });
      actions.push({
        type: "plugin",
        pluginId: activeChoiceVal(row, "plugin-id") || "",
        action: activeChoiceVal(row, "plugin-action") || "",
        params: params,
      });
    } else {
      const ms = parseInt((row.querySelector(".sc-ms") || {}).value || "0", 10);
      actions.push({ type: "delay_ms", ms: isNaN(ms) ? 0 : ms });
    }
  });
  const triggers = [];
  document.querySelectorAll(".shortcut-trigger").forEach(function (row) {
    const type = activeChoiceVal(row, "ttype") || "boot";
    if (type === "wheel_key") {
      const longEl = row.querySelector(".sc-longpress");
      triggers.push({
        type: "wheel_key",
        key: activeChoiceVal(row, "wkey") || "custom",
        longPress: !!(longEl && longEl.checked),
      });
    } else if (type === "wifi_ssid") {
      const ssid = ((row.querySelector(".sc-wifi-ssid") || {}).value || "").trim();
      triggers.push({ type: "wifi_ssid", ssid: ssid || null });
    } else if (type === "entity_state") {
      triggers.push({
        type: "entity_state",
        entityId: activeChoiceVal(row, "entity-trig") || "",
        value: ((row.querySelector(".sc-entity-val") || {}).value || "").trim() || null,
      });
    } else if (type === "gear") {
      const g = parseInt(activeChoiceVal(row, "gear") || "4", 10);
      triggers.push({ type: "gear", gear: isNaN(g) ? 4 : g });
    } else if (type === "screen") {
      triggers.push({
        type: "screen",
        on: (activeChoiceVal(row, "screen") || "on") !== "off",
      });
    } else if (type === "plugin") {
      const params = {};
      row.querySelectorAll(".sc-plugin-param").forEach(function (inp) {
        const key = inp.getAttribute("data-param-key");
        if (!key || key === "_raw") return;
        const val = (inp.value || "").trim();
        if (val) params[key] = val;
      });
      triggers.push({
        type: "plugin",
        pluginId: activeChoiceVal(row, "plugin-id") || "",
        trigger: activeChoiceVal(row, "plugin-trigger") || "",
        params: params,
      });
    } else {
      triggers.push({ type: type });
    }
  });
  return {
    id: base.id || undefined,
    name: name,
    icon: base.icon || "drive",
    enabled: enabled,
    actions: actions.slice(0, 10),
    triggers: triggers,
    conditions: base.conditions || [],
  };
}

/** Normalize editor state to API payload (parse optional JSON `data`). */
function toApiShortcut(body) {
  const actions = (body.actions || []).map(function (a) {
    a = normalizeAction(a);
    if (a.type !== "plugin") return a;
    const params = Object.assign({}, a.params || {});
    if (typeof params.data === "string" && params.data.trim()) {
      try {
        params.data = JSON.parse(params.data);
      } catch (e) {
        /* keep string */
      }
    }
    return {
      type: "plugin",
      pluginId: a.pluginId,
      action: a.action,
      params: params,
    };
  });
  const triggers = (body.triggers || []).map(function (tr) {
    tr = normalizeTrigger(tr);
    if (tr.type !== "plugin") return tr;
    return {
      type: "plugin",
      pluginId: tr.pluginId,
      trigger: tr.trigger,
      params: Object.assign({}, tr.params || {}),
    };
  });
  return Object.assign({}, body, { actions: actions, triggers: triggers });
}

export function bindShortcuts(refresh) {
  ensureShortcutsState();
  // Ghost-clicks after closing a choice menu can hit action/trigger toggles underneath.
  var ignoreTypeClicksUntil = 0;

  function syncEditFromDom() {
    state.shortcutEdit = readEditorFromDom(state.shortcutEdit || {});
    return state.shortcutEdit;
  }

  if ($("scNew")) {
    $("scNew").onclick = async function () {
      state.shortcutEdit = {
        name: "",
        enabled: true,
        actions: [{ type: "delay_ms", ms: 0 }],
        triggers: [],
      };
      await refresh();
      const name = $("scName");
      const wrap = $("scEditorWrap");
      if (wrap) wrap.scrollIntoView({ behavior: "smooth", block: "start" });
      if (name) name.focus();
    };
  }
  if ($("scCancel")) {
    $("scCancel").onclick = function () {
      state.shortcutEdit = null;
      refresh();
    };
  }
  if ($("scAddAction")) {
    $("scAddAction").onclick = function () {
      const edit = syncEditFromDom();
      edit.actions = (edit.actions || []).concat([{ type: "delay_ms", ms: 500 }]);
      state.shortcutEdit = edit;
      refresh();
    };
  }
  if ($("scAddTrigger")) {
    $("scAddTrigger").onclick = function () {
      const edit = syncEditFromDom();
      edit.triggers = (edit.triggers || []).concat([{ type: "boot" }]);
      state.shortcutEdit = edit;
      refresh();
    };
  }
  document.querySelectorAll(".sc-adel").forEach(function (el) {
    el.onclick = function () {
      const i = parseInt(el.getAttribute("data-ai"), 10);
      const edit = syncEditFromDom();
      edit.actions.splice(i, 1);
      state.shortcutEdit = edit;
      refresh();
    };
  });
  document.querySelectorAll(".sc-tdel").forEach(function (el) {
    el.onclick = function () {
      const i = parseInt(el.getAttribute("data-ti"), 10);
      const edit = syncEditFromDom();
      edit.triggers.splice(i, 1);
      state.shortcutEdit = edit;
      refresh();
    };
  });

  // Rebuild editor only when action/trigger *type* actually changes.
  document.querySelectorAll('[data-sc="atype"], [data-sc="ttype"]').forEach(function (el) {
    el.onclick = function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      if (Date.now() < ignoreTypeClicksUntil) return;
      const next = el.getAttribute("data-val");
      if (next == null) return;
      const edit = syncEditFromDom();
      if (el.getAttribute("data-sc") === "atype") {
        const i = parseInt(el.getAttribute("data-ai"), 10);
        if (isNaN(i) || !edit.actions[i]) return;
        if (edit.actions[i].type === next) {
          // Same type (often a ghost-click after picking an app) — keep fields.
          return;
        }
        const blank = { type: next };
        if (next === "delay_ms") blank.ms = 500;
        else if (next === "set_control") {
          blank.entityId = "";
          blank.value = "";
        } else if (next === "launch_app") {
          blank.packageName = "";
        } else if (next === "plugin") {
          const plugins = pluginSelectOptions();
          const pid = plugins[0] && plugins[0].value;
          const acts = pid ? pluginContributionOptions(pid, "action") : [];
          blank.pluginId = pid || "";
          blank.action = (acts[0] && acts[0].value) || "";
          blank.params = {};
        }
        edit.actions[i] = blank;
      } else {
        const i = parseInt(el.getAttribute("data-ti"), 10);
        if (isNaN(i) || !edit.triggers[i]) return;
        if (edit.triggers[i].type === next) return;
        if (next === "wheel_key") {
          edit.triggers[i] = { type: "wheel_key", key: "custom", longPress: false };
        } else if (next === "wifi_ssid") {
          edit.triggers[i] = { type: "wifi_ssid", ssid: "" };
        } else if (next === "entity_state") {
          edit.triggers[i] = { type: "entity_state", entityId: "", value: "" };
        } else if (next === "gear") {
          edit.triggers[i] = { type: "gear", gear: 4 };
        } else if (next === "screen") {
          edit.triggers[i] = { type: "screen", on: true };
        } else if (next === "plugin") {
          const plugins = pluginSelectOptions();
          const pid = plugins[0] && plugins[0].value;
          const trs = pid ? pluginContributionOptions(pid, "trigger") : [];
          edit.triggers[i] = {
            type: "plugin",
            pluginId: pid || "",
            trigger: (trs[0] && trs[0].value) || "",
            params: {},
          };
        } else {
          edit.triggers[i] = { type: next };
        }
      }
      state.shortcutEdit = edit;
      refresh();
    };
  });

  document
    .querySelectorAll(
      '[data-sc="entity"], [data-sc="entity-trig"], [data-sc="pkg"], [data-sc="wkey"], [data-sc="gear"], [data-sc="screen"], [data-sc="plugin-id"], [data-sc="plugin-action"], [data-sc="plugin-trigger"]',
    )
    .forEach(function (el) {
    el.onclick = function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      const next = el.getAttribute("data-val");
      if (next == null) return;
      const sc = el.getAttribute("data-sc");
      const ai = parseInt(el.getAttribute("data-ai"), 10);
      const ti = parseInt(el.getAttribute("data-ti"), 10);
      const edit = syncEditFromDom();
      if (sc === "pkg" && edit.actions[ai]) {
        edit.actions[ai].type = "launch_app";
        edit.actions[ai].packageName = next;
      } else if (sc === "entity" && edit.actions[ai]) {
        edit.actions[ai].type = "set_control";
        edit.actions[ai].entityId = next;
        if (edit.actions[ai].value == null) edit.actions[ai].value = "";
      } else if (sc === "entity-trig" && edit.triggers[ti]) {
        edit.triggers[ti].type = "entity_state";
        edit.triggers[ti].entityId = next;
      } else if (sc === "plugin-id" && !isNaN(ai) && edit.actions[ai]) {
        edit.actions[ai].type = "plugin";
        edit.actions[ai].pluginId = next;
        const acts = pluginContributionOptions(next, "action");
        edit.actions[ai].action = (acts[0] && acts[0].value) || "";
        edit.actions[ai].params = {};
      } else if (sc === "plugin-action" && !isNaN(ai) && edit.actions[ai]) {
        edit.actions[ai].type = "plugin";
        edit.actions[ai].action = next;
        edit.actions[ai].params = edit.actions[ai].params || {};
      } else if (sc === "plugin-id" && !isNaN(ti) && edit.triggers[ti]) {
        edit.triggers[ti].type = "plugin";
        edit.triggers[ti].pluginId = next;
        const trs = pluginContributionOptions(next, "trigger");
        edit.triggers[ti].trigger = (trs[0] && trs[0].value) || "";
        edit.triggers[ti].params = {};
      } else if (sc === "plugin-trigger" && !isNaN(ti) && edit.triggers[ti]) {
        edit.triggers[ti].type = "plugin";
        edit.triggers[ti].trigger = next;
        edit.triggers[ti].params = edit.triggers[ti].params || {};
      } else if (sc === "wkey" && edit.triggers[ti]) {
        edit.triggers[ti].type = "wheel_key";
        edit.triggers[ti].key = next;
      } else if (sc === "gear" && edit.triggers[ti]) {
        edit.triggers[ti].type = "gear";
        edit.triggers[ti].gear = parseInt(next, 10);
      } else if (sc === "screen" && edit.triggers[ti]) {
        edit.triggers[ti].type = "screen";
        edit.triggers[ti].on = next !== "off";
      }
      state.shortcutEdit = edit;
      ignoreTypeClicksUntil = Date.now() + 500;

      // plugin-id / contribution changes rebuild the param fields
      if (sc === "plugin-id" || sc === "plugin-action" || sc === "plugin-trigger") {
        refresh();
        return;
      }

      const root = el.closest("[data-choice-select]");
      if (root) {
        root.querySelectorAll(".choice-opt").forEach(function (opt) {
          const on = opt.getAttribute("data-val") === next;
          opt.classList.toggle("active", on);
          opt.setAttribute("aria-selected", on ? "true" : "false");
        });
        const label = root.querySelector(".choice-label");
        if (label) label.textContent = el.textContent;
        root.classList.remove("open");
        const trigger = root.querySelector("[data-choice-trigger]");
        if (trigger) trigger.setAttribute("aria-expanded", "false");
        const menu = root.querySelector(".choice-menu");
        if (menu) menu.setAttribute("hidden", "");
      }
    };
  });

  if ($("scName")) {
    $("scName").oninput = function () {
      if (!state.shortcutEdit) return;
      state.shortcutEdit.name = $("scName").value;
    };
  }

  if ($("scSave")) {
    $("scSave").onclick = async function () {
      const body = toApiShortcut(syncEditFromDom());
      state.shortcutMessage = null;
      try {
        const res = await api("/api/shortcuts", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        });
        if (res && res.ok === false) {
          state.shortcutMessage = res.error || "save failed";
        } else {
          state.shortcutEdit = null;
        }
        await loadShortcuts();
      } catch (e) {
        state.shortcutMessage = String(e && e.message ? e.message : e);
      }
      refresh();
    };
  }
  document.querySelectorAll(".sc-edit").forEach(function (el) {
    el.onclick = async function () {
      const id = el.getAttribute("data-id");
      const s = (state.shortcuts || []).find(function (x) {
        return x.id === id;
      });
      if (s) {
        state.shortcutEdit = JSON.parse(JSON.stringify(s));
        state.shortcutEdit.actions = (state.shortcutEdit.actions || []).map(normalizeAction);
        state.shortcutEdit.triggers = (state.shortcutEdit.triggers || []).map(normalizeTrigger);
        await refresh();
        const wrap = $("scEditorWrap");
        if (wrap) wrap.scrollIntoView({ behavior: "smooth", block: "start" });
        const name = $("scName");
        if (name) name.focus();
      }
    };
  });
  document.querySelectorAll(".sc-del").forEach(function (el) {
    el.onclick = async function () {
      const id = el.getAttribute("data-id");
      if (!confirm(t("shortcuts.delete_confirm", "Delete this shortcut?"))) return;
      await api("/api/shortcuts/" + encodeURIComponent(id), { method: "DELETE" });
      if (state.shortcutEdit && state.shortcutEdit.id === id) state.shortcutEdit = null;
      await loadShortcuts();
      refresh();
    };
  });
  document.querySelectorAll(".sc-run").forEach(function (el) {
    el.onclick = async function () {
      const id = el.getAttribute("data-id");
      try {
        const res = await api("/api/shortcuts/" + encodeURIComponent(id) + "/run", {
          method: "POST",
        });
        if (res && res.ok === false) {
          state.shortcutMessage = res.error || t("shortcuts.run_failed", "Run failed");
        } else {
          state.shortcutMessage = null;
        }
      } catch (e) {
        state.shortcutMessage = String(e && e.message ? e.message : e);
      }
      refresh();
    };
  });
}
