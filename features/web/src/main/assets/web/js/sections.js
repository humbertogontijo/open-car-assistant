/** @deprecated Import from ./sections/index.js */
export * from "./sections/index.js";

/** @deprecated Handlers live in lit templates. */
export function bindSection() {}
export function fillProbeTable() {}
export async function setControl(id, val) {
  const { setControl: sc } = await import("./actions.js");
  return sc(id, val);
}
