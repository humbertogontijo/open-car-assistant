# Vendored browser libraries

Built with `esbuild --target=chrome83` for the IVI WebView.

| File | Source | Notes |
|------|--------|--------|
| `lit-html.js` + directives | lit-html 3.2.1 | existing |
| `signal-polyfill.js` | signal-polyfill 0.2.2 | TC39 signals |
| `lit-router.js` | @lit-labs/router 0.1.4 | Routes + Router |
| `urlpattern.js` | urlpattern-polyfill 10.1.0 | native URLPattern is Chrome 95+ |
| `hls.light.mjs` | hls.js | existing |

Do not commit raw modern npm ESM that contains `||=` / `&&=` / `??=` or private fields without downleveling.
