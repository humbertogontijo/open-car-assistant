# Contributing to Open Car Assistant

Thanks for helping improve OCA.

## Prerequisites

- JDK 17
- Android SDK (API 28+ platform + build-tools); set `ANDROID_HOME` or `sdk.dir` in a local `local.properties` (never commit it)
- Optional: `adb` on `PATH` for head-unit install

## Build without a head unit

From the repository root:

```bash
./gradlew :app:assembleDebug
```

This compiles the app and integrations against `libs/car-stubs`. Vehicle reads/writes need a real AAOS head unit (or a device with matching VHAL).

## Install on a head unit

```bash
export OCA_HOST=CAR_IP   # or pass -H every time
./tools/oca-setup -i antora1000 -H CAR_IP setup
```

`oca-setup` force-stops the app, **pushes** the APK, then runs `pm install` on-device. Avoid plain `adb install` over wireless ADB on Antora — streamed install often hangs at “Performing Streamed Install” and drops the device offline (worse when DVR holds cameras open).

See [README.md](README.md) and [docs/safety.md](docs/safety.md). Install is user-space `/data` only. Platforms that need formal privileged grants handle that in their integration (shared `CarPropertyBackend` is available; core tooling does not elevate).

## Adding a platform integration

Follow [docs/adding-an-integration.md](docs/adding-an-integration.md): add `integrations/<id>/` with `host.sh`, `platform.json`, ServiceLoader entry, and optional Kotlin bridge. No core registry edits. Use **`ihu629g`** as the simple CarProperty reference; `antora1000` is the gRPC (user-space) example.

## Adding a plugin

Follow [docs/plugins.md](docs/plugins.md): add `plugins/<id>/` implementing `OcaPlugin` plus a ServiceLoader entry. System UI and shortcuts pick it up automatically.

## Adding a first-party feature

Shell features under `features/` are **curated** (not auto-discovered). Follow [docs/adding-a-feature.md](docs/adding-a-feature.md).

## Pull requests

- Keep changes focused; prefer small PRs.
- Match existing Kotlin / JS style; no drive-by refactors.
- Do not commit: APKs, OEM platform keys, `local.properties`, `build/` outputs, or personal LAN IPs.
- If you touch web assets, bump or regenerate cache-bust query params as needed until a hash-based scheme lands.
- Run `./gradlew :app:assembleDebug` before opening a PR.
- By participating, you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0 (see [LICENSE](LICENSE)). See also [docs/disclaimer.md](docs/disclaimer.md).
