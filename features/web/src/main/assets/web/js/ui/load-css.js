const loaded = new Set();
export function loadCss(href) {
  if (!href || loaded.has(href)) return;
  loaded.add(href);
  if (typeof document === "undefined") return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = href;
  document.head.appendChild(link);
}
