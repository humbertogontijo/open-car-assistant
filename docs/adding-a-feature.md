# Adding a first-party feature

Shell features (`feature-*`) are **curated** — not folder-auto-discovered like integrations or plugins. Expect a small multi-file PR that wires Gradle, the composition root, and usually the web UI.

Integrations and plugins stay plug-and-play; see [adding-an-integration.md](adding-an-integration.md) and [plugins.md](plugins.md).

## Checklist

1. **Create the module**
   - Folder `feature-<id>/` with `build.gradle.kts` (Android library, depend on `:integration-api` and optionally `:oca-support`).
   - Package under `cc.opencar.assistant.feature.<id>`.

2. **Register Gradle**
   - `include(":feature-<id>")` in [settings.gradle.kts](../settings.gradle.kts).
   - `implementation(project(":feature-<id>"))` in [app/build.gradle.kts](../app/build.gradle.kts).
   - If the web layer needs the types: `api(project(":feature-<id>"))` in [feature-web/build.gradle.kts](../feature-web/build.gradle.kts).

3. **Wire `AssistantRuntime`**
   - Construct the controller in [AssistantRuntime.kt](../app/src/main/java/cc/opencar/assistant/AssistantRuntime.kt) after the session is ready.
   - Gate on `Capability` when the feature needs vehicle writes/reads.

4. **Expose HTTP (usual)**
   - Add fields to [OcaWebDeps.kt](../feature-web/src/main/java/cc/opencar/assistant/feature/web/OcaWebDeps.kt) if needed.
   - Pass them from `OcaWebServer` / `AssistantRuntime`.
   - Add `Routes<Id>.kt` and call `register*Routes` from [OcaWebServer.kt](../feature-web/src/main/java/cc/opencar/assistant/feature/web/OcaWebServer.kt).

5. **Product UI (if user-facing)**
   - Section in `feature-web` assets (`sections.js` / `index.html` nav).
   - Strings in `support/src/main/assets/i18n/common/{en,pt-BR}.json`.

6. **Verify**
   - `./gradlew :app:assembleDebug`
   - On-device smoke via `./tools/oca-setup -i <platform> -H <ip> …` when vehicle-related.

## What not to do

- Do not put OEM VHAL hex IDs in features — use `WellKnownProperties` / opaque `VehicleProperty`.
- Do not import concrete `integrations.*` classes from `:app` or `:feature-*`.
- Prefer a new **plugin** (`plugin-<id>/`) for external bridges (Home Assistant-style), not a feature module.

## New product controls (not a whole feature)

Adding a visible control card usually touches:

1. [WellKnownProperties.kt](../integration-api/src/main/java/cc/opencar/assistant/api/WellKnownProperties.kt)
2. [ControlCatalog.kt](../feature-web/src/main/java/cc/opencar/assistant/feature/web/ControlCatalog.kt)
3. Common i18n (`control.<id>` / options)
4. Optional pin map in [SettingsMemoryController.kt](../feature-memory/src/main/java/cc/opencar/assistant/feature/memory/SettingsMemoryController.kt)
5. Each platform’s `platform.json` binding / allowlist

That sprawl is intentional for v1 (single product catalog). Platform-only enum labels belong in integration `valueMaps`, not in ControlCatalog.
