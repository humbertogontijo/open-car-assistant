export {
  pageShortcuts,
  loadShortcuts,
  quickEntryCard,
} from "./pages/shortcuts.js";
import { quickEntryCard } from "./pages/shortcuts.js";
import { render } from "./lit.js";

/** Render quick-entry card to an HTML string (System settings embed). */
export function quickEntryCardHtml() {
  const el = document.createElement("div");
  render(quickEntryCard(), el);
  return el.innerHTML;
}

/** @deprecated Handlers live in lit templates. */
export function bindShortcuts() {}
