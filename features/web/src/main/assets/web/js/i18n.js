import { api } from "./api.js";
import { state } from "./state.js";

export function t(key, fallback) {
  const strings = (state.i18n && state.i18n.strings) || {};
  if (strings[key] != null) return strings[key];
  return fallback != null ? fallback : key;
}

export function valueLabel(mapId, raw) {
  if (raw == null) return "";
  const maps = (state.i18n && state.i18n.valueMaps) || {};
  const key = maps[mapId] && maps[mapId][String(raw)];
  if (key) return t(key, String(raw));
  return String(raw);
}

export async function loadI18n() {
  state.i18n = await api("/api/i18n");
  applyChrome();
  return state.i18n;
}

export async function setLocale(locale) {
  const body = new URLSearchParams({ locale: locale });
  const res = await api("/api/locale", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
  state.i18n = {
    locale: res.locale,
    locales: (state.i18n && state.i18n.locales) || ["pt-BR", "en"],
    strings: res.strings || {},
    valueMaps: res.valueMaps || {},
  };
  try {
    localStorage.setItem("oca_locale", res.locale);
  } catch (e) {}
  document.documentElement.lang = res.locale || "pt-BR";
  applyChrome();
  return state.i18n;
}

export function applyChrome() {
  document.querySelectorAll(".nav-item[data-sec]").forEach(function (el) {
    const sec = el.getAttribute("data-sec");
    const label = el.querySelector(".label");
    if (label) label.textContent = t("nav." + sec, label.textContent);
  });
}
