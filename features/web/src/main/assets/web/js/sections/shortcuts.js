import { html, nothing } from "../lit.js";
import { live } from "../lit.js";
import { repeat } from "../lit.js";
import { api } from "../api.js";
import { state, notify } from "../store.js";
import { prefCard, segmentToggle, choiceSelect, boolToggle, prefSegment } from "../ui/cards.js";
import { runPref } from "../actions.js";
import { t } from "../i18n.js";

var ignoreTypeClicksUntil = 0;

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
      } catch (e) {}
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

function blankActionForType(next) {
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
  return blank;
}

function blankTriggerForType(next) {
  if (next === "wheel_key") {
    return { type: "wheel_key", key: "custom", longPress: false };
  }
  if (next === "wifi_ssid") return { type: "wifi_ssid", ssid: "" };
  if (next === "entity_state") return { type: "entity_state", entityId: "", value: "" };
  if (next === "gear") return { type: "gear", gear: 4 };
  if (next === "screen") return { type: "screen", on: true };
  if (next === "plugin") {
    const plugins = pluginSelectOptions();
    const pid = plugins[0] && plugins[0].value;
    const trs = pid ? pluginContributionOptions(pid, "trigger") : [];
    return {
      type: "plugin",
      pluginId: pid || "",
      trigger: (trs[0] && trs[0].value) || "",
      params: {},
    };
  }
  return { type: next };
}

function pluginParamsFields(pluginId, kind, name, params, index, indexKind) {
  const keys = pluginParamKeys(pluginId, kind, name);
  const useKeys = keys.length ? keys : Object.keys(params || {});
  if (!pluginId || !name) {
    return html`<p class="persist-note">
      ${t("shortcuts.plugin.pick", "Choose a plugin and action")}
    </p>`;
  }
  if (!useKeys.length) {
    return html`
      <input
        class="field sc-plugin-param"
        data-param-key="_raw"
        style="width:100%;margin-top:8px"
        placeholder="params"
        .value=${live("")}
      />
    `;
  }
  return html`
    ${useKeys.map(function (key) {
      const val = params && params[key] != null ? String(params[key]) : "";
      return html`
        <input
          class="field sc-plugin-param"
          data-param-key=${key}
          style="width:100%;margin-top:8px"
          placeholder=${key}
          .value=${live(val)}
          @input=${function (ev) {
            const edit = state.shortcutEdit;
            if (!edit) return;
            const row =
              indexKind === "action"
                ? edit.actions[index]
                : edit.triggers[index];
            if (!row) return;
            if (!row.params) row.params = {};
            row.params[key] = ev.target.value;
          }}
        />
      `;
    })}
  `;
}

function syncPluginParamsFromDom(index, indexKind) {
  const edit = state.shortcutEdit;
  if (!edit) return;
  const row =
    indexKind === "action" ? edit.actions[index] : edit.triggers[index];
  if (!row || row.type !== "plugin") return;
  const sel =
    indexKind === "action"
      ? '.shortcut-action[data-ai="' + index + '"]'
      : '.shortcut-trigger[data-ti="' + index + '"]';
  const root = document.querySelector(sel);
  if (!root) return;
  const params = Object.assign({}, row.params || {});
  root.querySelectorAll(".sc-plugin-param").forEach(function (inp) {
    const key = inp.getAttribute("data-param-key");
    if (!key || key === "_raw") return;
    const val = (inp.value || "").trim();
    if (val) params[key] = val;
    else delete params[key];
  });
  row.params = params;
}

function actionRow(a, i, controls, apps) {
  a = normalizeAction(a);
  var mid = nothing;
  if (a.type === "set_control") {
    mid = html`
      ${choiceSelect({
        options: controls.map(function (c) {
          return { value: c.id, label: c.label || c.id };
        }),
        current: a.entityId || "",
        choiceKey: "sc-action-entity-" + i,
        onSelect: function (next) {
          const edit = state.shortcutEdit;
          if (!edit || !edit.actions[i]) return;
          edit.actions[i].type = "set_control";
          edit.actions[i].entityId = next;
          if (edit.actions[i].value == null) edit.actions[i].value = "";
          ignoreTypeClicksUntil = Date.now() + 500;
          notify();
        },
      })}
      <input
        class="field sc-value"
        style="width:88px;margin-top:8px"
        placeholder="value"
        .value=${live(a.value || "")}
        @input=${function (ev) {
          if (state.shortcutEdit && state.shortcutEdit.actions[i]) {
            state.shortcutEdit.actions[i].value = ev.target.value;
          }
        }}
      />
    `;
  } else if (a.type === "launch_app") {
    mid = choiceSelect({
      options: apps.map(function (ap) {
        return { value: ap.packageName, label: ap.label || ap.packageName };
      }),
      current: a.packageName || "",
      choiceKey: "sc-action-pkg-" + i,
      onSelect: function (next) {
        const edit = state.shortcutEdit;
        if (!edit || !edit.actions[i]) return;
        edit.actions[i].type = "launch_app";
        edit.actions[i].packageName = next;
        ignoreTypeClicksUntil = Date.now() + 500;
        notify();
      },
    });
  } else if (a.type === "plugin") {
    const pluginOpts = pluginSelectOptions();
    const pluginId = a.pluginId || (pluginOpts[0] && pluginOpts[0].value) || "";
    const actionOpts = pluginContributionOptions(pluginId, "action");
    const actionName = a.action || (actionOpts[0] && actionOpts[0].value) || "";
    mid = html`
      <div style="margin-top:8px">
        ${choiceSelect({
          options: pluginOpts,
          current: pluginId,
          choiceKey: "sc-action-plugin-" + i,
          onSelect: function (next) {
            const edit = state.shortcutEdit;
            if (!edit || !edit.actions[i]) return;
            syncPluginParamsFromDom(i, "action");
            edit.actions[i].type = "plugin";
            edit.actions[i].pluginId = next;
            const acts = pluginContributionOptions(next, "action");
            edit.actions[i].action = (acts[0] && acts[0].value) || "";
            edit.actions[i].params = {};
            ignoreTypeClicksUntil = Date.now() + 500;
            notify();
          },
        })}
      </div>
      ${pluginId
        ? html`<div style="margin-top:8px">
            ${choiceSelect({
              options: actionOpts,
              current: actionName,
              choiceKey: "sc-action-pa-" + i,
              onSelect: function (next) {
                const edit = state.shortcutEdit;
                if (!edit || !edit.actions[i]) return;
                syncPluginParamsFromDom(i, "action");
                edit.actions[i].type = "plugin";
                edit.actions[i].action = next;
                edit.actions[i].params = edit.actions[i].params || {};
                ignoreTypeClicksUntil = Date.now() + 500;
                notify();
              },
            })}
          </div>`
        : nothing}
      ${pluginParamsFields(pluginId, "action", actionName, a.params || {}, i, "action")}
    `;
  } else {
    mid = html`
      <input
        class="field sc-ms"
        type="number"
        min="0"
        max="5000"
        style="width:100%;margin-top:8px"
        .value=${live(a.ms != null ? String(a.ms) : "500")}
        @input=${function (ev) {
          if (state.shortcutEdit && state.shortcutEdit.actions[i]) {
            const ms = parseInt(ev.target.value || "0", 10);
            state.shortcutEdit.actions[i].ms = isNaN(ms) ? 0 : ms;
          }
        }}
      />
    `;
  }
  return html`
    <div class="shortcut-action" data-ai=${String(i)} style="margin:10px 0;width:100%">
      ${segmentToggle({
        options: actionTypeOptions(),
        current: a.type || "delay_ms",
        onSelect: function (next) {
          if (Date.now() < ignoreTypeClicksUntil) return;
          const edit = state.shortcutEdit;
          if (!edit || !edit.actions[i]) return;
          if (edit.actions[i].type === next) return;
          syncPluginParamsFromDom(i, "action");
          edit.actions[i] = blankActionForType(next);
          notify();
        },
      })}
      <div style="margin-top:8px">${mid}</div>
      <button
        class="btn ghost sc-adel"
        type="button"
        style="margin-top:8px"
        @click=${function () {
          const edit = state.shortcutEdit;
          if (!edit) return;
          syncPluginParamsFromDom(i, "action");
          edit.actions.splice(i, 1);
          notify();
        }}
      >
        ×
      </button>
    </div>
  `;
}

function triggerRow(tr, i, controls) {
  tr = normalizeTrigger(tr);
  var extra = nothing;
  if (tr.type === "wheel_key") {
    extra = html`
      <div style="margin-top:8px">
        ${choiceSelect({
          options: wheelKeyOptions(),
          current: tr.key || "custom",
          choiceKey: "sc-trig-wkey-" + i,
          onSelect: function (next) {
            const edit = state.shortcutEdit;
            if (!edit || !edit.triggers[i]) return;
            edit.triggers[i].type = "wheel_key";
            edit.triggers[i].key = next;
            ignoreTypeClicksUntil = Date.now() + 500;
            notify();
          },
        })}
      </div>
      <label style="display:flex;align-items:center;gap:8px;margin-top:8px">
        <input
          type="checkbox"
          class="sc-longpress"
          .checked=${live(!!tr.longPress)}
          @change=${function (ev) {
            if (state.shortcutEdit && state.shortcutEdit.triggers[i]) {
              state.shortcutEdit.triggers[i].longPress = ev.target.checked;
            }
          }}
        />
        ${t("shortcuts.trigger.long_press", "Long press")}
      </label>
    `;
  } else if (tr.type === "wifi_ssid") {
    extra = html`
      <div style="margin-top:8px">
        <input
          class="field sc-wifi-ssid"
          placeholder=${t("shortcuts.trigger.wifi.hint", "SSID (blank = any)")}
          style="width:100%"
          .value=${live(tr.ssid || "")}
          @input=${function (ev) {
            if (state.shortcutEdit && state.shortcutEdit.triggers[i]) {
              state.shortcutEdit.triggers[i].ssid = ev.target.value;
            }
          }}
        />
      </div>
    `;
  } else if (tr.type === "entity_state") {
    extra = html`
      <div style="margin-top:8px">
        ${choiceSelect({
          options: controls.map(function (c) {
            return { value: c.id, label: c.label || c.id };
          }),
          current: tr.entityId || "",
          choiceKey: "sc-trig-entity-" + i,
          onSelect: function (next) {
              const edit = state.shortcutEdit;
              if (!edit || !edit.triggers[i]) return;
              edit.triggers[i].type = "entity_state";
              edit.triggers[i].entityId = next;
              ignoreTypeClicksUntil = Date.now() + 500;
              notify();
            },
          },
        )}
      </div>
      <div style="margin-top:8px">
        <input
          class="field sc-entity-val"
          placeholder=${t("shortcuts.trigger.entity_value", "Value (optional)")}
          style="width:100%"
          .value=${live(tr.value || "")}
          @input=${function (ev) {
            if (state.shortcutEdit && state.shortcutEdit.triggers[i]) {
              state.shortcutEdit.triggers[i].value = ev.target.value;
            }
          }}
        />
      </div>
    `;
  } else if (tr.type === "gear") {
    const gearVal = tr.gear != null ? String(tr.gear) : "4";
    extra = html`
      <div style="margin-top:8px">
        ${choiceSelect({
          options: gearOptions(),
          current: gearVal,
          choiceKey: "sc-trig-gear-" + i,
          onSelect: function (next) {
            const edit = state.shortcutEdit;
            if (!edit || !edit.triggers[i]) return;
            edit.triggers[i].type = "gear";
            edit.triggers[i].gear = parseInt(next, 10);
            ignoreTypeClicksUntil = Date.now() + 500;
            notify();
          },
        })}
      </div>
    `;
  } else if (tr.type === "screen") {
    const stateVal = tr.on === false ? "off" : "on";
    extra = html`
      <div style="margin-top:8px">
        ${choiceSelect({
          options: screenStateOptions(),
          current: stateVal,
          choiceKey: "sc-trig-screen-" + i,
          onSelect: function (next) {
            const edit = state.shortcutEdit;
            if (!edit || !edit.triggers[i]) return;
            edit.triggers[i].type = "screen";
            edit.triggers[i].on = next !== "off";
            ignoreTypeClicksUntil = Date.now() + 500;
            notify();
          },
        })}
      </div>
    `;
  } else if (tr.type === "plugin") {
    const pluginOpts = pluginSelectOptions();
    const pluginId = tr.pluginId || (pluginOpts[0] && pluginOpts[0].value) || "";
    const triggerOpts = pluginContributionOptions(pluginId, "trigger");
    const triggerName = tr.trigger || (triggerOpts[0] && triggerOpts[0].value) || "";
    extra = html`
      <div style="margin-top:8px">
        ${choiceSelect({
          options: pluginOpts,
          current: pluginId,
          choiceKey: "sc-trig-plugin-" + i,
          onSelect: function (next) {
            const edit = state.shortcutEdit;
            if (!edit || !edit.triggers[i]) return;
            syncPluginParamsFromDom(i, "trigger");
            edit.triggers[i].type = "plugin";
            edit.triggers[i].pluginId = next;
            const trs = pluginContributionOptions(next, "trigger");
            edit.triggers[i].trigger = (trs[0] && trs[0].value) || "";
            edit.triggers[i].params = {};
            ignoreTypeClicksUntil = Date.now() + 500;
            notify();
          },
        })}
      </div>
      ${pluginId
        ? html`<div style="margin-top:8px">
            ${choiceSelect({
              options: triggerOpts,
              current: triggerName,
              choiceKey: "sc-trig-pt-" + i,
              onSelect: function (next) {
                const edit = state.shortcutEdit;
                if (!edit || !edit.triggers[i]) return;
                syncPluginParamsFromDom(i, "trigger");
                edit.triggers[i].type = "plugin";
                edit.triggers[i].trigger = next;
                edit.triggers[i].params = edit.triggers[i].params || {};
                ignoreTypeClicksUntil = Date.now() + 500;
                notify();
              },
            })}
          </div>`
        : nothing}
      ${pluginParamsFields(pluginId, "trigger", triggerName, tr.params || {}, i, "trigger")}
    `;
  }
  return html`
    <div class="shortcut-trigger" data-ti=${String(i)} style="margin:10px 0;width:100%">
      ${segmentToggle({
        options: triggerTypeOptions(),
        current: tr.type || "boot",
        onSelect: function (next) {
          if (Date.now() < ignoreTypeClicksUntil) return;
          const edit = state.shortcutEdit;
          if (!edit || !edit.triggers[i]) return;
          if (edit.triggers[i].type === next) return;
          syncPluginParamsFromDom(i, "trigger");
          edit.triggers[i] = blankTriggerForType(next);
          notify();
        },
      })}
      ${extra}
      <button
        class="btn ghost sc-tdel"
        type="button"
        style="margin-top:8px"
        @click=${function () {
          const edit = state.shortcutEdit;
          if (!edit) return;
          syncPluginParamsFromDom(i, "trigger");
          edit.triggers.splice(i, 1);
          notify();
        }}
      >
        ×
      </button>
    </div>
  `;
}

function editorCard(edit) {
  const controls = (state.controls || []).filter(function (c) {
    return c.writable !== false;
  });
  const apps = state.shortcutApps || [];
  const actions = edit.actions || [];
  const triggers = (edit.triggers || []).map(normalizeTrigger);

  return prefCard({
    icon: "drive",
    title: edit.id
      ? t("shortcuts.edit", "Edit shortcut")
      : t("shortcuts.new", "New shortcut"),
    body: html`
      <label class="hint">${t("shortcuts.name", "Name")}</label>
      <input
        class="field"
        id="scName"
        style="width:100%;margin:4px 0 12px"
        .value=${live(edit.name || "")}
        @input=${function (ev) {
          if (state.shortcutEdit) state.shortcutEdit.name = ev.target.value;
        }}
      />
      <div style="margin-bottom:12px">
        ${boolToggle(edit.enabled !== false, function (val) {
          if (state.shortcutEdit) state.shortcutEdit.enabled = val === "1";
          notify();
        })}
      </div>
      <h3 style="margin:0 0 8px;font-size:1rem">${t("shortcuts.actions", "Actions")}</h3>
      <div id="scActions">
        ${repeat(
          actions,
          function (_, idx) {
            return "a-" + idx;
          },
          function (raw, i) {
            return actionRow(raw, i, controls, apps);
          },
        )}
      </div>
      <button
        class="btn"
        id="scAddAction"
        type="button"
        style="margin-top:4px"
        @click=${function () {
          const edit = state.shortcutEdit;
          if (!edit) return;
          edit.actions = (edit.actions || []).concat([{ type: "delay_ms", ms: 500 }]);
          notify();
        }}
      >
        ${t("shortcuts.add_action", "Add action")}
      </button>
      <h3 style="margin:16px 0 8px;font-size:1rem">${t("shortcuts.triggers", "Triggers")}</h3>
      <div id="scTriggers">
        ${repeat(
          triggers,
          function (_, idx) {
            return "t-" + idx;
          },
          function (raw, i) {
            return triggerRow(raw, i, controls);
          },
        )}
      </div>
      <button
        class="btn"
        id="scAddTrigger"
        type="button"
        style="margin-top:4px"
        @click=${function () {
          const edit = state.shortcutEdit;
          if (!edit) return;
          edit.triggers = (edit.triggers || []).concat([{ type: "boot" }]);
          notify();
        }}
      >
        ${t("shortcuts.add_trigger", "Add trigger")}
      </button>
      <div class="row" style="gap:8px;margin-top:16px;width:100%">
        <button
          class="btn primary"
          id="scSave"
          type="button"
          style="flex:1"
          @click=${function () {
            saveShortcut();
          }}
        >
          ${t("shortcuts.save", "Save")}
        </button>
        <button
          class="btn ghost"
          id="scCancel"
          type="button"
          style="flex:1"
          @click=${function () {
            state.shortcutEdit = null;
            notify();
          }}
        >
          ${t("shortcuts.cancel", "Cancel")}
        </button>
      </div>
    `,
  });
}

function toApiShortcut(body) {
  const actions = (body.actions || []).map(function (a) {
    a = normalizeAction(a);
    if (a.type !== "plugin") return a;
    const params = Object.assign({}, a.params || {});
    if (typeof params.data === "string" && params.data.trim()) {
      try {
        params.data = JSON.parse(params.data);
      } catch (e) {}
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

function finalizeEditBody() {
  const edit = state.shortcutEdit || {};
  (edit.actions || []).forEach(function (_, i) {
    syncPluginParamsFromDom(i, "action");
  });
  (edit.triggers || []).forEach(function (_, i) {
    syncPluginParamsFromDom(i, "trigger");
  });
  const name = (edit.name || "").trim() || "Shortcut";
  return {
    id: edit.id || undefined,
    name: name,
    icon: edit.icon || "drive",
    enabled: edit.enabled !== false,
    actions: (edit.actions || []).slice(0, 10),
    triggers: edit.triggers || [],
    conditions: edit.conditions || [],
  };
}

async function saveShortcut() {
  const body = toApiShortcut(finalizeEditBody());
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
  notify();
}

function slotsGrid() {
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
        body: choiceSelect({
          options: opts,
          current: slots[String(i)] || "",
          choiceKey: "sc-slot-" + i,
          onSelect: function (val) {
            runPref("sc-slot", val, { slot: i });
          },
        }),
      }),
    );
  }
  return html`
    <h2 class="section-label">${t("shortcuts.slots.title", "Pin slots")}</h2>
    <div class="grid">${cards}</div>
  `;
}

/** HU float chip / status-bar icon — shown on Settings. */
export function quickEntryCard() {
  ensureShortcutsState();
  const overlay = state.shortcutOverlay || {};
  const on = overlay.overlayEnabled !== false;
  const style = overlay.style || "float_chip";
  const canDraw = overlay.canDrawOverlays !== false;
  const opts = [
    { value: "1", label: t("shortcuts.topbar.show", "Show") },
    { value: "0", label: t("shortcuts.topbar.hide", "Hide") },
  ];
  const grantBtn =
    style === "float_chip" && !canDraw
      ? html`
          <button
            class="btn"
            id="scOverlayGrant"
            type="button"
            style="margin-top:10px;width:100%"
            @click=${async function () {
              await api("/api/shortcuts/overlay/request", { method: "POST" });
            }}
          >
            ${t("shortcuts.overlay.grant", "Grant overlay permission")}
          </button>
        `
      : nothing;
  const hint =
    style === "status_bar"
      ? t("shortcuts.topbar.hint_flyme", "Status-bar icon that opens the OCA menu")
      : t("shortcuts.topbar.hint", "Status-bar or overlay chip that opens the OCA menu");
  return prefCard({
    icon: "pin",
    title: t("shortcuts.topbar.title", "Floating menu"),
    sub: hint,
    body: html`
      ${prefSegment("sc-overlay", opts, on ? "1" : "0")}
      ${grantBtn}
    `,
  });
}

function shortcutListCard(s) {
  const trig = (s.triggers || []).map(triggerLabel).join(", ");
  const acts = (s.actions || []).map(actionLabel).join(" → ");
  return prefCard({
    icon: s.icon || "drive",
    title: s.name || s.id,
    sub:
      (s.enabled === false ? t("value.off", "Off") + " · " : "") +
      (trig || t("shortcuts.no_triggers", "No auto triggers")),
    body: html`
      <p class="mono hint" style="margin:0 0 10px">${acts || "—"}</p>
      <div class="row" style="gap:8px;width:100%">
        <button
          class="btn sc-run"
          type="button"
          style="flex:1"
          @click=${function () {
            runShortcut(s.id);
          }}
        >
          ${t("shortcuts.run", "Run")}
        </button>
        <button
          class="btn sc-edit"
          type="button"
          style="flex:1"
          @click=${function () {
            openEditShortcut(s.id);
          }}
        >
          ${t("shortcuts.edit", "Edit")}
        </button>
        <button
          class="btn ghost sc-del"
          type="button"
          @click=${function () {
            deleteShortcut(s.id);
          }}
        >
          ${t("shortcuts.delete", "Delete")}
        </button>
      </div>
    `,
  });
}

async function runShortcut(id) {
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
  notify();
}

function openEditShortcut(id) {
  const s = (state.shortcuts || []).find(function (x) {
    return x.id === id;
  });
  if (!s) return;
  state.shortcutEdit = JSON.parse(JSON.stringify(s));
  state.shortcutEdit.actions = (state.shortcutEdit.actions || []).map(normalizeAction);
  state.shortcutEdit.triggers = (state.shortcutEdit.triggers || []).map(normalizeTrigger);
  notify();
  requestAnimationFrame(function () {
    const wrap = document.getElementById("scEditorWrap");
    if (wrap) wrap.scrollIntoView({ behavior: "smooth", block: "start" });
    const name = document.getElementById("scName");
    if (name) name.focus();
  });
}

async function deleteShortcut(id) {
  if (!confirm(t("shortcuts.delete_confirm", "Delete this shortcut?"))) return;
  await api("/api/shortcuts/" + encodeURIComponent(id), { method: "DELETE" });
  if (state.shortcutEdit && state.shortcutEdit.id === id) state.shortcutEdit = null;
  await loadShortcuts();
  notify();
}

export function sectionShortcuts() {
  ensureShortcutsState();
  const list = state.shortcuts || [];
  const edit = state.shortcutEdit;
  const editingId = edit && edit.id ? edit.id : null;
  const isNew = !!(edit && !edit.id);

  const listCards = list.map(function (s) {
    if (editingId && s.id === editingId) {
      return html`<div id="scEditorWrap">${editorCard(edit)}</div>`;
    }
    return shortcutListCard(s);
  });

  const newEditor = isNew
    ? html`<div id="scEditorWrap">${editorCard(edit)}</div>`
    : nothing;

  let listContent;
  if (list.length || isNew) {
    listContent = html`${listCards} ${newEditor}`;
  } else {
    listContent = prefCard({
      icon: "drive",
      title: t("shortcuts.empty", "No shortcuts yet"),
      sub: t("shortcuts.empty.hint", "Create a shortcut, then assign it to a pin slot."),
      body: nothing,
    });
  }

  return html`
    <div class="section-head">
      <h1>${t("section.shortcuts.title", "Shortcuts")}</h1>
      <button
        class="btn primary"
        id="scNew"
        type="button"
        @click=${function () {
          state.shortcutEdit = {
            name: "",
            enabled: true,
            actions: [{ type: "delay_ms", ms: 0 }],
            triggers: [],
          };
          notify();
          requestAnimationFrame(function () {
            const wrap = document.getElementById("scEditorWrap");
            if (wrap) wrap.scrollIntoView({ behavior: "smooth", block: "start" });
            const name = document.getElementById("scName");
            if (name) name.focus();
          });
        }}
      >
        ${t("shortcuts.new", "New shortcut")}
      </button>
    </div>
    ${state.shortcutMessage
      ? html`<pre class="mono">${state.shortcutMessage}</pre>`
      : nothing}
    <div class="grid" style="margin-top:12px">${listContent}</div>
    ${slotsGrid()}
  `;
}
