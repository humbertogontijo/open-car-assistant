/**
 * Path-based section router (@lit-labs/router) for lit-html (no LitElement).
 * Ensures URLPattern polyfill on Chrome < 95 (IVI is Chrome 83).
 */
import { URLPattern as URLPatternPolyfill } from "./vendor/urlpattern.js";
import { Router } from "./vendor/lit-router.js";
import { sectionView } from "./sections/index.js";
import {
  SECTION_IDS,
  sectionPath,
  pathToSection,
  isKnownSection,
} from "./sections/ids.js";

if (typeof globalThis.URLPattern === "undefined") {
  globalThis.URLPattern = URLPatternPolyfill;
}

/**
 * Minimal ReactiveControllerHost + EventTarget for @lit-labs/router.
 * Controllers connect only after `connect()` so enter handlers can be wired first.
 */
class AppRouterHost extends EventTarget {
  constructor() {
    super();
    this._controllers = new Set();
    this._onUpdate = null;
    this._connected = false;
    this._updateComplete = Promise.resolve(true);
  }

  set onUpdate(fn) {
    this._onUpdate = fn;
  }

  addController(controller) {
    this._controllers.add(controller);
    if (this._connected && controller.hostConnected) {
      controller.hostConnected();
    }
  }

  removeController(controller) {
    this._controllers.delete(controller);
  }

  requestUpdate() {
    if (typeof this._onUpdate === "function") this._onUpdate();
  }

  get updateComplete() {
    return this._updateComplete;
  }

  connect() {
    if (this._connected) return;
    this._connected = true;
    this._controllers.forEach(function (c) {
      if (c.hostConnected) c.hostConnected();
    });
  }
}

/** @type {null | ((sec: string, prev: string) => void | Promise<void>)} */
let enterHandler = null;

/** @type {string} */
let lastSection = "home";

export function setRouteEnterHandler(fn) {
  enterHandler = fn;
}

function routeConfig(sec) {
  var path = sectionPath(sec);
  return {
    name: sec,
    path: path,
    enter: function () {
      var prev = lastSection;
      lastSection = sec;
      if (enterHandler) return enterHandler(sec, prev);
    },
    render: function () {
      return sectionView(sec);
    },
  };
}

var host = new AppRouterHost();

var routeList = SECTION_IDS.filter(function (id) {
  return id !== "home";
}).map(routeConfig);

routeList.unshift(routeConfig("home"));

export const router = new Router(host, routeList, {
  fallback: routeConfig("home"),
});

/** Start listening to clicks / popstate and resolve the current path. */
export function startRouter(onUpdate) {
  if (onUpdate) host.onUpdate = onUpdate;
  // Avoid treating the initial resolve as a leave from the default "home".
  lastSection = pathToSection(window.location.pathname || "/");
  host.connect();
  return router;
}

/** Navigate to a section; updates history when the path changes. */
export function gotoSection(sec, options) {
  if (!isKnownSection(sec)) sec = "home";
  var path = sectionPath(sec);
  var replace = options && options.replace;
  var current = window.location.pathname || "/";
  if (current.length > 1 && current.charAt(current.length - 1) === "/") {
    current = current.slice(0, -1) || "/";
  }
  if (path !== current) {
    if (replace) {
      window.history.replaceState({}, "", path);
    } else {
      window.history.pushState({}, "", path);
    }
  }
  return router.goto(path);
}

/** One-shot `?section=` → path redirect before the router connects. */
export function applyLegacySectionQuery() {
  try {
    var params = new URLSearchParams(window.location.search || "");
    var sec = params.get("section");
    if (!sec || !isKnownSection(sec)) return;
    var path = sectionPath(sec);
    window.history.replaceState({}, "", path);
  } catch (e) {}
}

export { sectionPath, pathToSection, isKnownSection, SECTION_IDS };
