import { api, $, fmt } from "./api.js";
import { state } from "./state.js";
import { t } from "./i18n.js";

export function shouldShowSetup(setup) {
  if (!setup) return false;
  if (setup.needsSetup) return true;
  return false;
}

export function renderSetupOverlay() {
  const s = state.setup;
  if (!s || !state.showSetup) {
    const el = $("setupOverlay");
    if (el) el.classList.add("hidden");
    return;
  }
  let el = $("setupOverlay");
  if (!el) {
    el = document.createElement("div");
    el.id = "setupOverlay";
    el.className = "setup-overlay";
    document.body.appendChild(el);
  }
  el.classList.remove("hidden");
  const actions = s.actions || {};
  const steps = (s.steps || [])
    .map(function (st) {
      return (
        '<div class="setup-step' +
        (st.done ? " done" : "") +
        '"><div class="mark">' +
        (st.done ? "✓" : "·") +
        '</div><div class="body"><h3>' +
        st.title +
        (st.optional ? " (opcional)" : "") +
        "</h3><p>" +
        st.detail +
        "</p></div></div>"
      );
    })
    .join("");
  const perms = (s.permissions || [])
    .map(function (p) {
      return (
        "<li><span>" +
        p.label +
        ' <span class="mono">(' +
        p.kind +
        ")</span></span>" +
        '<span class="badge ' +
        (p.granted ? "ok" : "warn") +
        '">' +
        (p.granted ? "OK" : "Pendente") +
        "</span></li>"
      );
    })
    .join("");
  const hostCmd =
    (s.adbHints && s.adbHints[0]) ||
    "./tools/oca-setup -i " +
      ((state.status && state.status.integration) || "PLATFORM_ID") +
      " -H <ip> setup";
  const hints = (s.adbHints || [])
    .map(function (h) {
      return '<code class="mono">' + h + "</code>";
    })
    .join("<br>");

  el.innerHTML =
    '<div class="setup-panel">' +
    "<h1>" +
    t("setup.title", "Configuração") +
    "</h1>" +
    '<p class="sub">' +
    t("setup.sub", "Conceda permissões de runtime no HU. Instalação privilegiada é opcional.") +
    "</p>" +
    (s.accessMode
      ? '<p class="sub">VHAL: <code class="mono">' +
        s.accessMode +
        "</code> (" +
        (s.accessMode === "grpc"
          ? "unprivileged / VenusVehicleServer"
          : s.accessMode === "car_property"
            ? "privileged / CarPropertyManager"
            : "unknown") +
        ")</p>"
      : "") +
    steps +
    '<ul class="setup-perms">' +
    perms +
    "</ul>" +
    '<p class="sub" id="setupMsg"></p>' +
    '<div class="row" style="margin-top:12px">' +
    '<button class="btn primary" id="setupRequestRuntime">' +
    (actions.grant || t("setup.action.grant", "Conceder permissões")) +
    "</button>" +
    (s.privilegedOk
      ? ""
      : '<button class="btn ghost" id="setupElevate">' +
        (actions.elevate || t("setup.action.elevate", "Instalar privilegiado (opcional)")) +
        "</button>") +
    '<button class="btn ghost" id="setupCopyHost">' +
    (actions.host || t("setup.action.host", "Comando no PC")) +
    "</button>" +
    '<button class="btn ghost" id="setupReboot">' +
    (actions.reboot || t("setup.action.reboot", "Reiniciar")) +
    "</button>" +
    "</div>" +
    "<p class=\"sub\">ADB / host</p><p>" +
    hints +
    "</p>" +
    '<div class="row" style="margin-top:20px">' +
    (s.runtimeOk && s.hasBasicTelemetry
      ? '<button class="btn primary" id="setupContinue">' +
        t("setup.continue", "Continuar") +
        "</button>"
      : "") +
    '<button class="btn ghost" id="setupRefresh">' +
    (actions.refresh || t("setup.action.refresh", "Atualizar")) +
    "</button>" +
    "</div></div>";

  function msg(text) {
    const m = $("setupMsg");
    if (m) m.textContent = text || "";
  }

  if ($("setupRequestRuntime")) {
    $("setupRequestRuntime").onclick = async function () {
      msg("Solicitando…");
      await api("/api/setup/actions/request-runtime", { method: "POST" });
      msg("Aceite o diálogo no head unit, depois Verifique de novo.");
    };
  }
  if ($("setupElevate")) {
    $("setupElevate").onclick = async function () {
      msg("Elevando (su)…");
      const r = await api("/api/setup/actions/elevate", { method: "POST" });
      msg(r.message || JSON.stringify(r));
      if (r.needsReboot) {
        msg((r.message || "") + " — " + t("setup.reboot.banner", "Reinicie o carro"));
      }
    };
  }
  if ($("setupCopyHost")) {
    $("setupCopyHost").onclick = async function () {
      try {
        await navigator.clipboard.writeText(hostCmd);
        msg("Copiado: " + hostCmd);
      } catch (e) {
        msg(hostCmd);
      }
    };
  }
  if ($("setupReboot")) {
    $("setupReboot").onclick = async function () {
      msg("Reiniciando…");
      await api("/api/setup/actions/reboot", { method: "POST" });
    };
  }
  if ($("setupContinue")) {
    $("setupContinue").onclick = async function () {
      state.setup = await api("/api/setup", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "dismiss=1",
      });
      state.showSetup = false;
      renderSetupOverlay();
    };
  }
  if ($("setupRefresh")) {
    $("setupRefresh").onclick = async function () {
      state.setup = await api("/api/setup");
      state.showSetup = shouldShowSetup(state.setup);
      renderSetupOverlay();
    };
  }
}

/** Quiet tip for About — privileged install is optional and not a blocker. */
export function aboutPrivilegeTipHtml() {
  const s = state.setup;
  if (!s || s.privilegedOk) return "";
  return (
    '<p class="persist-note" style="margin:0 0 1rem">' +
    t(
      "about.tip.privileged",
      "Privileged install is optional — only if Climate or vendor writes fail.",
    ) +
    ' <a href="#" id="openSetup">' +
    t("setup.open", "Open setup") +
    "</a></p>"
  );
}

export function bindPrivilegeTip() {
  const a = $("openSetup");
  if (a) {
    a.onclick = function (ev) {
      if (ev) ev.preventDefault();
      state.showSetup = true;
      renderSetupOverlay();
    };
  }
}

export function renderStatusBar() {
  const s = state.status || {};
  const tel = s.telemetry || {};
  const soc =
    tel.evBatteryPercent != null
      ? Math.round(tel.evBatteryPercent) + "%"
      : tel.hybridSocPercent != null
        ? Math.round(tel.hybridSocPercent) + "%"
        : null;
  const left =
    (tel.model ? tel.model + " · " : "") +
    "SOC " +
    fmt(soc) +
    " · " +
    t("sensor.gear", "Gear") +
    " " +
    fmt(tel.gearLabel || tel.gear) +
    " · " +
    fmt(tel.speedKmh != null ? Number(tel.speedKmh).toFixed(0) + " km/h" : null) +
    " · " +
    fmt(tel.rangeKm != null ? Math.round(tel.rangeKm) + " km" : null);
  $("statusLeft").textContent = left;
  const setup = state.setup || s.setup;
  const ok = !!s.integration && (setup ? setup.hasBasicTelemetry : true);
  const dot = document.querySelector("#statusRight .dot");
  if (dot) {
    dot.classList.remove("warn", "bad");
    if (!ok) dot.classList.add("bad");
  }
  $("connLabel").textContent = ok ? t("app.connected", "Conectado") : t("app.offline", "Offline");
  if (s.remote) $("connLabel").textContent += " · Remoto";
}
