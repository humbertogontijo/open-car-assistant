export {
  sectionShortcuts,
  loadShortcuts,
  quickEntryCard,
} from "./sections/shortcuts.js";
import { quickEntryCard } from "./sections/shortcuts.js";
import { render } from "./lit.js";

/** @deprecated String HTML for sections.js until System migrates to lit. */
export function quickEntryCardHtml() {
  const el = document.createElement("div");
  render(quickEntryCard(), el);
  return el.innerHTML;
}

/** @deprecated Handlers live in lit templates. */
export function bindShortcuts() {}
