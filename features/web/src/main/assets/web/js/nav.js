import { state } from "./store.js";

/** In-session scroll positions by section (no localStorage — resets on process kill). */
const scrollByPage = Object.create(null);

export function stashCurrentScroll() {
  const main = document.getElementById("main");
  if (!main || !state.page) return;
  scrollByPage[state.page] = main.scrollTop || 0;
}

export function rememberScroll(sec, scrollTop) {
  if (!sec) return;
  scrollByPage[sec] = scrollTop || 0;
}

export function getScroll(sec) {
  const n = scrollByPage[sec || state.page];
  return typeof n === "number" ? n : 0;
}

export function restorePageScroll(sec) {
  const main = document.getElementById("main");
  if (!main) return;
  main.scrollTop = getScroll(sec || state.page);
}
