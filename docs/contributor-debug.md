# Contributor remote debug

Anyone on the car LAN can inspect OCA state, pull logs, and attach Android Studio without reverse-engineering the HU alone.

## 1. Wireless ADB

1. On the HU: enable Developer options → Wireless debugging (or an existing Wi‑Fi ADB setup).
2. From your laptop:

```bash
adb connect CAR_IP:5566
adb devices
```

3. Android Studio → **Attach Debugger to Android Process** → package `cc.opencar.assistant` (or `.debug` / `.contributor` suffix).

Debug and contributor build types ship with `android:debuggable=true`. Release stays non-debuggable.

OCA debug is app-level and works with package-install rights only.

## 2. Contributor mode (Lab)

Open the product UI → **Lab** tab (`/lab`).

1. Toggle **Contributor mode** on. On `userdebug` / test-keys HUs this defaults on; on stock `user` builds you must enable it here.
2. The short LAN **token** appears next to the toggle (also on `/debug` HTML and `GET /api/lab`).
3. Contributor mode unlocks `/debug` writes and sensitive reads behind that token.

APIs:

| Method | Path | Body | Notes |
|--------|------|------|-------|
| GET | `/api/lab` | — | contributor, token, integrationOverride, integrations[] |
| POST | `/api/lab/contributor` | `enabled=1` or `0` | form-urlencoded |
| POST | `/api/lab/integration-override` | `id=<platform>` or empty | form-urlencoded; **restart required** |

### Integration override

Lab → **Integration override** forces a specific `integrations/<id>/` match (e.g. test `ihu629g` UI on an Antora HU). Apply, then **force-stop** the app or reboot so `AssistantRuntime` rematches.

adb fallback after Lab Apply (force rematch):

```bash
# Adjust package suffix for debug / contributor builds
adb shell am force-stop cc.opencar.assistant.debug
adb shell monkey -p cc.opencar.assistant.debug -c android.intent.category.LAUNCHER 1
```

Prefs key: `oca_runtime` / `integration_override` (set via Lab or `POST /api/lab/integration-override`).

## 3. HTTP `/debug` API

Base: `http://CAR_IP:8787`

| Route | Notes |
|-------|--------|
| `GET /debug` | HTML status: platform links, token hint |
| `GET /api/lab` | Lab snapshot (preferred for UI) |
| `GET /debug/logs?token=…` | Ring-buffer app logs |
| `WS /debug/logs/stream?token=…` | Live log stream |
| `GET /debug/integration` | Matched integration, variant, catalog size |
| `GET /debug/props?token=…&q=` | Safe catalog browse |
| `GET /debug/props/{key}?token=…` | Read one property (VIN redacted) |
| `POST /debug/props/{key}?token=…` | Write if allowlisted + contributor |
| `GET /debug/export?token=…` | Zip: logs, identity, capabilities, telemetry |
| `GET /debug/adb-hint` | Wi‑Fi IP + suggested `adb connect` |

Token is required when Contributor mode is on. Without Contributor mode, write/debug routes refuse (probe summary may still run for Lab re-probe when mode is off).

### VHAL catalog vs product entities

Lab → **VHAL catalog** lists every property from `platform.json` → `properties` (after `extends` merge). Each row may show an **Entity** id when that native id has an `entity` field.

| Filter | Meaning |
|--------|---------|
| **All** | Full HU / platform catalog |
| **Bound (cards)** | Props already mapped to a product control (`entity`) |
| **Missing** | On the car / in `properties`, but not yet a card |

**Product entities** (cards) stay curated: add a human description in i18n (`control.<id>` / hint), a `ControlDef` in [ControlCatalog.kt](../features/web/src/main/java/cc/opencar/assistant/feature/web/ControlCatalog.kt), a `WellKnownProperties` entry, and a `platform.json` property with `entity` (+ `access: "rw"` if writable). See [adding-a-feature.md](adding-a-feature.md) “New product controls”.

Probe summary includes `boundEntities` / `unbound` counts. Re-probe after updating `platform.json` (`force=1`).

## 4. Build types

| Type | Debuggable | Debug routes |
|------|------------|--------------|
| `debug` | yes | contributor default-on for userdebug |
| `contributor` | yes (release-signed style) | on — for sharing test APKs |
| `release` | no | off unless Contributor mode + token |

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleContributor
./gradlew :app:assembleRelease
```

## 5. Filing a bug

1. Enable Contributor mode in Lab, note the token.
2. Open `http://CAR_IP:8787/debug/export?token=TOKEN` (or use **Export zip** in Lab).
3. Attach the zip to a GitHub issue (VIN already redacted).
4. Include platform id + variant from `/debug/integration` or About.

## 6. Adding a platform integration

See [adding-an-integration.md](adding-an-integration.md). Prefer **ihu629g** as the simple CarProperty-only reference.
