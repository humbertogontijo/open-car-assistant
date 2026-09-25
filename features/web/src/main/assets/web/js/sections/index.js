import { t } from "../i18n.js";
import { sectionHome } from "./home.js";
import { sectionGroup } from "./group.js";
import { sectionSound, loadSounds } from "./sound.js";
import { sectionConnect } from "./connect.js";
import { sectionAssistant } from "./assistant.js";
import { sectionCameras, loadRecordings, startCameraLive, stopCameraLive, applyCameraPlayerSrc, isTimelineBusy } from "./cameras.js";
import { sectionHistory, loadHistoryPoints } from "./history.js";
import { sectionStore, ensureStoreLoaded } from "./store.js";
import { sectionSettings } from "./settings.js";
import { sectionLab } from "./lab.js";
import { sectionAbout } from "./about.js";
import { sectionShortcuts } from "./shortcuts.js";
import { sectionPlugins } from "./plugins.js";

export {
  sectionHome,
  sectionGroup,
  sectionSound,
  sectionConnect,
  sectionAssistant,
  sectionCameras,
  sectionHistory,
  sectionStore,
  sectionSettings,
  sectionLab,
  sectionAbout,
  sectionShortcuts,
  sectionPlugins,
  loadHistoryPoints,
  loadRecordings,
  loadSounds,
  startCameraLive,
  stopCameraLive,
  applyCameraPlayerSrc,
  ensureStoreLoaded,
  isTimelineBusy,
};

export function sectionView(section) {
  const map = {
    home: sectionHome,
    history: sectionHistory,
    controls: function () {
      return sectionGroup(t("section.controls.title", "Controles"), "", "controls");
    },
    drive: function () {
      return sectionGroup(t("section.drive.title", "Condução"), "", "drive");
    },
    energy: function () {
      return sectionGroup(t("section.energy.title", "Energia"), "", "energy");
    },
    lights: function () {
      return sectionGroup(t("section.lights.title", "Iluminação"), "", "lights");
    },
    adas: function () {
      return sectionGroup(t("section.adas.title", "ADAS"), "", "adas");
    },
    assistant: sectionAssistant,
    display: function () {
      return sectionGroup(t("section.display.title", "Tela"), "", "display");
    },
    sound: sectionSound,
    connect: sectionConnect,
    vehicle: function () {
      return sectionGroup(t("section.vehicle.title", "Meu Veículo"), "", "vehicle");
    },
    cameras: sectionCameras,
    dvr: sectionCameras,
    store: sectionStore,
    shortcuts: sectionShortcuts,
    plugins: sectionPlugins,
    settings: sectionSettings,
    // Legacy deep-links / quick-entry
    system: sectionSettings,
    climate: function () {
      return sectionGroup(t("section.controls.title", "Controles"), "", "controls");
    },
    cabin: function () {
      return sectionGroup(t("section.controls.title", "Controles"), "", "controls");
    },
    safety: function () {
      return sectionGroup(t("section.adas.title", "ADAS"), "", "adas");
    },
    android: sectionConnect,
    lab: sectionLab,
    about: sectionAbout,
  };
  const fn = map[section] || sectionHome;
  return fn();
}
