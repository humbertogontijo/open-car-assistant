# Plugins

Open Automotive Assistant distinguishes **vehicle integrations** (HU / SoC platforms) from **plugins** (external bridges such as Home Assistant).

Plugins are **compile-time** Gradle modules under `plugins/<id>/`. Folders are auto-included as `:plugin-<id>` and wired into `:app`. Runtime discovery uses Java ServiceLoader (`META-INF/services`). There is no dynamic ClassLoader / store-loaded plugin APK path.

Collaborator work stays under `plugins/<id>/` — no edits to `AssistantRuntime`, `features/web`, or Gradle include lists.

## SPI (`:integration-api`)

```kotlin
interface OaaPlugin {
  val id: String
  val displayName: String
  val actionHandler: ShortcutActionHandler?   // optional
  val triggerSource: ShortcutTriggerSource?   // optional
  suspend fun start(host: PluginHost)
  fun stop()
  fun status(): Map<String, Any?>             // no secrets
  fun configSchema(): PluginConfigSchema?     // optional Plugins UI fields
  fun configSnapshot(): Map<String, Any?>     // safe GET
  fun applyConfig(values: Map<String, Any?>)  // generic POST
}
```

Shortcut contributions:

- **Actions** — JSON `{ "type": "plugin", "pluginId", "action", "params" }` resolved by `ShortcutActionHandler`
- **Triggers** — `{ "type": "plugin", "pluginId", "trigger", "params" }` matched against events from `ShortcutTriggerSource`

Include `actions` / `triggers` (and optional `actionParams` / `triggerParams` maps) in `status()` so the Shortcuts editor can build pickers without hard-coding the plugin.

## HTTP + Plugins UI

Generic routes (no per-plugin Kotlin in `:feature-web`):

| Method | Path | Behavior |
|--------|------|----------|
| GET | `/api/plugins` | List id, displayName, status, config, schema |
| GET | `/api/plugins/{id}` | One plugin detail |
| POST | `/api/plugins/{id}` | `applyConfig` with JSON body |

`/api/status` → `plugins` is the same detail list. The **Plugins** sidebar section shows **Available** (Add / Set up) vs **Installed** (Configure / Enable / Disable).

## Home Assistant (`:plugin-homeassistant`)

Uses **REST + WebSocket** only (not MQTT). Works with:

- Local HA URL (`http://192.168.x.x:8123`)
- Reverse proxy
- **Nabu Casa** (`https://….ui.nabu.casa`) — cloud exposes HA HTTP/WebSocket, not the Mosquitto addon

### Setup

1. In Home Assistant: create a **Long-Lived Access Token** (Profile → Security).
2. In Open Automotive Assistant **Plugins → Home Assistant → Add / Set up**: paste URL + token, save (enables by default).
3. Status shows **Connected** when the WebSocket auth succeeds.

### Shortcut contributions

| Kind | Type | Params |
|------|------|--------|
| Action | `call_service` | `domain`, `service`, optional `entity_id`, optional `data` |
| Trigger | `entity_state` | `entity_id`, optional `from`, optional `to` (edge on state string change) |

## Adding a plugin

```
plugins/<id>/
  build.gradle.kts                 # depend on :integration-api
  src/main/java/.../YourPlugin.kt  # implements OaaPlugin
  src/main/resources/META-INF/services/
    cc.opencar.assistant.api.plugin.OaaPlugin
```

1. Create `plugins/<id>/` as above.
2. Implement `OaaPlugin` (+ optional shortcut handler/source + config schema).
3. Register the FQCN in `META-INF/services/cc.opencar.assistant.api.plugin.OaaPlugin`.
4. Never return secrets in `status()` / `configSnapshot()`.

Gradle discovers the folder automatically as `:plugin-<id>`. Shortcuts and the Plugins UI pick up contributions from the SPI.
