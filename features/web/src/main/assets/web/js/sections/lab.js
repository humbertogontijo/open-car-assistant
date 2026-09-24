import { html, nothing, repeat } from "../lit.js";
import { state, patch, notify } from "../store.js";
import { t } from "../i18n.js";
import { api, fmt } from "../api.js";
import { prefSegment } from "../ui/cards.js";

function probeRows() {
  const tab = state.labTab || "vhal";
  const q = (state.probeFilter || "").toLowerCase();
  let rows;
  if (tab === "entities") {
    rows = (state.entities || []).map(function (e) {
      return {
        name: e.id || e.label || "",
        family: e.entity || e.group || "",
        status: e.status || "",
        value: e.valueLabel || e.value || "",
        permission: e.group || "",
      };
    });
  } else {
    const src = tab === "obd2" ? state.obd2 : state.probe;
    rows = (src && src.results) || [];
  }
  return rows
    .filter(function (r) {
      if (!q) return true;
      return (r.name + r.family + r.status + (r.permission || "") + (r.value || ""))
        .toLowerCase()
        .indexOf(q) >= 0;
    })
    .slice(0, 400);
}

export function sectionLab() {
  const lab = state.lab || {};
  const tab = state.labTab || "vhal";
  const p = tab === "obd2" ? state.obd2 : tab === "entities" ? null : state.probe;
  const sum =
    tab === "entities"
      ? {
          source: "ControlCatalog /api/entities",
          count: (state.entities || []).length,
        }
      : p && p.summary
        ? p.summary
        : p;
  const sourceHint =
    tab === "obd2"
      ? t("lab.source.obd2", "OBD2_LIVE_FRAME / OBD2_FREEZE_FRAME (VHAL)")
      : tab === "entities"
        ? t("lab.source.entities", "Bound product entities (ControlCatalog)")
        : t("lab.source.vhal", "VHAL catalog (CarPropertyManager / gRPC)");
  const contributorOn = !!lab.contributor;
  const token = (contributorOn && (lab.token || state.token)) || "";
  const override = lab.integrationOverride || "";
  const integrations = lab.integrations || [];
  const tabOpts = [
    { value: "vhal", label: t("lab.tab.vhal", "VHAL catalog") },
    { value: "obd2", label: t("lab.tab.obd2", "OBD2") },
    { value: "entities", label: t("lab.tab.entities", "Product entities") },
  ];
  const rows = probeRows();

  return html`
    <h1>${t("lab.title", "Lab / Contributor")}</h1>
    <p class="sub">
      ${t("lab.sub", "Probe data sources · enable Contributor mode for /debug writes")}
    </p>
    <div class="card" style="margin-bottom:16px">
      <div class="row" style="align-items:center;gap:12px;flex-wrap:wrap">
        <label style="display:flex;align-items:center;gap:8px">
          <input
            type="checkbox"
            .checked=${contributorOn}
            @change=${async function (ev) {
              const enabled = ev.target.checked ? "1" : "0";
              try {
                const next = await api("/api/lab/contributor", {
                  method: "POST",
                  headers: { "Content-Type": "application/x-www-form-urlencoded" },
                  body: "enabled=" + enabled,
                });
                patch({
                  lab: next,
                  token: next && next.token ? next.token : "",
                });
              } catch (e) {
                state.lab = state.lab || {};
                state.lab.restartHint = String(e && e.message ? e.message : e);
                notify();
              }
            }}
          />
          ${t("lab.contributor", "Contributor mode")}
        </label>
        <span class="mono"
          >${t("lab.token", "Token")}:
          ${token ? html`<code>${token}</code>` : "—"}</span
        >
      </div>
      <div class="row" style="align-items:center;gap:12px;margin-top:12px;flex-wrap:wrap">
        <label>
          ${t("lab.override", "Integration override")}
          <select
            id="labOverride"
            .value=${override}
            @change=${function (ev) {
              state._labOverrideDraft = ev.target.value;
            }}
          >
            <option value="">
              ${t("lab.override.auto", "Auto (fingerprint match)")}
            </option>
            ${integrations.map(function (id) {
              return html`<option value=${id} ?selected=${id === override}>${id}</option>`;
            })}
          </select>
        </label>
        <button
          class="btn"
          @click=${async function () {
            const id =
              state._labOverrideDraft != null
                ? state._labOverrideDraft
                : (document.getElementById("labOverride") &&
                    document.getElementById("labOverride").value) ||
                  "";
            try {
              const res = await api("/api/lab/integration-override", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "id=" + encodeURIComponent(id),
              });
              if (res && res.hint) res.restartHint = res.hint;
              patch({ lab: res });
            } catch (e) {
              state.lab = state.lab || {};
              state.lab.restartHint = String(e && e.message ? e.message : e);
              notify();
            }
          }}
        >
          ${t("lab.override.apply", "Apply")}
        </button>
      </div>
      ${lab.restartHint
        ? html`<p class="sub" style="margin:8px 0 0;color:var(--warn, #c90)">
            ${lab.restartHint}
          </p>`
        : html`<p class="sub" style="margin:8px 0 0">
            ${t(
              "lab.override.hint",
              "Override applies after force-stop or reboot. Matched now: ",
            )}<code class="mono">${lab.integration || "—"}</code>
          </p>`}
    </div>
    <div class="card" style="margin-bottom:12px">
      ${prefSegment("lab-tab", tabOpts, tab)}
      <p class="sub" style="margin:10px 0 0">${sourceHint}</p>
    </div>
    <div class="card">
      <div class="row">
        <button
          class="btn primary"
          @click=${async function () {
            const tok = encodeURIComponent(state.token);
            if (tab === "obd2") {
              patch({ obd2: await api("/debug/obd2?force=1&token=" + tok) });
            } else if (tab === "entities") {
              patch({ entities: await api("/api/entities") });
            } else {
              patch({ probe: await api("/debug/probe?force=1&token=" + tok) });
            }
          }}
        >
          ${t("lab.probe", "Re-probe")}
        </button>
        <a class="btn" href=${"/debug/export?token=" + encodeURIComponent(token)}
          >${t("lab.export", "Export zip")}</a
        >
        <input
          type="text"
          placeholder=${t("lab.filter", "filter…")}
          .value=${state.probeFilter || ""}
          style="flex:1"
          @input=${function (ev) {
            patch({ probeFilter: ev.target.value || "" });
          }}
        />
      </div>
      <pre class="mono">
${JSON.stringify(sum || { tip: t("lab.probe.tip", "Click Re-probe") }, null, 2)}</pre
      >
      <div id="probeScroll" style="max-height:420px;overflow:auto">
        <table class="table" id="probeTable">
          <thead>
            <tr>
              <th>Nome</th>
              <th>Família</th>
              <th>Status</th>
              <th>Valor</th>
              <th>Perm</th>
            </tr>
          </thead>
          <tbody>
            ${repeat(
              rows,
              function (r) {
                return r.name + "|" + r.family;
              },
              function (r) {
                return html`<tr>
                  <td class="mono">${r.name}</td>
                  <td>${r.family}</td>
                  <td>${r.status}</td>
                  <td class="mono">${fmt(r.value)}</td>
                  <td class="mono">${fmt(r.permission)}</td>
                </tr>`;
              },
            )}
          </tbody>
        </table>
      </div>
    </div>
  `;
}
