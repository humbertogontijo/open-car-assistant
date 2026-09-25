import { t } from "../i18n.js";
import { sectionGroup } from "./group.js";

export function sectionAssistant() {
  return sectionGroup(t("section.assistant.title", "Assistente"), "", "assistant");
}
