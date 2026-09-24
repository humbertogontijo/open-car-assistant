import { api } from "./api.js";

export function theme() {
  return document.documentElement.getAttribute("data-theme") || "dark";
}

export function setTheme(t) {
  document.documentElement.setAttribute("data-theme", t);
  try {
    localStorage.setItem("oca_theme", t);
  } catch (e) {}
  api("/api/prefs", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "theme=" + encodeURIComponent(t),
  }).catch(function () {});
}
