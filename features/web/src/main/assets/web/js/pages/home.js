import { html, nothing } from "../lit.js";
import { state, hiddenEntitiesByGroup, isShowingHidden } from "../store.js";
import { t } from "../i18n.js";
import { entityGrid, pageHead } from "../ui/cards.js";
import { dashSummary, pickEntities } from "../ui/dashboard.js";

var HOME_HERO_IDS = [
  "sensor.soc",
  "sensor.range",
  "sensor.speed",
  "sensor.gear",
  "drivetrain.vehicle",
  "sensor.fuel",
];

export function pageHome() {
  // Dashboard sensors (group=home) + drivetrain composite.
  const viewing = isShowingHidden("home");
  const items = viewing
    ? hiddenEntitiesByGroup("home").filter(function (e) {
        return e.entity === "sensor" || e.domain === "sensor";
      })
    : state.entities.filter(function (e) {
        return e.group === "home" && (e.entity === "sensor" || e.domain === "sensor") && e.status === "ok";
      });
  const heroPool = viewing
    ? items
    : (state.entities || []).filter(function (e) {
        if (!(e.status === "ok" || e.status === "cached")) return false;
        if (e.id === "drivetrain.vehicle" || e.id === "drivetrain") return true;
        return e.group === "home" && (e.entity === "sensor" || e.domain === "sensor");
      });
  const hero = pickEntities(heroPool, HOME_HERO_IDS);
  const heroIds = {};
  hero.forEach(function (e) {
    heroIds[e.id] = true;
  });
  const rest = items.filter(function (e) {
    return !heroIds[e.id];
  });

  return html`
    ${pageHead(
      t("section.home.title", "Início"),
      "home",
      t("section.home.sub", "At a glance"),
    )}
    ${viewing
      ? entityGrid(items, { restore: true })
      : html`
          ${dashSummary(hero, { className: "dash-home" })}
          ${rest.length
            ? html`
                <h2 class="page-label" style="margin:20px 0 10px">
                  ${t("dash.home.more", "More sensors")}
                </h2>
                ${entityGrid(rest)}
              `
            : nothing}
        `}
  `;
}
