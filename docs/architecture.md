# Open Car Assistant — Architecture

Open Car Assistant (OCA) is a head-unit app where **vehicle platform is a plugin** (`VehicleIntegration`). The shell owns UI, web, install, DVR, remote debug, and **external bridge plugins** (Home Assistant, …). Integrations are keyed by **shared chip/platform identity** (not marketing make/model).

## Modules

| Module | Path | Role |
|--------|------|------|
| `:app` | `app/` | Thin WebView host → `http://127.0.0.1:8787`, `AssistantRuntime`, foreground service, boot receiver |
| `:integration-api` | `libs/api/` | SPI: `VehicleIntegration`, `VehicleSession`, capabilities, opaque properties, `OcaPlugin`, ServiceLoader registries |
| `:oca-support` | `libs/support/` | Product helpers shared by features: `I18nBundle`, `LastKnownStore` (not vehicle-specific) |
| `:car-stubs` | `libs/car-stubs/` | Compile-only `android.car` stubs (never packaged) |
| `:signing` | `libs/signing/` | Community testkey + host APK re-sign CLI |
| `:integrations:antora1000` | `integrations/antora1000/` | Antora / SE1000 (EX5 EM-i) — `platform.json` + dual VHAL backends (gRPC / CarProperty) |
| `:integrations:ihu629g` | `integrations/ihu629g/` | IHU629G (BR/CN EX2) — VHAL via CarPropertyManager |
| `:integrations:platform:common` | `integrations/platform/common/` | Universal AAOS plumbing: `PlatformConfig`, `CarPropertyBridge`, `VehiclePropertyBackend`, `AospVehicleIds` |
| `:integrations:platform:flyme` | `integrations/platform/flyme/` | Flyme Auto family helpers shared by EX2 + EX5 |
| `:feature-web` | `features/web/` | **Single product UI** (Flyme-inspired HTML/CSS/JS shell + themes) + Ktor APIs + `/debug` |
| `:feature-*` | `features/<id>/` | Memory, telemetry, install, DVR, debug/`CatalogProbe`, history, shortcuts |
| `:plugin-homeassistant` | `plugins/homeassistant/` | Home Assistant REST + WebSocket bridge (shortcut actions/triggers) |

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

### Poll vs push (vehicle state)

`VehiclePropertyBackend.observe()` is optional:

- **Push** — return a hot `Flow<PropertyUpdate>` (Antora `GrpcVhalBackend` from Venus `StartPropertyValuesStream`; `CarPropertyBackend` when `registerCallback` works). The session collects updates, publishes distinct `telemetry()`, and emits `VehicleEvent.EntityValueChanged` / gear / ignition edges. No 1s session poll.
- **Poll** — leave `observe()` null (or CarProperty registration fails). The session owns a ~1s `readSnapshot()` loop and still fans out entity events so the UI/WebSocket stay event-driven.

The web UI never polls vehicle state: Ktor `/api/events` fans out session telemetry + events (and `catalog` after writes). Camera HLS stays on its own path.

## Plugin SPI

External bridges (not HU platforms) implement `OcaPlugin` under `cc.opencar.assistant.api.plugin`. See [plugins.md](plugins.md).

## Discovery

Vehicle integrations and plugins are **folder-discovered**:

- Gradle scans `integrations/<id>/` (except `platform/`) and `plugins/<id>/` for `build.gradle.kts`, includes them (`:integrations:<id>`, `:plugin-<id>`), and wires them into `:app`.
- Curated shell features live under `features/<id>/` as `:feature-<id>` (explicit list in `settings.gradle.kts`).
- Runtime loads implementations via `ServiceLoaderIntegrationRegistry` / `ServiceLoaderPluginRegistry` from `META-INF/services`.
- `:app` and `:feature-*` depend only on `:integration-api` interfaces (plus `:oca-support` for i18n/cache). They never import concrete integration or plugin classes.

## First platform: `antora1000`

Detects via `ro.product.device` / `ro.hardware` / fingerprint containing `antora1000` or `se1000`.

VHAL access (user-space `/data` install):

- **Default** — `GrpcVhalBackend` → VenusVehicleServer `127.0.0.1:40004`
- **Fallback** — `CarPropertyBackend` only if gRPC is unreachable (reads may be denied)

Shared `CarPropertyBackend` remains available for platforms that talk to `CarPropertyManager` directly (see `ihu629g`).

Variants:

- `phev` — fuel capacity or hybrid SOC present (EX5 EM-i)
- `bev` — battery-only
- `default` — fallback

Known badges (docs only): EX5, EX5 EM-i, Starray, Starship 7, Proton e.Mas 7, Galaxy E5.

## Second platform: `ihu629g`

Detects via fingerprint match for `ihu629` / `geometry`. Uses **CarPropertyManager only** (no VenusVehicleServer). Property IDs and `access` come from `platform.json` / `Ihu629gVhalIds`. Shared AOSP HVAC/speed stubs live in `platform/aosp.json` (+ `AospVehicleIds`) under `:integrations:platform:common`.

## Runtime flow

1. `OcaApp` builds `DeviceFingerprint` and matches an integration via ServiceLoader registry (Lab can override).
2. Integration connects → `VehicleSession` + variant + capabilities.
3. Feature controllers start when required capabilities are present.
4. Plugins (`ServiceLoaderPluginRegistry`) start with a `PluginHost`, then shortcuts wire their action/trigger contributions.
5. `OcaWebServer` binds `0.0.0.0:8787` (LAN UI + `/debug`).
6. `AssistantService` keeps a foreground watchdog for reapply / web / DVR.

## Web UI shell (Flyme-inspired tokens)

The product UI in `:feature-web` assets uses a shared **Alive Design** token set (`themes.css`) applied app-wide: frosted surfaces, ice-blue accent, Dock sidebar with linear SVG icons (`icons/sprite.svg`), slim status chips, and glass setup overlay. Spacing/radius/touch (≥48px) are theme-agnostic across `dark` / `light` / `contrast`.

Rendering is **lit-html** (vendored ESM under `web/js/vendor/`) driven by a small reactive store (`store.js` `patch` / `subscribe`). Section templates live under `web/js/sections/`; control widgets under `web/js/ui/`. Live updates arrive on `/api/events` WebSocket (`telemetry` / `entity` / `catalog`); the client bootstraps once via HTTP `refresh()` and does **not** soft-poll. In-session scroll is remembered per section in memory only (not `localStorage`); process kill still starts at Home. Theme/locale/units prefs remain in `localStorage` (and `/api/prefs`). Static assets are served with `Cache-Control: no-store` (no `?v=` query busting).

Control cards are typed by `ControlDef.input` (`bool`, `choice`, `int`, `float`, `text`, `sensor`). Choice with ≤3 options renders as pills; more than three uses a styled dropdown. Each writable card can **pin** a boot value; live writes go to VHAL, persist writes go to DataStore only.

`ControlDef.group` is the **OEM nav section id** (Início / Controles / Condução / Energia / Iluminação / ADAS / Assistente / Tela / Som / Conexão / Meu Veículo). `EntityType` remains the family subsection header inside a tab. Energia is capability-gated (`CHARGING` / `HYBRID_ENERGY`). Android Wi‑Fi/BT + ADB/storage cards live under Conexão. Assistente binds voice VHAL (`vr_activated`); more OEM voice props TBD via Lab.

Numeric entities carry HA-style `deviceClass` + `unitOfMeasurement` (canonical platform ids from `:integration-api`). Cards convert to the user’s preferred unit per dimension (temperature, distance, speed, fuel economy, energy economy) via `web/js/units.js`, including L/100km ↔ km/L / mpg and kWh/100km ↔ km/kWh.

## Entity contract (HA-inspired)

Product entities are the **portable contract** across platforms. See [`EntityContract`](../libs/api/src/main/java/cc/opencar/assistant/api/EntityContract.kt).

| Concept | OCA | Notes |
|---------|-----|--------|
| Entity id | Catalog id (`sensor_soc`, `hvac_temp`, …) | Stable; used by UI, history, shortcuts, scenes, routines |
| Domain | [`EntityType`](../libs/api/src/main/java/cc/opencar/assistant/api/EntityType.kt) (`sensor`, `climate`, `lock`, …) | Exposed as `domain` (+ legacy `entity`) on `/api/entities` |
| State + attributes | `state`/`value` + `attributes` map | Also `friendlyName`, `available`, `deviceClass`, `unitOfMeasurement` |
| Availability | Binding + diagnose status | Entity omitted / `unavailable` when the platform has no binding — like HA not registering the entity |
| Device class / UoM | [`DeviceClass`](../libs/api/src/main/java/cc/opencar/assistant/api/DeviceClass.kt), [`UnitOfMeasurement`](../libs/api/src/main/java/cc/opencar/assistant/api/UnitOfMeasurement.kt) | Icons, history charts, future MQTT/HA discovery |

**Rules for multi-make portability**

1. Features and automations reference **catalog entity ids** only — never VHAL hex or OEM property names.
2. Integrations map [`WellKnownProperties`](../libs/api/src/main/java/cc/opencar/assistant/api/WellKnownProperties.kt) → native IDs in `platform.json`; one product [`ControlCatalog`](../features/web/src/main/java/cc/opencar/assistant/feature/web/ControlCatalog.kt), no per-make catalog forks.
3. Builtin scenes/routines must tolerate missing targets (platform without that binding).
4. Nav sections compose the same entities into dashboards (`home`, `energy`, `controls`, `drive`, …) — specialized layouts, not a separate Lovelace layer.

**Outbound HA discovery** (MQTT / publish OCA as an HA device) is deferred until this contract stays stable. Inbound bridge remains `:plugin-homeassistant`.

## Settings memory (boot / gear reapply)

`SettingsMemoryController` stores per-control pins under snake_case catalog ids (`pin_<id>` / `val_<id>`). Values are reapplied to the vehicle on **Boot / session-ready**, **gear changes**, and **screen-on** (via `AssistantRuntime.notifyScreenOn`). APIs: `POST /api/controls/{id}/persist`, bulk capture/reapply via `/api/memory/*`. `LastKnownStore` (`:oca-support`) remains a display-only stale cache.

## Shortcuts (flows + scenes + routines)

`:feature-shortcuts` has three building blocks under the web **Shortcuts** section. They map cleanly to Home Assistant:

| Home Assistant | OCA | Role |
|----------------|-----|------|
| **Automation** | **Shortcut (flow)** | Trigger → AND conditions → actions |
| **Script** | **Routine** | Reusable fire-once action sequence (`run_routine`); optional AND conditions gate every run |
| **Scene** | **Scene** | Multi-entity on/off with restore or forced off-value |
| **Helper** (`input_boolean`, …) | **`ui_card`** virtual control | Bool/command card in the entity grid (`shortcut_<id>`) |
| **Blueprint** | Builtin scenes (e.g. **Sentinel**) | Ship templates keyed only to catalog entity ids; skip unbound targets |

- **Shortcut (flow)** — triggers + conditions + actions. Event triggers: `boot` / `screen` (`on` / `off`; HU wake/sleep, debounced ~5s) / `gear` / `wheel_key` / `wifi_ssid` / `entity_state` / plugin triggers. Conditions (`entity_equals` / `gear_equals` / `wifi_ssid`) are AND-gated after a trigger match. Actions may `set_control`, `set_scene`, `run_routine`, `launch_app`, `delay_ms`, or plugin actions.
- **Scene** — snapshot configured entities, write on-values when activated; on deactivate restore snapshot or force a value per target. Builtin **Sentinel** seeds on first use (parking comfort on; HVAC / exterior lights / fog off). An external write to any target of an **active** scene deactivates that scene (user left the mode). On **boot**, every scene still marked active is restored (off-path) before boot shortcuts run.
- **Routine** — reusable fire-once action sequence. Optional AND conditions (same types as flows) are evaluated on **every** run (API, nested `run_routine`); empty = always pass.

**Device tracker / home zone:** `device_tracker_vehicle` (My Vehicle) exposes GPS presence as HA-style `home` / `not_home` vs a home lat/lon/radius stored in `/api/prefs` (`homeLat`, `homeLon`, `homeRadiusM`; set via Settings or `POST /api/location/home/here`). Use an `entity_equals` condition with entity `device_tracker_vehicle` and value `home` to gate routines/flows. Requires `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`.

**Android section (non-VHAL):** Nav **Android** (`group=android`) holds OS-controlled entities separate from VHAL Sound / My Vehicle: `android_wifi`, `android_bluetooth`, `android_brightness` (0–255, needs Modify system settings), and HA-style **`media_player_vehicle`** (`EntityType.MEDIA_PLAYER`, input `media_player`; state `playing` / `paused` / `idle`; attributes `media_title` / `media_artist` / `media_album` / `app_id` / `volume`; writes accept states, transport `play` / `pause` / `play_pause` / `next` / `previous` / `stop`, or volume `volume_up` / `volume_down` / `volume:N`). Now-playing metadata needs the notification listener (or privileged `MEDIA_CONTENT_CONTROL`). Transport and **media** cabin volume writes use unprivileged key inject (`InputManager` / `input keyevent`); levels are read from `Settings.System` `android.car.VOLUME_GROUP/{N}` (CarVolumeGroup mirrors). OEM Som sliders map to Sound entities **`cabin_vol_media`** (0, writable via keys; same bus as media_player volume), **`cabin_vol_navigation`** (2), **`cabin_vol_voice`** (6), **`cabin_vol_call`** (3), **`cabin_vol_ring`** (7) — non-media groups are read-only without `CAR_CONTROL_AUDIO_VOLUME`. Sound **`media_volume`** stays on VHAL (`SETTING_FUNC_AUDIO_MEDIA_VOLUME`) — likely startup/limit semantics, not the live cabin bus. `speed_volume` / AVAS remain VHAL under **Sound**. Example: pause when gear = P via `set_control` on `media_player_vehicle` = `paused`.

Flows may include a non-event **`ui_card` trigger** that publishes a virtual control (`shortcut_<id>`): with a `set_scene` action the card is a bool bound to that scene; otherwise a command that runs the flow.

**Portable automations:** only reference catalog entity ids that appear in ControlCatalog. Prefer builtins / shared templates over platform-specific VHAL. Future HA-like polish (not required for first platforms): run modes (`single` / `restart`), last-run traces — avoid full Choose/YAML complexity on the HU.

APIs: `/api/shortcuts`, `/api/scenes`, `/api/routines`, `/api/apps`. Legacy combined shortcuts migrate once into a routine + a flow that `run_routine`s it (flow id preserved for pin slots).

Fixed **pin slots** 1–8 are assigned to flows and appear in the float-chip dropdown.

A native **quick entry** (hosted by `AssistantService`) is provided by the matched integration via `VehicleIntegration.createQuickEntry()`:

- **Flyme Auto** (`:integrations:platform:flyme`) — status-bar icon through notification extras (`flag_status_icon_*`). Tap fires a callback into the shared dropdown.
- **Default** — `FloatChipQuickEntry` WindowManager overlay placed below the status bar (touchable on HUs where SystemUI would eat an overlay in the bar band).

The shared dropdown (Open / Cameras / Shortcuts / Background / Exit + pinned slots) lives in `:feature-shortcuts` and is platform-agnostic.

Wake / `screen` triggers (`on` / `off`) listen for AOSP `ACTION_SCREEN_ON` / `USER_PRESENT` / display + interactive polls, plus optional platform [WakeSignals](../libs/api/src/main/java/cc/opencar/assistant/api/WakeSignals.kt) (Flyme: ECARX `ACC_ON` / `DISPLAY_ON` / `STR_RESUME` / … via `:integrations:platform:flyme`). **Manifest-registered** [FlymeWakeReceiver](../integrations/platform/flyme/src/main/java/cc/opencar/assistant/integrations/platform/flyme/FlymeWakeReceiver.kt) catches those when the process was dead during STR; pending edges are flushed once shortcuts start. Debounced ~5 s. Antora polls `WHEEL_HARD_KEY_*` for press edges and emits `VehicleEvent.WheelKeyPressed`. OEM `BCM_FUNC_CUSTOM_KEY` is exposed as Controles control `wheel_custom_key`. **Custom AVAS / lock sounds** live under the Som section (app storage `sounds/avas`, `sounds/lock`) via `/api/sounds`; preview/playback uses app `MediaPlayer`. OEM AVAS style/volume remain the VHAL ints `esm_sound` / `esm_volume` — there is no confirmed vendor wav drop-in path.

## DVR / cameras

One capture path: Camera → GLES mosaic → HW H.264 (`:feature-dvr`). Live clients and the DVR writer share [`SharedMosaicHub`](../features/dvr/src/main/java/cc/opencar/assistant/feature/dvr/SharedMosaicHub.kt) refcounts so stopping recording does not tear down HLS. Continuous mode persists as `mode=dvr`; ACC wake starts / sleep stops (debounced ~5 s, same as shortcuts). Rotating MP4 (~5 min / 100 MB) under `dvr/` with wall-clock meta in app-private `files/dvr-meta/`. Web: day-scoped scrubber + Cut remux (`/api/dvr/timeline`, `/play`, `/cut`, `/live.m3u8`). Pure helpers: `DvrTimelineMath` / `DvrStorageMath` (JVM unit tests) and `dvr-timeline.js`.

## Safety

Writable VHAL IDs are properties with `access` `w`/`rw` in each integration's `platform.json` (loaded via `PlatformConfig`; derived as `writableAllowlist`). Web/debug writes require Contributor mode + token. VIN is redacted in reads/exports.

## Extending

New SoC/HU family → new folder `integrations/<platform-id>/` (+ ServiceLoader entry). Same chip, market quirk → new `PlatformVariant` or property override map, not a new module. Prefer **ihu629g** as the thin CarProperty reference when cloning. See [adding-an-integration.md](adding-an-integration.md).

New external bridge → new folder `plugins/<id>/` implementing `OcaPlugin` (+ ServiceLoader entry). See [plugins.md](plugins.md).

New first-party shell feature → curated `features/<id>/` (Gradle + `AssistantRuntime` + often web). Integrations/plugins are plug-and-play; features are not. See [adding-a-feature.md](adding-a-feature.md).
