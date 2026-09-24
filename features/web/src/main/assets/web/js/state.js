export const state = {
  status: null,
  controls: [],
  entities: [],
  setup: null,
  probe: null,
  lab: null,
  section: "home",
  token: "",
  showSetup: false,
  i18n: { locale: "pt-BR", locales: ["pt-BR", "en"], strings: {}, valueMaps: {} },
  adb: null,
  adbMessage: null,
  storeQuery: "",
  storeResults: [],
  storeDetail: null,
  storeBusy: false,
  storeMessage: null,
  historyEntities: [],
  historyPoints: null,
  historySelected: null,
  hiddenEntities: [],
};

export function entitiesByGroup(group) {
  return state.entities.filter(function (e) {
    return e.group === group;
  });
}

export function entitiesByType(type) {
  return state.entities.filter(function (e) {
    return e.entity === type;
  });
}

export function groupByEntity(list) {
  const map = {};
  list.forEach(function (e) {
    const k = e.entity || "extra";
    if (!map[k]) map[k] = [];
    map[k].push(e);
  });
  return map;
}
