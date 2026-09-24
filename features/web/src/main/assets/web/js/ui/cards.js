import { html, nothing, repeat, unsafeHTML, classMap } from "../lit.js";
import { fmt } from "../api.js";
import { t } from "../i18n.js";
import { iconSvg } from "../icons.js";
import { state, patch, findControl } from "../store.js";
import { faceValue } from "../persist.js";
import {
  setControl,
  setPersist,
  hideEntity,
  runPref,
} from "../actions.js";
import {
  unitLabelFor,
  formatDisplayNumber,
} from "../units.js";

function icon(name) {
  return unsafeHTML(iconSvg(name || "sensor"));
}

function displayUnit(c) {
  return unitLabelFor(c);
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

export function boolOpts() {
  return [
    { value: "0", label: t("value.off", "Off") },
    { value: "1", label: t("value.on", "On") },
  ];
}

export function boolVal(val) {
  return isOn(val) ? "1" : "0";
}

function pinSnapshot(c) {
  if (!c || !c.persistEnabled) return null;
  if (c.persistValue == null || c.persistValue === "") return null;
  const input = c.input || "int";
  if (input === "bool") return boolVal(c.persistValue);
  return String(c.persistValue);
}

function pinChip(label) {
  const title = t("persist.back_hint", "Applied only after the car restarts");
  return html`<span class="pin-chip" title=${title}>${icon("pin")}${label}</span>`;
}

function closeChoices() {
  if (state.openChoiceId != null) patch({ openChoiceId: null });
}

if (!window.__ocaChoiceCloseBound) {
  window.__ocaChoiceCloseBound = true;
  document.addEventListener("click", function () {
    closeChoices();
  });
}

/**
 * @param {object} opts
 * @param {Array} opts.options
 * @param {*} opts.current
 * @param {boolean} [opts.locked]
 * @param {*} [opts.pinnedVal]
 * @param {function} opts.onSelect (value) => void
 * @param {string} [opts.choiceKey] stable id for open menu tracking
 */
export function segmentToggle(opts) {
  const list = opts.options || [];
  if (!list.length) return nothing;
  if (list.length > 4) {
    return choiceSelect(opts);
  }
  const current = opts.current;
  const locked = !!opts.locked;
  const pinnedVal = opts.pinnedVal;
  const hasCurrent = current != null && current !== "";
  const hasPin = pinnedVal != null && pinnedVal !== "";
  const pinTitle = t("persist.back_hint", "Applied only after the car restarts");
  return html`
    <div
      class="toggle-group ${hasCurrent ? "" : "unset"}"
      role="group"
      data-count=${list.length}
    >
      ${list.map(function (o) {
        const active = hasCurrent && String(current) === String(o.value);
        const pinned = hasPin && String(pinnedVal) === String(o.value);
        return html`
          <button
            type="button"
            class="toggle-seg ${active ? "active" : ""} ${pinned ? "pin-mark" : ""}"
            data-val=${o.value}
            ?disabled=${locked || !!o.disabled}
            aria-pressed=${active ? "true" : "false"}
            title=${o.disabled
              ? o.title || t("cameras.storage.unavailable", "Not available")
              : pinned
                ? pinTitle
                : nothing}
            @click=${function (ev) {
              ev.stopPropagation();
              if (locked || o.disabled) return;
              opts.onSelect(o.value);
            }}
          >
            ${o.label}
          </button>
        `;
      })}
    </div>
  `;
}

export function choiceSelect(opts) {
  const list = opts.options || [];
  if (!list.length) return nothing;
  const current = opts.current;
  const locked = !!opts.locked;
  const pinnedVal = opts.pinnedVal;
  const choiceKey = opts.choiceKey || "choice";
  const hasCurrent = current != null && current !== "";
  const hasPin = pinnedVal != null && pinnedVal !== "";
  const pinTitle = t("persist.back_hint", "Applied only after the car restarts");
  const match = hasPin && hasCurrent && String(current) === String(pinnedVal);
  const label = hasCurrent
    ? optionLabel(list, current)
    : t("persist.pick_short", "Select…");
  const open = state.openChoiceId === choiceKey;
  const rootClass = classMap({
    "choice-select": true,
    "pin-match": hasPin && match,
    "pin-diff": hasPin && !match,
    unset: !hasCurrent,
    open: open,
  });
  return html`
    <div
      class=${rootClass}
      data-choice-select
      @click=${function (ev) {
        ev.stopPropagation();
      }}
    >
      <button
        type="button"
        class="choice-trigger"
        ?disabled=${locked}
        aria-haspopup="listbox"
        aria-expanded=${open ? "true" : "false"}
        @click=${function (ev) {
          ev.stopPropagation();
          if (locked) return;
          patch({ openChoiceId: open ? null : choiceKey });
        }}
      >
        <span class="choice-label">${label}</span>
        <svg class="choice-chevron" viewBox="0 0 24 24" aria-hidden="true">
          <path
            d="M6 9l6 6 6-6"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <div class="choice-menu" role="listbox" ?hidden=${!open}>
        ${list.map(function (o) {
          const active = hasCurrent && String(current) === String(o.value);
          const pinned = hasPin && String(pinnedVal) === String(o.value);
          return html`
            <button
              type="button"
              class="choice-opt ${active ? "active" : ""} ${pinned ? "pin-mark" : ""}"
              role="option"
              data-val=${o.value}
              ?disabled=${locked || !!o.disabled}
              aria-selected=${active ? "true" : "false"}
              title=${o.disabled
                ? o.title || t("cameras.storage.unavailable", "Not available")
                : pinned
                  ? pinTitle
                  : nothing}
              @click=${function (ev) {
                ev.stopPropagation();
                if (locked || o.disabled) return;
                patch({ openChoiceId: null });
                opts.onSelect(o.value);
              }}
            >
              ${o.label}
            </button>
          `;
        })}
      </div>
      ${hasPin && !match ? pinChip(optionLabel(list, pinnedVal)) : nothing}
    </div>
  `;
}

export function boolToggle(current, onSelect, locked, pinnedVal, choiceKey) {
  return segmentToggle({
    options: boolOpts(),
    current: boolVal(current),
    locked: locked,
    pinnedVal: pinnedVal,
    choiceKey: choiceKey,
    onSelect: onSelect,
  });
}

/** Pref toggle that runs runPref(pref, value, el). */
export function prefSegment(pref, options, current, extra) {
  extra = extra || {};
  return segmentToggle({
    options: options,
    current: current,
    locked: !!extra.locked,
    pinnedVal: extra.pinnedVal,
    choiceKey: extra.choiceKey || "pref:" + pref,
    onSelect: function (val) {
      runPref(pref, val, extra);
    },
  });
}

export function prefBool(pref, current, extra) {
  return boolToggle(
    current,
    function (val) {
      runPref(pref, val, extra);
    },
    extra && extra.locked,
    extra && extra.pinnedVal,
    (extra && extra.choiceKey) || "pref:" + pref,
  );
}

function onStep(id, delta) {
  const c = findControl(id) || {};
  const curRaw = faceValue(c);
  let cur = parseFloat(curRaw);
  if (isNaN(cur)) cur = c.min != null ? Number(c.min) : 0;
  // Step in vehicle-native units; display layer converts for the UI.
  let next = cur + delta;
  if (delta < 0 && c.min != null) next = Math.max(Number(c.min), next);
  if (delta > 0 && c.max != null) next = Math.min(Number(c.max), next);
  if (delta > 0 && next < cur) next = cur;
  if (delta < 0 && next > cur) next = cur;
  if (next === cur) return;
  if (c.input === "int" || (c.step && Number(c.step) === 1)) next = Math.round(next);
  else next = Math.round(next * 10) / 10;
  setControl(id, String(next));
}

function inputWidget(c) {
  const locked = c.status !== "ok" && c.status !== "cached";
  const val = c.value;
  const id = c.id;
  const input = c.input || "int";
  const pin = pinSnapshot(c);

  if (input === "bool") {
    return boolToggle(
      val,
      function (v) {
        setControl(id, v);
      },
      locked,
      pin,
      "ctrl:" + id,
    );
  }

  if (input === "choice") {
    return segmentToggle({
      options: c.options || [],
      current: val,
      locked: locked,
      pinnedVal: pin,
      choiceKey: "ctrl:" + id,
      onSelect: function (v) {
        setControl(id, v);
      },
    });
  }

  if (input === "int" || input === "float") {
    const step = c.step != null ? c.step : input === "float" ? 0.5 : 1;
    const num = parseFloat(val);
    const unitId = c.unitOfMeasurement || null;
    const display = isNaN(num)
      ? "—"
      : formatDisplayNumber(unitId, num, input);
    const pinNum = pin != null ? parseFloat(pin) : NaN;
    const match = pin != null && !isNaN(num) && !isNaN(pinNum) && num === pinNum;
    const pinClass = pin == null ? "" : match ? " pin-match" : " pin-diff";
    const pinLabel =
      pin == null || match
        ? ""
        : isNaN(pinNum)
          ? String(pin)
          : formatDisplayNumber(unitId, pinNum, input);
    return html`
      <div class="stepper${pinClass}">
        <button type="button" ?disabled=${locked} @click=${function () {
          onStep(id, -step);
        }}>
          −
        </button>
        <span class="val" data-step-val=${id}>
          ${display}${displayUnit(c) && !isNaN(num)
            ? html`<span class="unit">${displayUnit(c)}</span>`
            : nothing}
        </span>
        <button type="button" ?disabled=${locked} @click=${function () {
          onStep(id, step);
        }}>
          +
        </button>
        ${pinClass === " pin-diff"
          ? pinChip(pinLabel + (displayUnit(c) ? " " + displayUnit(c) : ""))
          : nothing}
      </div>
    `;
  }

  if (input === "text") {
    const match = pin != null && String(val || "") === String(pin);
    return html`
      <div
        class="select-wrap ${pin == null ? "" : match ? "pin-match" : "pin-diff"}"
      >
        <input
          type="text"
          class="field"
          .value=${val || ""}
          ?disabled=${locked}
          @change=${function (ev) {
            setControl(id, ev.target.value);
          }}
        />
        ${pin != null && !match ? pinChip(String(pin)) : nothing}
      </div>
    `;
  }

  return html`<span class="mono"
    >${c.valueLabel ||
      (function () {
        const n = parseFloat(val);
        if (!isNaN(n) && c.unitOfMeasurement) {
          return formatDisplayNumber(c.unitOfMeasurement, n, input);
        }
        return fmt(val);
      })()}${displayUnit(c) ? " " + displayUnit(c) : ""}</span
  >`;
}

function hideBtn(id) {
  const title = t("entity.hide", "Hide card");
  return html`
    <button
      type="button"
      class="hide-btn"
      title=${title}
      aria-label=${title}
      @click=${function (ev) {
        ev.stopPropagation();
        hideEntity(id);
      }}
    >
      ${icon("hide")}
    </button>
  `;
}

function sensorDisplay(c) {
  if (c.valueLabel) return c.valueLabel;
  const n = parseFloat(c.value);
  if (!isNaN(n) && c.unitOfMeasurement) {
    return formatDisplayNumber(c.unitOfMeasurement, n, c.input || "sensor");
  }
  return fmt(c.value);
}

function controlCard(c) {
  if (c.input === "sensor") {
    if (c.status !== "ok") return nothing;
    return html`
      <div class="ctrl-card sensor-card" data-card=${c.id}>
        <div class="ctrl-head">
          <div class="ctrl-icon">${icon(c.icon || "sensor")}</div>
          <div class="ctrl-meta">
            <h3>${c.label}</h3>
            ${c.hint || c.description
              ? html`<p class="hint">${c.hint || c.description}</p>`
              : nothing}
          </div>
          <div class="card-actions">${hideBtn(c.id)}</div>
        </div>
        <div class="ctrl-body">
          <div class="entity-value">
            ${sensorDisplay(c)}${displayUnit(c)
              ? html`<span class="unit">${displayUnit(c)}</span>`
              : nothing}
          </div>
        </div>
      </div>
    `;
  }

  const locked = c.status !== "ok" && c.status !== "cached";
  const pinned = !!c.persistEnabled && pinSnapshot(c) != null;
  const hasValue = c.value != null && c.value !== "";
  const pinTitle = pinned
    ? t("persist.unpin", "Unpin reboot value")
    : t("persist.pin", "Pin current value for reboot");

  return html`
    <div
      class="ctrl-card ${locked ? "locked" : ""} ${pinned ? "pinned" : ""}"
      data-card=${c.id}
    >
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(c.icon || c.id)}</div>
        <div class="ctrl-meta">
          <h3>${c.label}</h3>
          ${c.hint ? html`<p class="hint">${c.hint}</p>` : nothing}
          ${c.acronym
            ? html`<span class="badge acronym">${c.acronym}</span>`
            : nothing}
        </div>
        <div class="card-actions">
          ${hideBtn(c.id)}
          <button
            type="button"
            class="pin-btn ${pinned ? "active" : ""}"
            title=${pinTitle}
            aria-label=${pinTitle}
            aria-pressed=${pinned ? "true" : "false"}
            ?disabled=${!pinned && !hasValue}
            @click=${function (ev) {
              ev.stopPropagation();
              if (pinned) {
                setPersist(c.id, { enabled: false });
                return;
              }
              if (c.value == null || c.value === "") return;
              setPersist(c.id, { enabled: true, value: String(c.value) });
            }}
          >
            ${icon("pin")}
          </button>
        </div>
      </div>
      <div class="ctrl-body">
        ${inputWidget(c)}
        ${c.stale
          ? html`<div class="lock-note">${t("status.cached", "Último conhecido")}</div>`
          : nothing}
      </div>
    </div>
  `;
}

export function entityGrid(items) {
  if (!items || !items.length) {
    return html`<p class="sub">${t("empty.controls", "Nenhum controle neste grupo")}</p>`;
  }
  return html`<div class="grid">
    ${repeat(
      items,
      function (c) {
        return c.id;
      },
      controlCard,
    )}
  </div>`;
}

export function entityLabel(type) {
  return t("entity." + (type || "extra"), type || "Extra");
}

/** Pref / system card shell. body is a TemplateResult or nothing. */
export function prefCard(opts) {
  return html`
    <div class="ctrl-card pref-card">
      <div class="ctrl-head">
        <div class="ctrl-icon">${icon(opts.icon || "system")}</div>
        <div class="ctrl-meta">
          <h3>${opts.title}</h3>
          ${opts.sub ? html`<p class="hint">${opts.sub}</p>` : nothing}
        </div>
      </div>
      <div class="ctrl-body">${opts.body || nothing}</div>
    </div>
  `;
}

/* ---- compatibility aliases used during migration ---- */
export function renderEntityGrid(items) {
  return entityGrid(items);
}
export function segmentToggleHtml() {
  return "";
}
export function choiceSelectHtml() {
  return "";
}
export function boolToggleHtml() {
  return "";
}
