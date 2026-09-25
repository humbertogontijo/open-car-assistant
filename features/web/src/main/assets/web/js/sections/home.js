import { html } from "../lit.js";
import { state, hiddenEntitiesByGroup, isShowingHidden } from "../store.js";
import { t } from "../i18n.js";
import { entityGrid, pageHead } from "../ui/cards.js";

export function sectionHome() {
  // Dashboard sensors only (group=home). Writable drive_mode / regen live under Condução.
  const viewing = isShowingHidden("home");
  const items = viewing
    ? hiddenEntitiesByGroup("home").filter(function (e) {
        return e.entity === "sensor";
      })
    : state.entities.filter(function (e) {
        return e.group === "home" && e.entity === "sensor" && e.status === "ok";
      });
  return html`
    ${pageHead(t("section.home.title", "Início"), "home")}
    ${entityGrid(items, viewing ? { restore: true } : null)}
  `;
}
