import { state } from "./store.js";

/** In-session scroll positions by section (no localStorage — resets on process kill). */
const scrollBySection = Object.create(null);

export function stashCurrentScroll() {
  const main = document.getElementById("main");
  if (!main || !state.section) return;
  scrollBySection[state.section] = main.scrollTop || 0;
}

export function rememberScroll(sec, scrollTop) {
  if (!sec) return;
  scrollBySection[sec] = scrollTop || 0;
}

export function getScroll(sec) {
  const n = scrollBySection[sec || state.section];
  return typeof n === "number" ? n : 0;
}

export function restoreSectionScroll(sec) {
  const main = document.getElementById("main");
  if (!main) return;
  main.scrollTop = getScroll(sec || state.section);
}
