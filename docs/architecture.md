# Open Car Assistant — Architecture

Open Car Assistant (OCA) is a head-unit app where **vehicle platform is a plugin** (`VehicleIntegration`). The shell owns UI, web, install, DVR, remote debug, and **external bridge plugins** (Home Assistant, …). Integrations are keyed by **shared chip/platform identity** (not marketing make/model).

## Modules

| Module | Role |
|--------|------|
| `:app` | Thin WebView host → `http://127.0.0.1:8787`, `AssistantRuntime`, foreground service, boot receiver |
| `:integration-api` | SPI: `VehicleIntegration`, `VehicleSession`, capabilities, opaque properties, `OcaPlugin`, ServiceLoader registries |
| `:oca-support` | Product helpers shared by features: `I18nBundle`, `LastKnownStore` (not vehicle-specific) |
| `:integrations:antora1000` | Antora / SE1000 (EX5 EM-i) — `platform.json` + dual VHAL backends (gRPC / CarProperty) |
| `:integrations:ihu629g` | IHU629G (BR/CN EX2) — VHAL via CarPropertyManager |
| `:integrations:platform:common` | Universal AAOS plumbing: `PlatformConfig`, `CarPropertyBridge`, `VehiclePropertyBackend`, `AospVehicleIds` |
| `:integrations:platform:flyme` | Flyme Auto family helpers shared by EX2 + EX5 |
| `:feature-web` | **Single product UI** (Flyme-inspired HTML/CSS/JS shell + themes) + Ktor APIs + `/debug` |
| `:feature-history` | Entity history recorder |
| `:feature-shortcuts` | Shortcut macros, wake/wheel/plugin triggers, float chip |
| `:plugin-homeassistant` | Home Assistant REST + WebSocket bridge (shortcut actions/triggers) |
| `:feature-*` | Memory, telemetry, install, DVR, debug/`CatalogProbe`, history, shortcuts |
| `:car-stubs` | Compile-only `android.car` stubs (never packaged) |

HU and phone share the same UI. Native code owns VHAL, DVR, install, and the HTTP server — not a second Compose product surface.


## Integration SPI

```kotlin
interface VehicleIntegration {
  val id: String                         // e.g. "antora1000"
  fun matches(device: DeviceFingerprint): Boolean
  fun detectVariant(session: VehicleSession): PlatformVariant
  fun capabilities(variant: PlatformVariant): Set<Capability>
  suspend fun connect(context: Context): VehicleSession
}
```

Features never hardcode VHAL hex IDs. They use `WellKnownProperties` / `VehicleProperty(namespace, key)`. The integration maps those to native IDs.

## Plugin SPI

External bridges (not HU platforms) implement `OcaPlugin` under `cc.opencar.assistant.api.plugin`. See [plugins.md](plugins.md).

## Discovery

Vehicle integrations and plugins are **folder-discovered**:

- Gradle scans `integrations/<id>/` (except `platform/`) and `plugin-*/` for `build.gradle.kts`, includes them, and wires them into `:app`.
- Runtime loads implementations via `ServiceLoaderIntegrationRegistry` / `ServiceLoaderPluginRegistry` from `META-INF/services`.
- `:app` and `:feature-*` depend only on `:integration-api` interfaces (plus `:oca-support` for i18n/cache). They never import concrete integration or plugin classes.

## First platform: `antora1000`

Detects via `ro.product.device` / `ro.hardware` / fingerprint containing `antora1000` or `se1000`.

VHAL access (auto-selected):

- **Unprivileged** — `GrpcVhalBackend` → VenusVehicleServer `127.0.0.1:40004`
- **Privileged** (`CAR_VENDOR_EXTENSION` granted) — `CarPropertyBackend` → `CarPropertyManager`

Variants:

- `phev` — fuel capacity or hybrid SOC present (EX5 EM-i)
- `bev` — battery-only
- `default` — fallback

Known badges (docs only): EX5, EX5 EM-i, Starray, Starship 7, Proton e.Mas 7, Galaxy E5.

## Second platform: `ihu629g`

Detects via fingerprint match for `ihu629` / `geometry`. Uses **CarPropertyManager only** (no VenusVehicleServer). Property IDs and writable allowlist come from `platform.json` / `Ihu629gVhalIds`. Shared AOSP HVAC/speed IDs live in `AospVehicleIds` under `:integrations:platform:common`.

## Runtime flow

1. `OcaApp` builds `DeviceFingerprint` and matches an integration via ServiceLoader registry (Lab can override).
2. Integration connects → `VehicleSession` + variant + capabilities.
3. Feature controllers start when required capabilities are present.
4. Plugins (`ServiceLoaderPluginRegistry`) start with a `PluginHost`, then shortcuts wire their action/trigger contributions.
5. `OcaWebServer` binds `0.0.0.0:8787` (LAN UI + `/debug`).
6. `AssistantService` keeps a foreground watchdog for reapply / web / DVR.

## Web UI shell (Flyme-inspired tokens)

The product UI in `:feature-web` assets uses a shared **Alive Design** token set (`themes.css`) applied app-wide: frosted surfaces, ice-blue accent, Dock sidebar with linear SVG icons (`icons/sprite.svg`), slim status chips, and glass setup overlay. Spacing/radius/touch (≥48px) are theme-agnostic across `dark` / `light` / `contrast`.

Control cards are typed by `ControlDef.input` (`bool`, `choice`, `int`, `float`, `text`, `sensor`). Choice with ≤3 options renders as pills; more than three uses `<select>`. Each writable card can **pin** a boot value (flip face); live writes go to VHAL, persist writes go to DataStore only.

Numeric entities carry HA-style `deviceClass` + `unitOfMeasurement` (canonical ids from `:integration-api`) and localized `unitLabel` (from `unit.*` i18n keys).

## Settings memory (boot / gear reapply)

`SettingsMemoryController` stores per-control pins under snake_case catalog ids (`pin_<id>` / `val_<id>`). Values are reapplied to the vehicle on **Boot / session-ready**, **gear changes**, and **screen-on** (via `AssistantRuntime.notifyScreenOn`). APIs: `POST /api/controls/{id}/persist`, bulk capture/reapply via `/api/memory/*`. `LastKnownStore` (`:oca-support`) remains a display-only stale cache.

## Shortcuts (macros + float chip)

`:feature-shortcuts` stores named action sequences (set control, launch app, delay, plugin actions) with triggers:

- `boot` / `screen` (`on` / `off`; HU wake/sleep, debounced ~5s) / `gear` / `wheel_key`
- Plugin triggers (e.g. Home Assistant `entity_state`) when the plugin is enabled

Fixed **pin slots** 1–8 are assigned separately (not as shortcut triggers) and appear in the float-chip dropdown.

A native **quick entry** (hosted by `AssistantService`) is provided by the matched integration via `VehicleIntegration.createQuickEntry()`:

- **Flyme Auto** (`:integrations:platform:flyme`) — status-bar icon through notification extras (`flag_status_icon_*`). Tap fires a callback into the shared dropdown.
- **Default** — `FloatChipQuickEntry` WindowManager overlay placed below the status bar (touchable on HUs where SystemUI would eat an overlay in the bar band).

The shared dropdown (Open / Cameras / Shortcuts / Background / Exit + pinned slots) lives in `:feature-shortcuts` and is platform-agnostic. Config UI: web **Shortcuts** section (`/api/shortcuts`, `/api/apps`).

Wake / `screen` triggers (`on` / `off`) listen for AOSP `ACTION_SCREEN_ON` / `USER_PRESENT` / display + interactive polls, plus optional platform [WakeSignals](../integration-api/src/main/java/cc/opencar/assistant/api/WakeSignals.kt) (Flyme: ECARX `ACC_ON` / `DISPLAY_ON` / `STR_RESUME` / … via `:integrations:platform:flyme`). **Manifest-registered** [FlymeWakeReceiver](../integrations/platform/flyme/src/main/java/cc/opencar/assistant/integrations/platform/flyme/FlymeWakeReceiver.kt) catches those when the process was dead during STR; pending edges are flushed once shortcuts start. Debounced ~5 s. Antora polls `WHEEL_HARD_KEY_*` for press edges and emits `VehicleEvent.WheelKeyPressed`. OEM `BCM_FUNC_CUSTOM_KEY` is exposed as cabin control `wheel_custom_key`. **Custom AVAS audio files** remain out of scope.

## Safety

Writable VHAL IDs live in each integration's `platform.json` `writableAllowlist` (loaded via `PlatformConfig`). Web/debug writes require Contributor mode + token. VIN is redacted in reads/exports.

## Extending

New SoC/HU family → new folder `integrations/<platform-id>/` (+ ServiceLoader entry). Same chip, market quirk → new `PlatformVariant` or property override map, not a new module. Prefer **ihu629g** as the thin CarProperty reference when cloning. See [adding-an-integration.md](adding-an-integration.md).

New external bridge → new `plugin-<id>/` implementing `OcaPlugin` (+ ServiceLoader entry). See [plugins.md](plugins.md).

New first-party shell feature → curated `feature-*` (Gradle + `AssistantRuntime` + often web). Integrations/plugins are plug-and-play; features are not. See [adding-a-feature.md](adding-a-feature.md).
