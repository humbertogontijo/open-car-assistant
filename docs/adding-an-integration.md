# Adding a platform integration

Add a new module only when the **hardware/software platform** differs (new SoC / HU family). Do **not** add a module for a new badge or market trim of the same chip.

Collaborator work stays under `integrations/<platform-id>/`. Gradle auto-includes the folder and wires it into `:app`. Runtime discovery uses Java ServiceLoader — no edits to `AssistantRuntime`, `settings.gradle.kts`, or `app/build.gradle.kts`.

## Folder layout (copy this)

```
integrations/<platform-id>/
  host.sh                      # oca-setup defaults (ADB host/port/user)
  privapp-permissions.xml      # privileged whitelist template
  build.gradle.kts
  src/main/assets/
    platform.json              # match, bindings, allowlist, variants, driveModeEnum
    vhal_named_ids.tsv         # optional catalog (VHAL backends)
    i18n/<platform-id>/        # optional overrides + valueMaps (en.json, pt-BR.json)
  src/main/java/...            # thin Integration + bridge only when needed
  src/main/resources/META-INF/services/
    cc.opencar.assistant.api.VehicleIntegration   # FQCN of your implementation
```

Shared layers (not auto-registered as integrations):

```
integrations/platform/common/   # AAOS plumbing: PlatformConfig, CarPropertyBridge, …
integrations/platform/flyme/    # Flyme Auto family helpers
support/                        # :oca-support — I18nBundle, LastKnownStore (product helpers)
```

## Steps

1. Create `integrations/<platform-id>/` as above.
2. Fill `platform.json` (`backend`: `vhal`). Prefer config over Kotlin for bindings/allowlist/match. Copy structure from **`ihu629g`** (simple) rather than Antora when starting out.
3. Implement `VehicleIntegration` (+ optional `warm`, `createQuickEntry`, `wakeSignals`) on `VehiclePropertyBackend` / `CarPropertyBackend`.
4. Keep a **writable allowlist** in JSON for memory / web writes.
5. Register the class in `META-INF/services/cc.opencar.assistant.api.VehicleIntegration` (one FQCN per line).
6. Host setup: `./tools/oca-setup -i <platform-id> -H <ip> setup` (loads `integrations/<id>/host.sh`).
7. i18n: reuse common keys from `:oca-support`; add platform packs only for overrides / valueMaps (see below).

## Internationalization

Common strings live in `:oca-support` assets:

```
i18n/common/en.json
i18n/common/pt-BR.json
```

Per-integration packs (path includes the id so APK asset merge does not collide):

```
i18n/<platform-id>/en.json
i18n/<platform-id>/pt-BR.json
```

JSON shape:

```json
{
  "strings": {
    "control.elka": "Emergency lane assist",
    "control.oem_special": "Platform-only control"
  },
  "valueMaps": {
    "drive_mode": {
      "1": "drive_mode.eco",
      "6": "drive_mode.normal"
    }
  }
}
```

- **Reuse common keys** for shared controls (`control.*`, `opt.*`, `nav.*`, `sensor.*`). Integrations do not need to redefine them.
- **Override** a common key in the integration pack when the platform wording differs.
- **valueMaps** map live numeric/string values → i18n keys (resolved for segments, telemetry, and `valueLabel` on controls).
- `platform.json` `driveModeEnum` values should be **i18n keys** (e.g. `"6": "drive_mode.normal"`), not localized literals.
- Product `ControlDef` uses `labelKey` / `hintKey` defaults `control.<id>` / `control.<id>.hint`.
- Locale: `GET /api/i18n`, `POST /api/locale`, Sistema → Idioma (pt-BR / en).
- New OEM enum literals belong in `platform.json` / valueMaps, not in `ControlCatalog`.

## Variants vs new modules

| Situation | Action |
|-----------|--------|
| Same chip, PHEV vs BEV props | `variants` in `platform.json` |
| Same chip, one market missing a setting | binding override / variant |
| New SoC / different VHAL family | New `integrations/<id>/` |
| BR/CN EX2 (IHU629G) | `integrations/ihu629g` (VHAL / CarProperty) |
| AU EX2 (Antora) | Same `antora1000` module |

## Lab override

Lab tab → **Integration override** (or `POST /api/lab/integration-override` with `id=<platform>`). Forces a specific integration id (e.g. `ihu629g` on an Antora HU to test registry/UI without that hardware). **Force-stop or reboot** after applying so runtime rematches. See [contributor-debug.md](contributor-debug.md).
