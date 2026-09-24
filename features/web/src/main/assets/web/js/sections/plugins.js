import { html, nothing } from "../lit.js";
import { live } from "../lit.js";
import { repeat } from "../lit.js";
import { api } from "../api.js";
import { state, notify } from "../store.js";
import { prefCard, boolToggle } from "../ui/cards.js";
import { t } from "../i18n.js";

function pluginList() {
  return (state.status && state.status.plugins) || [];
}

function isConfigured(plugin) {
  const cfg = plugin.config || {};
  const st = plugin.status || {};
  if (cfg.configured === true || st.configured === true) return true;
  if (cfg.tokenSet && cfg.baseUrl) return true;
  if (st.hasToken && st.baseUrl) return true;
  return false;
}

function isEnabled(plugin) {
  const cfg = plugin.config || {};
  const st = plugin.status || {};
  if (cfg.enabled === false || st.enabled === false) return false;
  if (cfg.enabled === true || st.enabled === true) return true;
  return isConfigured(plugin);
}

async function reloadStatus() {
  try {
    state.status = await api("/api/status");
  } catch (e) {}
  notify();
}

function ensureDraft(pluginId) {
  if (!state.pluginDraft) state.pluginDraft = {};
  if (!state.pluginDraft[pluginId]) state.pluginDraft[pluginId] = {};
  return state.pluginDraft[pluginId];
}

function fieldValue(plugin, f, draft) {
  const cfg = plugin.config || {};
  const key = f.key;
  if (f.type === "bool") {
    if (draft[key] != null) return !!draft[key];
    if (cfg[key] != null) return !!cfg[key];
    return true;
  }
  if (f.type === "password") {
    return draft[key] != null ? String(draft[key]) : "";
  }
  if (draft[key] != null) return String(draft[key]);
  if (cfg[key] != null) return String(cfg[key]);
  return "";
}

function setupForm(plugin) {
  const cfg = plugin.config || {};
  const draft = ensureDraft(plugin.id);
  const fields = (plugin.schema && plugin.schema.fields) || [];

  return prefCard({
    icon: "energy",
    title: plugin.displayName || plugin.id,
    body: html`
      ${repeat(
        fields,
        function (f) {
          return f.key;
        },
        function (f) {
          const key = f.key;
          const id = "plugin-" + plugin.id + "-" + key;
          const label = f.label || key;
          if (f.type === "bool") {
            const checked = fieldValue(plugin, f, draft);
            return html`
              <div style="margin:0 0 16px;width:100%">
                <p class="hint" style="margin:0 0 8px">${label}</p>
                ${boolToggle(checked, function (val) {
                  draft[key] = val === "1";
                  notify();
                })}
              </div>
            `;
          }
          const isPassword = f.type === "password";
          var placeholder = f.placeholder || "";
          var helper = nothing;
          if (isPassword && cfg.tokenSet) {
            helper = html`<p class="persist-note">
              ${t("plugins.token_saved", "A token is already saved")}
              ${cfg.tokenHint ? " (" + cfg.tokenHint + "). " : ". "}
              ${t(
                "plugins.token_keep",
                "Leave empty to keep it, or paste a new token to replace.",
              )}
            </p>`;
            placeholder = t(
              "plugins.token_replace",
              "Paste a new token to replace (optional)",
            );
          }
          const value = fieldValue(plugin, f, draft);
          return html`
            <div style="margin:0 0 16px;width:100%">
              <label class="hint" for=${id}
                >${label}${f.optional ? "" : " *"}</label
              >
              <input
                class="field"
                id=${id}
                type=${isPassword ? "password" : "text"}
                placeholder=${placeholder}
                .value=${live(value)}
                style="width:100%;margin-top:6px"
                autocomplete=${isPassword ? "off" : nothing}
                @input=${function (ev) {
                  draft[key] = ev.target.value;
                }}
              />
              ${helper}
            </div>
          `;
        },
      )}
      <div class="row" style="width:100%;margin:12px 0 0;gap:8px">
        <button
          class="btn primary"
          style="flex:1"
          @click=${function () {
            savePlugin(plugin.id);
          }}
        >
          ${t("plugins.save", "Save")}
        </button>
        <button
          class="btn ghost"
          type="button"
          style="flex:1"
          @click=${function () {
            if (state.pluginDraft) delete state.pluginDraft[plugin.id];
            state.pluginEditId = null;
            notify();
          }}
        >
          ${t("plugins.cancel", "Cancel")}
        </button>
      </div>
    `,
  });
}

async function savePlugin(pluginId) {
  const plugins = pluginList();
  const plugin = plugins.find(function (p) {
    return p.id === pluginId;
  });
  if (!plugin) return;
  const fields = (plugin.schema && plugin.schema.fields) || [];
  const draft = (state.pluginDraft && state.pluginDraft[pluginId]) || {};
  const body = {};
  fields.forEach(function (f) {
    const key = f.key;
    if (f.type === "bool") {
      body[key] =
        draft[key] != null
          ? !!draft[key]
          : fieldValue(plugin, f, draft);
      return;
    }
    const val = draft[key] != null ? String(draft[key]) : "";
    if (f.type === "password" && !val) return;
    if (val !== "" || !f.optional) body[key] = val;
  });
  if (body.enabled == null) body.enabled = true;
  await api("/api/plugins/" + encodeURIComponent(pluginId), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (state.pluginDraft) delete state.pluginDraft[pluginId];
  state.pluginEditId = null;
  await reloadStatus();
}

function catalogCard(plugin) {
  return prefCard({
    icon: "energy",
    title: plugin.displayName || plugin.id,
    body: html`
      <button
        class="btn primary plugin-add"
        type="button"
        style="width:100%"
        @click=${function () {
          state.pluginEditId = plugin.id;
          ensureDraft(plugin.id);
          notify();
        }}
      >
        ${t("plugins.add", "Add / Set up")}
      </button>
    `,
  });
}

function activeCard(plugin) {
  const cfg = plugin.config || {};
  const st = plugin.status || {};
  const enabled = isEnabled(plugin);
  const connected = !!(cfg.connected || st.connected);
  const sub = connected
    ? t("plugins.connected", "Connected")
    : t("plugins.disconnected", "Not connected");
  return prefCard({
    icon: "energy",
    title: plugin.displayName || plugin.id,
    sub: enabled ? sub : t("plugins.status.disabled", "Disabled"),
    body: html`
      <div class="row" style="width:100%;gap:8px">
        <button
          class="btn plugin-edit"
          type="button"
          style="flex:1"
          @click=${function () {
            state.pluginEditId = plugin.id;
            ensureDraft(plugin.id);
            notify();
          }}
        >
          ${t("plugins.configure", "Configure")}
        </button>
        <button
          class="btn ghost plugin-toggle"
          type="button"
          style="flex:1"
          @click=${async function () {
            await api("/api/plugins/" + encodeURIComponent(plugin.id), {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ enabled: !enabled }),
            });
            await reloadStatus();
          }}
        >
          ${enabled
            ? t("plugins.disable", "Disable")
            : t("plugins.enable", "Enable")}
        </button>
      </div>
    `,
  });
}

function labeledGrid(label, cards) {
  if (!cards || !cards.length) return nothing;
  return html`
    <h2 class="section-label">${label}</h2>
    <div class="grid">${cards}</div>
  `;
}

export function sectionPlugins() {
  if (state.pluginEditId == null) state.pluginEditId = null;
  const plugins = pluginList();
  const editId = state.pluginEditId;
  const editing = editId
    ? plugins.find(function (p) {
        return p.id === editId;
      })
    : null;

  if (editing) {
    return html`
      <h1>${t("nav.plugins", "Plugins")}</h1>
      <div class="grid">${setupForm(editing)}</div>
    `;
  }

  const available = plugins.filter(function (p) {
    return !isConfigured(p);
  });
  const active = plugins.filter(function (p) {
    return isConfigured(p);
  });

  if (!plugins.length) {
    return html`
      <h1>${t("nav.plugins", "Plugins")}</h1>
      ${labeledGrid(
        t("plugins.available", "Available"),
        [
          prefCard({
            icon: "energy",
            title: t("plugins.empty", "No plugins available"),
            body: nothing,
          }),
        ],
      )}
    `;
  }

  return html`
    <h1>${t("nav.plugins", "Plugins")}</h1>
    ${labeledGrid(
      t("plugins.available", "Available"),
      available.map(catalogCard),
    )}
    ${labeledGrid(
      t("plugins.installed", "Installed"),
      active.map(activeCard),
    )}
  `;
}
