# Shortcuts (flows, scenes, routines)

`:feature-shortcuts` under the web **Shortcuts** section. Home Assistant mapping:

| Home Assistant | Product | Role |
|----------------|-----|------|
| **Automation** | **Shortcut (flow)** | Trigger → AND conditions → actions |
| **Script** | **Routine** | Reusable fire-once action sequence (`run_routine`); optional AND conditions gate every run |
| **Scene** | **Scene** | Multi-entity on/off with restore or forced off-value |
| **Helper** (`input_boolean`, …) | **`ui_card`** virtual control | Bool/command card in the entity grid (`shortcut_<id>`) |
| **Blueprint** | Builtin scenes (e.g. **Sentinel**) | Templates keyed only to catalog entity ids; skip unbound targets |

## Building blocks

- **Shortcut (flow)** — triggers + conditions + actions. Event triggers: `boot` / `screen` (`on` / `off`; HU wake/sleep, debounced ~5s) / `gear` / `wheel_key` / `wifi_ssid` / `entity_state` / plugin triggers. Conditions (`entity_equals` / `gear_equals` / `wifi_ssid`) are AND-gated after a trigger match. Actions may `set_control`, `set_scene`, `run_routine`, `launch_app`, `delay_ms`, or plugin actions.
- **Scene** — snapshot configured entities, write on-values when activated; on deactivate restore snapshot or force a value per target. Builtin **Sentinel** seeds on first use. An external write to any target of an **active** scene deactivates it. On **boot**, every scene still marked active is restored (off-path) before boot shortcuts run.
- **Routine** — reusable fire-once action sequence. Optional AND conditions evaluated on **every** run; empty = always pass.

**Portable automations:** reference [EntityRegistry](../libs/api/src/main/java/cc/opencar/assistant/api/EntityRegistry.kt) entity ids only — never VHAL hex. Builtin scenes skip missing targets.

## Device tracker / home zone

`device_tracker_vehicle` (My Vehicle) exposes GPS presence as HA-style `home` / `not_home` vs prefs `homeLat` / `homeLon` / `homeRadiusM` (`POST /api/location/home/here`). Gate flows with `entity_equals` on that entity. Needs location permissions.

## Quick entry & wake

- Integration may supply `VehicleIntegration.createQuickEntry()` (Flyme status-bar via `:integrations:platform:flyme`; default float chip).
- Shared dropdown (Open / Cameras / Shortcuts / … + pin slots 1–8) lives in `:feature-shortcuts`.
- `screen` on/off: AOSP screen intents + optional platform `WakeSignals` (Flyme ACC/STR). Debounced ~5s.

## APIs

`/api/shortcuts`, `/api/scenes`, `/api/routines`, `/api/apps`.

HU radios / media product ids are documented in [domains.md](domains.md) § HU settings — not under a separate `android` domain.
