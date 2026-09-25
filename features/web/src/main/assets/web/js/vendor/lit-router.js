// router-pkg/routes.js
var t = /* @__PURE__ */ new WeakMap();
var s = (s2) => {
  if (((t2) => void 0 !== t2.pattern)(s2)) return s2.pattern;
  let i3 = t.get(s2);
  return void 0 === i3 && t.set(s2, i3 = new URLPattern({ pathname: s2.path })), i3;
};
var i = class {
  constructor(t2, s2, i3) {
    this.routes = [], this.o = [], this.t = {}, this.i = (t3) => {
      if (t3.routes === this) return;
      const s3 = t3.routes;
      this.o.push(s3), s3.h = this, t3.stopImmediatePropagation(), t3.onDisconnect = () => {
        var _a;
        (_a = this.o) == null ? void 0 : _a.splice(this.o.indexOf(s3) >>> 0, 1);
      };
      const i4 = o(this.t);
      void 0 !== i4 && s3.goto(i4);
    }, (this.l = t2).addController(this), this.routes = [...s2], this.fallback = i3 == null ? void 0 : i3.fallback;
  }
  link(t2) {
    var _a;
    if (t2 == null ? void 0 : t2.startsWith("/")) return t2;
    if (t2 == null ? void 0 : t2.startsWith(".")) throw Error("Not implemented");
    return t2 ?? (t2 = this.u), (((_a = this.h) == null ? void 0 : _a.link()) ?? "") + t2;
  }
  async goto(t2) {
    let i3;
    if (0 === this.routes.length && void 0 === this.fallback) i3 = t2, this.u = "", this.t = { 0: i3 };
    else {
      const e2 = this.p(t2);
      if (void 0 === e2) throw Error("No route found for " + t2);
      const h = s(e2).exec({ pathname: t2 }), n = (h == null ? void 0 : h.pathname.groups) ?? {};
      if (i3 = o(n), "function" == typeof e2.enter && false === await e2.enter(n)) return;
      this.v = e2, this.t = n, this.u = void 0 === i3 ? t2 : t2.substring(0, t2.length - i3.length);
    }
    if (void 0 !== i3) for (const t3 of this.o) t3.goto(i3);
    this.l.requestUpdate();
  }
  outlet() {
    var _a, _b;
    return (_b = (_a = this.v) == null ? void 0 : _a.render) == null ? void 0 : _b.call(_a, this.t);
  }
  get params() {
    return this.t;
  }
  p(t2) {
    const i3 = this.routes.find((i4) => s(i4).test({ pathname: t2 }));
    return i3 || void 0 === this.fallback ? i3 : this.fallback ? { ...this.fallback, path: "/*" } : void 0;
  }
  hostConnected() {
    this.l.addEventListener(e.eventName, this.i);
    const t2 = new e(this);
    this.l.dispatchEvent(t2), this._ = t2.onDisconnect;
  }
  hostDisconnected() {
    var _a;
    (_a = this._) == null ? void 0 : _a.call(this), this.h = void 0;
  }
};
var o = (t2) => {
  let s2;
  for (const i3 of Object.keys(t2)) /\d+/.test(i3) && (void 0 === s2 || i3 > s2) && (s2 = i3);
  return s2 && t2[s2];
};
var e = class _e extends Event {
  constructor(t2) {
    super(_e.eventName, { bubbles: true, composed: true, cancelable: false }), this.routes = t2;
  }
};
e.eventName = "lit-routes-connected";

// router-pkg/router.js
var o2 = location.origin || location.protocol + "//" + location.host;
var i2 = class extends i {
  constructor() {
    super(...arguments), this.m = (t2) => {
      const i3 = 0 !== t2.button || t2.metaKey || t2.ctrlKey || t2.shiftKey;
      if (t2.defaultPrevented || i3) return;
      const s2 = t2.composedPath().find((t3) => "A" === t3.tagName);
      if (void 0 === s2 || "" !== s2.target || s2.hasAttribute("download") || "external" === s2.getAttribute("rel")) return;
      const n = s2.href;
      if ("" === n || n.startsWith("mailto:")) return;
      const e2 = window.location;
      s2.origin === o2 && (t2.preventDefault(), n !== e2.href && (window.history.pushState({}, "", n), this.goto(s2.pathname)));
    }, this.R = (t2) => {
      this.goto(window.location.pathname);
    };
  }
  hostConnected() {
    super.hostConnected(), window.addEventListener("click", this.m), window.addEventListener("popstate", this.R), this.goto(window.location.pathname);
  }
  hostDisconnected() {
    super.hostDisconnected(), window.removeEventListener("click", this.m), window.removeEventListener("popstate", this.R);
  }
};
export {
  i2 as Router,
  i as Routes,
  e as RoutesConnectedEvent
};
/**
 * @license
 * Copyright 2021 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */
