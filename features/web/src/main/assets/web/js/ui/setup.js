import { html, render, nothing } from "../lit.js";
import { api, $, fmt } from "../api.js";
import { state, patch, notify } from "../store.js";
import { t } from "../i18n.js";

export function shouldShowSetup(setup) {
  if (!setup) return false;
  if (setup.needsSetup) return true;
  return false;
}

function hostCmd() {
  const s = state.setup;
  return (
    (s && s.adbHints && s.adbHints[0]) ||
    "./tools/oca-setup -i " +
      ((state.status && state.status.integration) || "PLATFORM_ID") +
      " -H <ip> setup"
  );
}

export function setupOverlayTemplate() {
  const s = state.setup;
  if (!s || !state.showSetup) return nothing;
  const actions = s.actions || {};
  const accessNote = s.accessMode
    ? html`<p class="sub">
        VHAL: <code class="mono">${s.accessMode}</code> (${s.accessMode === "grpc"
          ? "unprivileged / VenusVehicleServer"
          : s.accessMode === "car_property"
            ? "privileged / CarPropertyManager"
            : "unknown"})
      </p>`
    : nothing;

  return html`
    <div class="setup-overlay">
      <div class="setup-panel">
        <h1>${t("setup.title", "Configuração")}</h1>
        <p class="sub">
          ${t(
            "setup.sub",
            "Conceda permissões de runtime no HU. Instalação privilegiada é opcional.",
          )}
        </p>
        ${accessNote}
        ${(s.steps || []).map(function (st) {
          return html`<div class="setup-step ${st.done ? "done" : ""}">
            <div class="mark">${st.done ? "✓" : "·"}</div>
            <div class="body">
              <h3>${st.title}${st.optional ? " (opcional)" : ""}</h3>
              <p>${st.detail}</p>
            </div>
          </div>`;
        })}
        <ul class="setup-perms">
          ${(s.permissions || []).map(function (p) {
            return html`<li>
              <span>${p.label} <span class="mono">(${p.kind})</span></span>
              <span class="badge ${p.granted ? "ok" : "warn"}"
                >${p.granted ? "OK" : "Pendente"}</span
              >
            </li>`;
          })}
        </ul>
        <p class="sub">${state.setupMsg || ""}</p>
        <div class="row" style="margin-top:12px">
          <button
            class="btn primary"
            @click=${async function () {
              patch({ setupMsg: "Solicitando…" });
              await api("/api/setup/actions/request-runtime", { method: "POST" });
              patch({
                setupMsg: "Aceite o diálogo no head unit, depois Verifique de novo.",
              });
            }}
          >
            ${actions.grant || t("setup.action.grant", "Conceder permissões")}
          </button>
          ${s.privilegedOk
            ? nothing
            : html`<button
                class="btn ghost"
                @click=${async function () {
                  patch({ setupMsg: "Elevando (su)…" });
                  const r = await api("/api/setup/actions/elevate", { method: "POST" });
                  let msg = r.message || JSON.stringify(r);
                  if (r.needsReboot) {
                    msg += " — " + t("setup.reboot.banner", "Reinicie o carro");
                  }
                  patch({ setupMsg: msg });
                }}
              >
                ${actions.elevate ||
                t("setup.action.elevate", "Instalar privilegiado (opcional)")}
              </button>`}
          <button
            class="btn ghost"
            @click=${async function () {
              const cmd = hostCmd();
              try {
                await navigator.clipboard.writeText(cmd);
                patch({ setupMsg: "Copiado: " + cmd });
              } catch (e) {
                patch({ setupMsg: cmd });
              }
            }}
          >
            ${actions.host || t("setup.action.host", "Comando no PC")}
          </button>
          <button
            class="btn ghost"
            @click=${async function () {
              patch({ setupMsg: "Reiniciando…" });
              await api("/api/setup/actions/reboot", { method: "POST" });
            }}
          >
            ${actions.reboot || t("setup.action.reboot", "Reiniciar")}
          </button>
        </div>
        <p class="sub">ADB / host</p>
        <p>
          ${(s.adbHints || []).map(function (h) {
            return html`<code class="mono">${h}</code><br />`;
          })}
        </p>
        <div class="row" style="margin-top:20px">
          ${s.runtimeOk && s.hasBasicTelemetry
            ? html`<button
                class="btn primary"
                @click=${async function () {
                  const setup = await api("/api/setup", {
                    method: "POST",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: "dismiss=1",
                  });
                  patch({ setup: setup, showSetup: false, setupMsg: "" });
                }}
              >
                ${t("setup.continue", "Continuar")}
              </button>`
            : nothing}
          <button
            class="btn ghost"
            @click=${async function () {
              const setup = await api("/api/setup");
              patch({
                setup: setup,
                showSetup: shouldShowSetup(setup),
                setupMsg: "",
              });
            }}
          >
            ${actions.refresh || t("setup.action.refresh", "Atualizar")}
          </button>
        </div>
      </div>
    </div>
  `;
}

/** Render setup overlay into a host on document.body (outside #main). */
export function renderSetupOverlay() {
  let host = $("setupOverlayHost");
  if (!host) {
    host = document.createElement("div");
    host.id = "setupOverlayHost";
    document.body.appendChild(host);
  }
  render(setupOverlayTemplate(), host);
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
  const leftEl = $("statusLeft");
  if (leftEl) leftEl.textContent = left;
  const setup = state.setup || s.setup;
  const ok = !!s.integration && (setup ? setup.hasBasicTelemetry : true);
  const dot = document.querySelector("#statusRight .dot");
  if (dot) {
    dot.classList.remove("warn", "bad");
    if (!ok) dot.classList.add("bad");
  }
  const conn = $("connLabel");
  if (conn) {
    conn.textContent = ok ? t("app.connected", "Conectado") : t("app.offline", "Offline");
    if (s.remote) conn.textContent += " · Remoto";
  }
}
