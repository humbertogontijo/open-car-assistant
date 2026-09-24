import { t } from "../i18n.js";
import { sectionHome } from "./home.js";
import { sectionGroup } from "./group.js";
import { sectionCabin, loadSounds } from "./cabin.js";
import { sectionCameras, loadRecordings, startCameraLive, stopCameraLive, applyCameraPlayerSrc } from "./cameras.js";
import { sectionHistory, loadHistoryPoints } from "./history.js";
import { sectionStore, ensureStoreLoaded } from "./store.js";
import { sectionSettings } from "./settings.js";
import { sectionAndroid } from "./android.js";
import { sectionLab } from "./lab.js";
import { sectionAbout } from "./about.js";
import { sectionShortcuts } from "./shortcuts.js";
import { sectionPlugins } from "./plugins.js";

export {
  sectionHome,
  sectionGroup,
  sectionCabin,
  sectionCameras,
  sectionHistory,
  sectionStore,
  sectionSettings,
  sectionAndroid,
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
};

export function sectionView(section) {
  const map = {
    home: sectionHome,
    history: sectionHistory,
    drive: function () {
      return sectionGroup(t("section.drive.title", "Condução"), "", "drive");
    },
    climate: function () {
      return sectionGroup(t("section.climate.title", "Clima"), "", "climate");
    },
    energy: function () {
      return sectionGroup(t("section.energy.title", "Energia"), "", "energy");
    },
    safety: function () {
      return sectionGroup(t("section.safety.title", "Segurança"), "", "safety");
    },
    cabin: sectionCabin,
    cameras: sectionCameras,
    dvr: sectionCameras,
    store: sectionStore,
    shortcuts: sectionShortcuts,
    plugins: sectionPlugins,
    android: sectionAndroid,
    settings: sectionSettings,
    // Legacy deep-links / quick-entry
    system: sectionSettings,
    lab: sectionLab,
    about: sectionAbout,
  };
  const fn = map[section] || sectionHome;
  return fn();
}
