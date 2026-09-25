import { html, nothing } from "../lit.js";
import {
  entitiesByGroup,
  hiddenEntitiesByGroup,
  isShowingHidden,
  state,
  patch,
} from "../store.js";
import { t } from "../i18n.js";
import { api } from "../api.js";
import { pageHead } from "../ui/cards.js";
import { familySections } from "./group.js";
import {
  dashSummary,
  dashSparkline,
  pickEntities,
  withoutIds,
} from "../ui/dashboard.js";

var ENERGY_HERO_IDS = [
  "sensor_soc",
  "sensor_hybrid_soc",
  "sensor_range",
  "sensor_range_ev",
  "sensor_fuel",
  "sensor_charge_plug",
  "charge_current",
  "sensor_charge_energy",
  "sensor_charge_eta",
];

var ENERGY_SPARK_IDS = ["sensor_soc", "sensor_charge_energy", "sensor_avg_energy"];

export async function loadEnergyDash() {
  if (state._energyDashLoading) return;
  state._energyDashLoading = true;
  try {
    const end = Date.now();
    const start = end - 24 * 60 * 60 * 1000;
    const sparks = {};
    await Promise.all(
      ENERGY_SPARK_IDS.map(async function (id) {
        try {
          const res = await api(
            "/api/history/" +
              encodeURIComponent(id) +
              "?start=" +
              start +
              "&end=" +
              end +
              "&limit=200",
          );
          if (res && res.points && res.points.length) sparks[id] = res.points;
        } catch (e) {
          /* history optional */
        }
      }),
    );
    patch({ energySparks: sparks });
  } finally {
    state._energyDashLoading = false;
  }
}

export function pageEnergy() {
  const group = "energy";
  const viewing = isShowingHidden(group);
  const items = viewing ? hiddenEntitiesByGroup(group) : entitiesByGroup(group);
  // Pull home/energy sensors that live on Início but belong in the energy hero.
  const pool = viewing
    ? items
    : (state.entities || []).filter(function (e) {
        return (
          (e.group === "energy" ||
            e.id === "sensor_soc" ||
            e.id === "sensor_hybrid_soc" ||
            e.id === "sensor_range" ||
            e.id === "sensor_range_ev" ||
            e.id === "sensor_fuel") &&
          (e.status === "ok" || e.status === "cached")
        );
      });
  const hero = pickEntities(pool, ENERGY_HERO_IDS);
  const heroIds = hero.map(function (e) {
    return e.id;
  });
  const rest = withoutIds(items, heroIds);
  const sparks = state.energySparks || {};
  const sparkBlocks = ENERGY_SPARK_IDS.map(function (id) {
    const pts = sparks[id];
    if (!pts || pts.length < 2) return null;
    const ent = pickEntities(pool, [id])[0];
    const title = (ent && (ent.friendlyName || ent.label)) || id;
    return dashSparkline(pts, { title: title });
  }).filter(Boolean);

  return html`
    ${pageHead(
      t("section.energy.title", "Energia"),
      group,
      t("section.energy.sub", "Battery, charging, and consumption"),
    )}
    ${viewing
      ? familySections(items, { restore: true })
      : html`
          ${dashSummary(hero, { className: "dash-energy" })}
          ${sparkBlocks.length
            ? html`<div class="dash-sparks">${sparkBlocks}</div>`
            : nothing}
          <h2 class="page-label" style="margin:20px 0 10px">
            ${t("dash.energy.controls", "Charge & hybrid")}
          </h2>
          ${familySections(rest)}
        `}
  `;
}
