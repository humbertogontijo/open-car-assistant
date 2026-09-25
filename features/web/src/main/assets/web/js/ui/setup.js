import { loadCss } from "./load-css.js";
loadCss("/static/js/ui/setup.css");

import { html, render, nothing } from "../lit.js";
import { api, $ } from "../api.js";
import { state, patch } from "../store.js";
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
        VHAL: <code class="mono">${s.accessMode}</code>${s.accessMode === "grpc"
          ? " (VenusVehicleServer)"
          : ""}
      </p>`
    : nothing;

  return html`
    <div class="setup-overlay">
      <div class="setup-panel">
        <h1>${t("setup.title", "Configuração")}</h1>
        <p class="sub">
          ${t(
            "setup.sub",
            "Grant runtime permissions on the HU.",
          )}
        </p>
        ${accessNote}
        ${(s.steps || []).map(function (st) {
          return html`<div class="setup-step ${st.done ? "done" : ""}">
            <div class="mark">${st.done ? "✓" : "·"}</div>
            <div class="body">
              <h3>${st.title}</h3>
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
