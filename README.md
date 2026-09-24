# Open Car Assistant

Capability-based AAOS platform for Flyme Auto head units (Antora 1000 / SE1000, IHU629G, and future integrations).

**Canonical repository name:** `open-car-assistant`  
Package: `cc.opencar.assistant` · Web UI: `http://CAR_IP:8787` (LAN) or `http://127.0.0.1:8787` on the HU

License: [Apache-2.0](LICENSE) · See [NOTICE](NOTICE) for community testkey disclosure · [Disclaimer](docs/disclaimer.md) · [Code of Conduct](CODE_OF_CONDUCT.md) · [Security](SECURITY.md)

## Build without a head unit

Requires **JDK 17** and an **Android SDK** (`ANDROID_HOME`, or `sdk.dir` in a local `local.properties` — never commit that file).

```bash
./gradlew :app:assembleDebug
```

This compiles against `libs/car-stubs`. Live vehicle property reads/writes need a real AAOS head unit.

## Install on a head unit (optional)

From a computer on the same LAN as an unlocked / userdebug HU you own:

```bash
# Antora / SE1000 (EX5 family) — dual backend (gRPC + CarProperty)
./tools/oca-setup -i antora1000 -H CAR_IP setup

# IHU629G (BR/CN EX2) — CarProperty only; simpler reference for new platforms
./tools/oca-setup -i ihu629g -H CAR_IP setup
```

This builds, signs with the [community testkey](libs/signing/README.md), installs under **`/data`** (uninstallable), grants runtime car/camera permissions, and prints a permission report.

Optional later — formal `CAR_VENDOR_EXTENSION` / `CONTROL_CAR_CLIMATE` via priv-app (reboot required; not uninstallable from Settings):

```bash
./tools/oca-setup -i antora1000 -H CAR_IP setup --privileged
adb -s CAR_IP:5566 reboot
# after boot:
./tools/oca-setup -i antora1000 -H CAR_IP grant
./tools/oca-setup -i antora1000 -H CAR_IP check
```

`CAR_IP` and ADB port/user come from `-H` / `OCA_HOST` (required) and each integration’s `host.sh` defaults for port and Android user. See [docs/safety.md](docs/safety.md).

Other commands: `connect` · `build` · `sign` · `install` · `grant` · `check` · `start` · `uninstall`

## Contributor paths

| Goal | How | Locality |
|------|-----|----------|
| New **vehicle platform** | [docs/adding-an-integration.md](docs/adding-an-integration.md) — copy `integrations/<id>/`; use **ihu629g** as the simple reference | One folder tree (auto Gradle + ServiceLoader) |
| New **external plugin** | [docs/plugins.md](docs/plugins.md) — `plugins/<id>/` + `OcaPlugin` | One folder tree (auto-discovered) |
| New **shell feature** | [docs/adding-a-feature.md](docs/adding-a-feature.md) — curated `features/<id>/` | Multi-file (Gradle + `AssistantRuntime` + often web) |
| Install / debug on HU | [docs/contributor-debug.md](docs/contributor-debug.md) — Lab tab + `oca-setup` | — |

**Design:** integrations and plugins are plug-and-play. First-party features and the product control catalog are curated on purpose.

## Threat model (short)

- The in-car Ktor server listens on **cleartext** `0.0.0.0:8787` for HU WebView and same-LAN browsers. Do **not** expose that port to the public internet.
- Contributor debug mode can show a short token in Lab / `/debug` HTML when enabled.
- Host tools may use `adb root` / remount / priv-app and on-device `su` elevate — intended only for **owned userdebug** head units.
- Details: [docs/safety.md](docs/safety.md) · [docs/disclaimer.md](docs/disclaimer.md).

## Docs

- [Architecture](docs/architecture.md)
- [Contributing](CONTRIBUTING.md)
- [Contributor debug](docs/contributor-debug.md)
- [Adding an integration](docs/adding-an-integration.md)
- [Adding a feature](docs/adding-a-feature.md)
- [Plugins](docs/plugins.md)
- [Safety / privileges](docs/safety.md)
- [Disclaimer](docs/disclaimer.md)
- [App store catalog](docs/store.md)
