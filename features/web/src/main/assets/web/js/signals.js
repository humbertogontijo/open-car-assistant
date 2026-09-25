/**
 * App-facing signals API. Backed by vendored signal-polyfill (Chrome 83 target).
 * @see vendor/signal-polyfill.js
 *
 * effect() is the supported way to react to signal changes (root paint, etc.).
 * Flush runs in a microtask after the Watcher notify so it lines up with
 * store scheduleNotify()'s rAF bump → same-frame paint before the browser paints.
 */
import { Signal } from "./vendor/signal-polyfill.js";

export { Signal };

/** Create a writable state signal. */
export function signal(initial, options) {
  return new Signal.State(initial, options);
}

/** Create a computed signal. */
export function computed(fn, options) {
  return new Signal.Computed(fn, options);
}

/**
 * Run `callback` when any signals it reads change.
 * Returns a dispose function.
 *
 * Side-effect callbacks should catch their own errors; we also guard the
 * Watcher flush so one throw cannot leave the effect permanently stuck.
 */
export function effect(callback) {
  let cleanup;
  let disposed = false;
  let pending = false;
  const watcher = new Signal.subtle.Watcher(function () {
    // Watcher notify must not read/write signals — only schedule work.
    if (pending || disposed) return;
    pending = true;
    queueMicrotask(function () {
      pending = false;
      if (disposed) return;
      var list = watcher.getPending();
      for (var i = 0; i < list.length; i++) {
        try {
          list[i].get();
        } catch (e) {}
      }
      try {
        watcher.watch();
      } catch (e) {}
    });
  });
  const computed = new Signal.Computed(function () {
    if (typeof cleanup === "function") {
      try {
        cleanup();
      } catch (e) {}
      cleanup = undefined;
    }
    cleanup = callback();
  });
  watcher.watch(computed);
  try {
    computed.get();
  } catch (e) {}
  return function dispose() {
    if (disposed) return;
    disposed = true;
    try {
      watcher.unwatch(computed);
    } catch (e) {}
    if (typeof cleanup === "function") {
      try {
        cleanup();
      } catch (e) {}
      cleanup = undefined;
    }
  };
}
