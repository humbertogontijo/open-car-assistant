import { api, $ } from "./api.js";
import { state } from "./state.js";
import { prefCard, boolToggleHtml } from "./cards.js";
import { t } from "./i18n.js";

function escAttr(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

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

function setupFormHtml(plugin) {
  const cfg = plugin.config || {};
  const status = plugin.status || {};
  const draft = (state.pluginDraft && state.pluginDraft[plugin.id]) || {};
  const fields = (plugin.schema && plugin.schema.fields) || [];
  const fieldHtml = fields
    .map(function (f) {
      const key = f.key;
      const id = "plugin-" + plugin.id + "-" + key;
      const label = f.label || key;
      if (f.type === "bool") {
        const checked =
          draft[key] != null ? !!draft[key] : cfg[key] != null ? !!cfg[key] : true;
        return (
          '<div style="margin:0 0 16px;width:100%">' +
          '<p class="hint" style="margin:0 0 8px">' +
          escAttr(label) +
          "</p>" +
          boolToggleHtml(checked, 'data-plugin-field="' + escAttr(key) + '" data-plugin-id="' + escAttr(plugin.id) + '"') +
          "</div>"
        );
      }
      const isPassword = f.type === "password";
      var placeholder = f.placeholder || "";
      var helper = "";
      if (isPassword && cfg.tokenSet) {
        helper =
          t("plugins.token_saved", "A token is already saved") +
          (cfg.tokenHint ? " (" + escAttr(cfg.tokenHint) + "). " : ". ") +
          t("plugins.token_keep", "Leave empty to keep it, or paste a new token to replace.");
        placeholder = t("plugins.token_replace", "Paste a new token to replace (optional)");
      }
      var value = "";
      if (isPassword) {
        value = draft[key] != null ? String(draft[key]) : "";
      } else if (draft[key] != null) {
        value = String(draft[key]);
      } else if (cfg[key] != null) {
        value = String(cfg[key]);
      }
      return (
        '<div style="margin:0 0 16px;width:100%">' +
        '<label class="hint" for="' +
        escAttr(id) +
        '">' +
        escAttr(label) +
        (f.optional ? "" : " *") +
        "</label>" +
        '<input class="field" id="' +
        escAttr(id) +
        '" type="' +
        (isPassword ? "password" : "text") +
        '" data-plugin-field="' +
        escAttr(key) +
        '" data-plugin-id="' +
        escAttr(plugin.id) +
        '" placeholder="' +
        escAttr(placeholder) +
        '" value="' +
        escAttr(value) +
        '" style="width:100%;margin-top:6px"' +
        (isPassword ? ' autocomplete="off"' : "") +
        ">" +
        (helper ? '<p class="persist-note">' + helper + "</p>" : "") +
        "</div>"
      );
    })
    .join("");

  return prefCard({
    icon: "energy",
    title: plugin.displayName || plugin.id,
    bodyHtml:
      fieldHtml +
      '<div class="row" style="width:100%;margin:12px 0 0;gap:8px">' +
      '<button class="btn primary" id="pluginSave" data-plugin-id="' +
      escAttr(plugin.id) +
      '" style="flex:1">' +
      t("plugins.save", "Save") +
      "</button>" +
      '<button class="btn ghost" id="pluginCancel" type="button" style="flex:1">' +
      t("plugins.cancel", "Cancel") +
      "</button></div>",
  });
}

function catalogCard(plugin) {
  return prefCard({
    icon: "energy",
    title: plugin.displayName || plugin.id,
    bodyHtml:
      '<button class="btn primary plugin-add" type="button" data-plugin-id="' +
      escAttr(plugin.id) +
      '" style="width:100%">' +
      t("plugins.add", "Add / Set up") +
      "</button>",
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
    bodyHtml:
      '<div class="row" style="width:100%;gap:8px">' +
      '<button class="btn plugin-edit" type="button" data-plugin-id="' +
      escAttr(plugin.id) +
      '" style="flex:1">' +
      t("plugins.configure", "Configure") +
      "</button>" +
      '<button class="btn ghost plugin-toggle" type="button" data-plugin-id="' +
      escAttr(plugin.id) +
      '" data-enable="' +
      (enabled ? "0" : "1") +
      '" style="flex:1">' +
      (enabled
        ? t("plugins.disable", "Disable")
        : t("plugins.enable", "Enable")) +
      "</button></div>",
  });
}

function labeledGrid(label, cardsHtml) {
  if (!cardsHtml) return "";
  return (
    '<h2 class="section-label">' +
    label +
    '</h2><div class="grid">' +
    cardsHtml +
    "</div>"
  );
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
    return (
      "<h1>" +
      t("nav.plugins", "Plugins") +
      "</h1>" +
      '<div class="grid">' +
      setupFormHtml(editing) +
      "</div>"
    );
  }

  const available = plugins.filter(function (p) {
    return !isConfigured(p);
  });
  const active = plugins.filter(function (p) {
    return isConfigured(p);
  });

  var body = "";
  if (!plugins.length) {
    body = labeledGrid(
      t("plugins.available", "Available"),
      prefCard({
        icon: "energy",
        title: t("plugins.empty", "No plugins available"),
        bodyHtml: "",
      }),
    );
  } else {
    body += labeledGrid(
      t("plugins.available", "Available"),
      available.map(catalogCard).join(""),
    );
    body += labeledGrid(
      t("plugins.installed", "Installed"),
      active.map(activeCard).join(""),
    );
  }

  return (
    "<h1>" +
    t("nav.plugins", "Plugins") +
    "</h1>" +
    body
  );
}

export function bindPlugins(refresh) {
  document.querySelectorAll(".plugin-add, .plugin-edit").forEach(function (btn) {
    btn.onclick = function () {
      state.pluginEditId = btn.getAttribute("data-plugin-id");
      if (!state.pluginDraft) state.pluginDraft = {};
      refresh();
    };
  });
  if ($("pluginCancel")) {
    $("pluginCancel").onclick = function () {
      const id = state.pluginEditId;
      if (id && state.pluginDraft) delete state.pluginDraft[id];
      state.pluginEditId = null;
      refresh();
    };
  }
  // Keep typed values across any accidental remount.
  document.querySelectorAll("[data-plugin-field][data-plugin-id]").forEach(function (el) {
    if (el.tagName !== "INPUT") return;
    const saveDraft = function () {
      const pluginId = el.getAttribute("data-plugin-id");
      const key = el.getAttribute("data-plugin-field");
      if (!pluginId || !key) return;
      if (!state.pluginDraft) state.pluginDraft = {};
      if (!state.pluginDraft[pluginId]) state.pluginDraft[pluginId] = {};
      state.pluginDraft[pluginId][key] = el.value;
    };
    el.oninput = saveDraft;
    el.onchange = saveDraft;
  });
  document.querySelectorAll(".plugin-toggle").forEach(function (btn) {
    btn.onclick = async function () {
      const pluginId = btn.getAttribute("data-plugin-id");
      const enable = btn.getAttribute("data-enable") === "1";
      if (!pluginId) return;
      await api("/api/plugins/" + encodeURIComponent(pluginId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled: enable }),
      });
      await refresh();
    };
  });
  if ($("pluginSave")) {
    $("pluginSave").onclick = async function () {
      const pluginId = $("pluginSave").getAttribute("data-plugin-id");
      if (!pluginId) return;
      const body = {};
      document
        .querySelectorAll('[data-plugin-id="' + pluginId + '"][data-plugin-field]')
        .forEach(function (el) {
          const key = el.getAttribute("data-plugin-field");
          if (!key) return;
          if (el.classList.contains("toggle-seg")) return;
          if (el.tagName === "INPUT") {
            const val = el.value;
            if (el.type === "password" && !val) return;
            body[key] = val;
          }
        });
      document
        .querySelectorAll(
          '.toggle-seg.active[data-plugin-id="' + pluginId + '"][data-plugin-field]',
        )
        .forEach(function (activeBool) {
          body[activeBool.getAttribute("data-plugin-field")] =
            activeBool.getAttribute("data-val") === "1";
        });
      // First-time setup: if enabled wasn't toggled, turn on when saving.
      if (body.enabled == null) body.enabled = true;
      await api("/api/plugins/" + encodeURIComponent(pluginId), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      if (state.pluginDraft) delete state.pluginDraft[pluginId];
      state.pluginEditId = null;
      await refresh();
    };
  }
}
