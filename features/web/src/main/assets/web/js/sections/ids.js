/**
 * Canonical section ids for routing + SPA fallback allowlist.
 * Keep in sync with sectionView map in index.js.
 */
export const SECTION_IDS = [
  "home",
  "history",
  "controls",
  "drive",
  "energy",
  "lights",
  "adas",
  "assistant",
  "display",
  "sound",
  "android",
  "connect",
  "vehicle",
  "cameras",
  "dvr",
  "store",
  "shortcuts",
  "plugins",
  "settings",
  "system",
  "climate",
  "cabin",
  "safety",
  "lab",
  "about",
];

const SECTION_SET = new Set(SECTION_IDS);

export function isKnownSection(id) {
  return !!id && SECTION_SET.has(id);
}

/** Pathname for a section id (`home` → `/`). */
export function sectionPath(sec) {
  if (!sec || sec === "home") return "/";
  return "/" + sec;
}

/** Section id from pathname (`/cameras` → `cameras`). Unknown → `home`. */
export function pathToSection(pathname) {
  var path = pathname || "/";
  if (path.length > 1 && path.charAt(path.length - 1) === "/") {
    path = path.slice(0, -1);
  }
  if (path === "/" || path === "") return "home";
  var id = path.slice(1).split("/")[0];
  return isKnownSection(id) ? id : "home";
}
