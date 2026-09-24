import { fmt } from "./api.js";
import { t } from "./i18n.js";
import { iconSvg } from "./icons.js";

function esc(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/"/g, "&quot;");
}

/** Localized unit glyph from `unitLabel`. */
function displayUnit(c) {
  return c.unitLabel || "";
}

function isOn(v) {
  return v === "1" || v === "true" || v === true || v === 1 || v === "on";
}

function optionIndex(opts, val) {
  const i = opts.findIndex(function (o) {
    return String(o.value) === String(val);
  });
  return i >= 0 ? i : 0;
}

function nextOptionValue(opts, val) {
  if (!opts.length) return val;
  const i = optionIndex(opts, val);
  return opts[(i + 1) % opts.length].value;
}

function optionLabel(opts, val) {
  const o = opts.find(function (x) {
    return String(x.value) === String(val);
  });
  return o ? o.label : fmt(val);
}

function encodeOpts(opts) {
  return opts
    .map(function (o) {
      return encodeURIComponent(String(o.value)) + "=" + encodeURIComponent(String(o.label));
    })
    .join("&");
}

export function decodeOpts(raw) {
  if (!raw) return [];
  return String(raw)
    .split("&")
    .filter(Boolean)
    .map(function (pair) {
      const i = pair.indexOf("=");
      if (i < 0) return { value: decodeURIComponent(pair), label: decodeURIComponent(pair) };
      return {
        value: decodeURIComponent(pair.slice(0, i)),
        label: decodeURIComponent(pair.slice(i + 1)),
      };
    });
}

export function cycleNext(opts, val) {
  return nextOptionValue(opts, val);
}

/**
 * Segmented toggle for ≤4 options. Use choiceSelectHtml when there are more.
 * pinnedVal (optional): reboot snapshot — marks that option with .pin-mark.
 */
export function segmentToggleHtml(opts, current, idAttr, locked, pinnedVal) {
  const list = opts || [];
  if (!list.length) return "";
  if (list.length > 4) {
    return choiceSelectHtml(list, current, idAttr, locked, pinnedVal);
  }
  const hasCurrent = current != null && current !== "";
  const hasPin = pinnedVal != null && pinnedVal !== "";
  const pinTitle = t("persist.back_hint", "Applied only after the car restarts");
  return (
    '<div class="toggle-group' +
    (hasCurrent ? "" : " unset") +
    '" role="group" data-count="' +
    list.length +
    '">' +
    list
      .map(function (o) {
        const active = hasCurrent && String(current) === String(o.value);
        const pinned = hasPin && String(pinnedVal) === String(o.value);
        return (
          '<button type="button" class="toggle-seg' +
          (active ? " active" : "") +
          (pinned ? " pin-mark" : "") +
          '" ' +
          (idAttr || "") +
          ' data-val="' +
          esc(o.value) +
          '"' +
          (locked ? " disabled" : "") +
          (active ? ' aria-pressed="true"' : ' aria-pressed="false"') +
          (pinned ? ' title="' + esc(pinTitle) + '"' : "") +
          ">" +
          esc(o.label) +
          "</button>"
        );
      })
      .join("") +
    "</div>"
  );
}

/** Styled dropdown for >4 options — same chrome / pin language as steppers. */
export function choiceSelectHtml(opts, current, idAttr, locked, pinnedVal) {
  const list = opts || [];
  if (!list.length) return "";
  const hasCurrent = current != null && current !== "";
  const hasPin = pinnedVal != null && pinnedVal !== "";
  const pinTitle = t("persist.back_hint", "Applied only after the car restarts");
  const match = hasPin && hasCurrent && String(current) === String(pinnedVal);
  const label = hasCurrent
    ? optionLabel(list, current)
    : t("persist.pick_short", "Select…");
  const rootClass =
    "choice-select" +
    (hasPin ? (match ? " pin-match" : " pin-diff") : "") +
    (hasCurrent ? "" : " unset");
  const chevron =
    '<svg class="choice-chevron" viewBox="0 0 24 24" aria-hidden="true">' +
    '<path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" stroke-width="1.8" ' +
    'stroke-linecap="round" stroke-linejoin="round"/></svg>';
  const options = list
    .map(function (o) {
      const active = hasCurrent && String(current) === String(o.value);
      const pinned = hasPin && String(pinnedVal) === String(o.value);
      return (
        '<button type="button" class="choice-opt' +
        (active ? " active" : "") +
        (pinned ? " pin-mark" : "") +
        '" role="option" ' +
        (idAttr || "") +
        ' data-val="' +
        esc(o.value) +
        '"' +
        (locked ? " disabled" : "") +
        (active ? ' aria-selected="true"' : ' aria-selected="false"') +
        (pinned ? ' title="' + esc(pinTitle) + '"' : "") +
        ">" +
        esc(o.label) +
        "</button>"
      );
    })
    .join("");
  const pinChip =
    hasPin && !match ? pinChipHtml(optionLabel(list, pinnedVal)) : "";
  return (
    '<div class="' +
    rootClass +
    '" data-choice-select>' +
    '<button type="button" class="choice-trigger" data-choice-trigger' +
    (locked ? " disabled" : "") +
    ' aria-haspopup="listbox" aria-expanded="false">' +
    '<span class="choice-label">' +
    esc(label) +
    "</span>" +
    chevron +
    '</button><div class="choice-menu" role="listbox" hidden>' +
    options +
    "</div>" +
    pinChip +
    "</div>"
  );
}

/** Shared Off/On options for bool entity inputs and pref toggles. */
export function boolOpts() {
  return [
    { value: "0", label: t("value.off", "Off") },
    { value: "1", label: t("value.on", "On") },
  ];
}

export function boolVal(val) {
  return isOn(val) ? "1" : "0";
}

/** Segmented Off/On control — same chrome as entity bool cards. */
export function boolToggleHtml(current, idAttr, locked, pinnedVal) {
  return segmentToggleHtml(boolOpts(), boolVal(current), idAttr, locked, pinnedVal);
}

/** Normalized pin snapshot for highlighting, or null if none. */
function pinSnapshot(c) {
  if (!c || !c.persistEnabled) return null;
  if (c.persistValue == null || c.persistValue === "") return null;
  const input = c.input || "int";
  if (input === "bool") return boolVal(c.persistValue);
  return String(c.persistValue);
}

function pinChipHtml(label) {
  return (
    '<span class="pin-chip" title="' +
    esc(t("persist.back_hint", "Applied only after the car restarts")) +
    '">' +
    iconSvg("pin") +
    esc(label) +
    "</span>"
  );
}

function inputWidget(c) {
  const locked = c.status !== "ok" && c.status !== "cached";
  const val = c.value;
  const id = c.id;
  const input = c.input || "int";
  const idAttr = 'data-ctrl="' + esc(id) + '"';
  const pin = pinSnapshot(c);

  if (input === "bool") {
    return boolToggleHtml(val, idAttr, locked, pin);
  }

  if (input === "choice") {
    return segmentToggleHtml(
      c.options || [],
      val,
      idAttr,
      locked,
      pin,
    );
  }

  if (input === "int" || input === "float") {
    const step = c.step != null ? c.step : input === "float" ? 0.5 : 1;
    const num = parseFloat(val);
    const display = isNaN(num)
      ? "—"
      : input === "float"
        ? String(num)
        : String(Math.round(num));
    const pinNum = pin != null ? parseFloat(pin) : NaN;
    const match = pin != null && !isNaN(num) && !isNaN(pinNum) && num === pinNum;
    const pinClass = pin == null ? "" : match ? " pin-match" : " pin-diff";
    const pinLabel =
      pin == null || match
        ? ""
        : isNaN(pinNum)
          ? String(pin)
          : input === "float"
            ? String(pinNum)
            : String(Math.round(pinNum));
    return (
      '<div class="stepper' +
      pinClass +
      '">' +
      '<button type="button" data-step="' +
      esc(id) +
      '" data-delta="-' +
      step +
      '"' +
      (locked ? " disabled" : "") +
      ">−</button>" +
      '<span class="val" data-step-val="' +
      esc(id) +
      '">' +
      esc(display) +
      (displayUnit(c) && !isNaN(num) ? '<span class="unit">' + esc(displayUnit(c)) + "</span>" : "") +
      "</span>" +
      '<button type="button" data-step="' +
      esc(id) +
      '" data-delta="' +
      step +
      '"' +
      (locked ? " disabled" : "") +
      ">+</button>" +
      (pinClass === " pin-diff"
        ? pinChipHtml(pinLabel + (displayUnit(c) ? " " + displayUnit(c) : ""))
        : "") +
      "</div>"
    );
  }

  if (input === "text") {
    const match = pin != null && String(val || "") === String(pin);
    return (
      '<div class="select-wrap' +
      (pin == null ? "" : match ? " pin-match" : " pin-diff") +
      '"><input type="text" class="field" data-ctrl-text="' +
      esc(id) +
      '" value="' +
      esc(val || "") +
      '"' +
      (locked ? " disabled" : "") +
      ">" +
      (pin != null && !match ? pinChipHtml(String(pin)) : "") +
      "</div>"
    );
  }

  return (
    '<span class="mono">' +
    esc(c.valueLabel || fmt(val)) +
    (displayUnit(c) ? " " + esc(displayUnit(c)) : "") +
    "</span>"
  );
}

/** Pref / system card using same shell as vehicle controls. */
export function prefCard(opts) {
  const icon = opts.icon || "system";
  const body = opts.bodyHtml || "";
  const sub = opts.sub
    ? '<p class="hint">' + esc(opts.sub) + "</p>"
    : "";
  return (
    '<div class="ctrl-card pref-card">' +
    '<div class="ctrl-head"><div class="ctrl-icon">' +
    iconSvg(icon) +
    '</div><div class="ctrl-meta"><h3>' +
    esc(opts.title) +
    "</h3>" +
    sub +
    '</div></div><div class="ctrl-body">' +
    body +
    "</div></div>"
  );
}

function hideBtnHtml(id) {
  const title = t("entity.hide", "Hide card");
  return (
    '<button type="button" class="hide-btn" data-entity-hide="' +
    esc(id) +
    '" title="' +
    esc(title) +
    '" aria-label="' +
    esc(title) +
    '">' +
    iconSvg("hide") +
    "</button>"
  );
}

function controlCard(c) {
  if (c.input === "sensor") {
    if (c.status !== "ok") return "";
    return (
      '<div class="ctrl-card sensor-card" data-card="' +
      esc(c.id) +
      '">' +
      '<div class="ctrl-head"><div class="ctrl-icon">' +
      iconSvg(c.icon || "sensor") +
      '</div><div class="ctrl-meta"><h3>' +
      esc(c.label) +
      "</h3>" +
      (c.hint || c.description
        ? '<p class="hint">' + esc(c.hint || c.description) + "</p>"
        : "") +
      '</div><div class="card-actions">' +
      hideBtnHtml(c.id) +
      "</div></div>" +
      '<div class="ctrl-body"><div class="entity-value">' +
      esc(c.valueLabel || fmt(c.value)) +
      (displayUnit(c) ? '<span class="unit">' + esc(displayUnit(c)) + "</span>" : "") +
      "</div></div></div>"
    );
  }

  const locked = c.status !== "ok" && c.status !== "cached";
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const hasValue = c.value != null && c.value !== "";

  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");
  const pinBtn =
    '<button type="button" class="pin-btn' +
    (pinned ? " active" : "") +
    '" data-persist-pin="' +
    esc(c.id) +
    '" title="' +
    esc(pinTitle) +
    '" aria-label="' +
    esc(pinTitle) +
    '" aria-pressed="' +
    (pinned ? "true" : "false") +
    '"' +
    (!pinned && !hasValue ? " disabled" : "") +
    ">" +
    iconSvg("pin") +
    "</button>";

  const metaBits =
    (c.hint ? '<p class="hint">' + esc(c.hint) + "</p>" : "") +
    (c.acronym ? '<span class="badge acronym">' + esc(c.acronym) + "</span>" : "");

  const notes = c.stale
    ? '<div class="lock-note">' + esc(t("status.cached", "Último conhecido")) + "</div>"
    : "";

  return (
    '<div class="ctrl-card' +
    (locked ? " locked" : "") +
    (pinned ? " pinned" : "") +
    '" data-card="' +
    esc(c.id) +
    '">' +
    '<div class="ctrl-head"><div class="ctrl-icon">' +
    iconSvg(c.icon || c.id) +
    '</div><div class="ctrl-meta"><h3>' +
    esc(c.label) +
    "</h3>" +
    metaBits +
    '</div><div class="card-actions">' +
    hideBtnHtml(c.id) +
    pinBtn +
    '</div></div><div class="ctrl-body">' +
    inputWidget(c) +
    notes +
    "</div></div>"
  );
}

export function renderEntityGrid(items) {
  if (!items || !items.length) {
    return '<p class="sub">' + t("empty.controls", "Nenhum controle neste grupo") + "</p>";
  }
  return '<div class="grid">' + items.map(controlCard).join("") + "</div>";
}

export function entityLabel(type) {
  return t("entity." + (type || "extra"), type || "Extra");
}
