export async function api(path, opts) {
  const r = await fetch(path, opts);
  const ct = r.headers.get("content-type") || "";
  if (ct.indexOf("json") >= 0) return r.json();
  return r.text();
}

export function fmt(v) {
  return v == null || v === "" ? "—" : v;
}

export function $(id) {
  return document.getElementById(id);
}
