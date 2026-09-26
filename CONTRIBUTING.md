# Contributing to Open Automotive Assistant

Thanks for helping improve Open Automotive Assistant. Doc map: [docs/README.md](docs/README.md).

## Prerequisites

- JDK 17
- Android SDK (API 28+ platform + build-tools); set `ANDROID_HOME` or `sdk.dir` in a local `local.properties` (never commit it)
- Optional: `adb` on `PATH` for head-unit install

## Build without a head unit

```bash
./gradlew :app:assembleDebug
```

Compiles against `libs/car-stubs`. For a fake vehicle without hardware, use Lab → **Integration override** → `demo` (see [docs/contributor-debug.md](docs/contributor-debug.md)).

## Install on a head unit

```bash
export OAA_HOST=CAR_IP   # or pass -H every time
./tools/oaa-setup -i antora1000 -H CAR_IP setup
```

`oaa-setup` force-stops the app, **pushes** the APK, then runs `pm install` on-device. Avoid plain `adb install` over wireless ADB on Antora — streamed install often hangs and drops the device offline.

See [README.md](README.md) and [docs/safety.md](docs/safety.md). Install is user-space `/data` only.

## Adding a platform integration

Follow [docs/adding-an-integration.md](docs/adding-an-integration.md): `integrations/<id>/` with `host.sh`, `platform.json`, ServiceLoader entry, optional Kotlin bridge. No core registry edits required for bindings.

- **`demo`** — in-memory; CI and laptop work
- **`ihu629g`** — thin CarProperty hardware reference
- **`antora1000`** — dual-backend / large catalog example

## Adding a plugin

Follow [docs/plugins.md](docs/plugins.md).

## Adding a first-party feature

Shell features under `features/` are **curated**. Follow [docs/adding-a-feature.md](docs/adding-a-feature.md).

## Pull requests

- Keep changes focused; prefer small PRs.
- Match existing Kotlin / JS style; no drive-by refactors.
- Do not commit: APKs, OEM platform keys, `local.properties`, `build/` outputs, or personal LAN IPs.
- Web static assets use `Cache-Control: no-store` (no cache-bust query params required).
- Run `./gradlew :app:assembleDebug` before opening a PR.
- By participating, you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

Contributions are licensed under the Apache License 2.0 (see [LICENSE](LICENSE)). See also [docs/disclaimer.md](docs/disclaimer.md).
