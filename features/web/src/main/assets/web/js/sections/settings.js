import { quickEntryCard } from "./shortcuts.js";
import { prefCard, prefSegment } from "../ui/cards.js";
import { theme } from "../theme.js";
import { html } from "../lit.js";
import { state, patch } from "../store.js";
import { t } from "../i18n.js";
import { api } from "../api.js";
import { unitPrefs, UNIT_CHOICES } from "../units.js";

function unitChoiceOpts(dim) {
  return (UNIT_CHOICES[dim] || []).map(function (id) {
    return { value: id, label: t("unit." + id, id) };
  });
}

function unitDimensionCards() {
  const prefs = unitPrefs();
  const dims = [
    {
      key: "temperature",
      icon: "temp",
      title: t("units.dim.temperature", "Temperature"),
    },
    {
      key: "distance",
      icon: "drive",
      title: t("units.dim.distance", "Distance"),
    },
    {
      key: "speed",
      icon: "drive",
      title: t("units.dim.speed", "Speed"),
    },
    {
      key: "fuel_economy",
      icon: "energy",
      title: t("units.dim.fuel", "Fuel economy"),
    },
    {
      key: "energy_economy",
      icon: "battery",
      title: t("units.dim.energy", "Energy economy"),
    },
  ];

  return dims.map(function (d) {
    return prefCard({
      icon: d.icon,
      title: d.title,
      body: prefSegment("unit_" + d.key, unitChoiceOpts(d.key), prefs[d.key], {
        choiceKey: "unit:" + d.key,
      }),
    });
  });
}

export function sectionSettings() {
  const th = theme();
  const locale = (state.i18n && state.i18n.locale) || "pt-BR";
  const locales = (state.i18n && state.i18n.locales) || ["pt-BR", "en"];

  const themeOpts = [
    { value: "dark", label: t("theme.dark", "Dark") },
    { value: "light", label: t("theme.light", "Light") },
    { value: "contrast", label: t("theme.contrast", "Contrast") },
  ];
  const localeOpts = locales.map(function (loc) {
    return {
      value: loc,
      label: loc === "pt-BR" ? t("locale.pt-BR", "Português") : t("locale." + loc, loc),
    };
  });

  return html`
    <h1>${t("nav.settings", "Settings")}</h1>
    <div class="grid">
      ${quickEntryCard()}
      ${prefCard({
        icon: "system",
        title: t("prefs.theme", "Tema"),
        body: prefSegment("theme", themeOpts, th),
      })}
      ${prefCard({
        icon: "about",
        title: t("prefs.locale", "Idioma"),
        body: prefSegment("locale", localeOpts, locale),
      })}
      ${unitDimensionCards()}
      ${prefCard({
        icon: "system",
        title: t("prefs.setup", "Setup"),
        body: html`<div class="row" style="width:100%;margin:0">
          <button
            class="btn"
            style="flex:1"
            @click=${function () {
              patch({ showSetup: true });
            }}
          >
            ${t("setup.open", "Abrir setup")}
          </button>
          <button
            class="btn ghost"
            style="flex:1"
            @click=${async function () {
              const setup = await api("/api/setup", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "reset=1",
              });
              patch({ setup: setup, showSetup: true });
            }}
          >
            ${t("setup.reset", "Resetar setup")}
          </button>
        </div>`,
      })}
    </div>
  `;
}
