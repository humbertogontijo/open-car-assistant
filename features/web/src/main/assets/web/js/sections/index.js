import { t } from "../i18n.js";
import { sectionHome } from "./home.js";
import { sectionGroup } from "./group.js";
import { sectionEnergy, loadEnergyDash } from "./energy.js";
import { sectionControls } from "./controls.js";
import { sectionDrive } from "./drive.js";
import { sectionSound, loadSounds } from "./sound.js";
import { sectionAndroid } from "./connect.js";
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
  sectionEnergy,
  sectionControls,
  sectionDrive,
  sectionSound,
  sectionAndroid,
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
  loadEnergyDash,
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
    controls: sectionControls,
    drive: sectionDrive,
    energy: sectionEnergy,
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
    android: sectionAndroid,
    // Legacy nav / deep-link id
    connect: sectionAndroid,
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
    climate: sectionControls,
    cabin: sectionControls,
    safety: function () {
      return sectionGroup(t("section.adas.title", "ADAS"), "", "adas");
    },
    lab: sectionLab,
    about: sectionAbout,
  };
  const fn = map[section] || sectionHome;
  return fn();
}
