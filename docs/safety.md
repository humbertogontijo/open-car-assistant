# Safety and ops

## Writable allowlist

Only properties with `access` `w` or `rw` in each integration's `platform.json` may be written via memory reapply, web controls, or `/debug` POST. Comfort / ADAS / charge / hybrid energy modes only — no steering, braking, or propulsion torque writes.

## Threat model / network

- Ktor binds to LAN (`0.0.0.0:8787`) with cleartext HTTP for the in-car WebView and same-LAN browsers. **Do not** expose the port to the public internet.
- Contributor writes and sensitive debug reads require a token when Contributor mode is on; the token may appear in `/debug` HTML while that mode is enabled.
- Host CLI installs under **`/data`** (user-space). Wireless ADB and optional on-device `su` (e.g. ADB toggle helpers) are for **owned userdebug** head units only — not production locked cars or third-party devices.
- Home Assistant long-lived access tokens are stored in app prefs when configured via the UI/API; they are not shipped in the repo. GET APIs return only a masked token hint.

## Redaction

- VIN is redacted in property reads and debug exports.

## Install model — user-space first

Product tooling and the shell assume a normal **`/data`** install with the community testkey (uninstallable from HU Settings).

| Platform | Default VHAL path | Notes |
|----------|-------------------|--------|
| **antora1000** | gRPC → VenusVehicleServer `127.0.0.1:40004` | No `CAR_VENDOR_EXTENSION` needed. |
| **ihu629g** | `CarPropertyManager` via shared `CarPropertyBackend` | Uses runtime/install car permissions available to a user app. |

Platforms that need formal `signature\|privileged` grants (priv-app whitelist, OEM platform key, etc.) own that in their integration — shared means such as `CarPropertyBackend` remain available. Core `oca-setup` and the in-app setup UI do **not** elevate to `/system/priv-app`.

### Host setup

Replace `CAR_IP` with the HU address (required — there is no default LAN IP).

```bash
# /data install + runtime grants (uninstallable)
./tools/oca-setup -i antora1000 -H CAR_IP setup
```

Web UI setup (`/api/setup/actions/*`): request runtime permissions and show host install hints.

### Camera note

Live and DVR share one GPU path: camera `SurfaceTexture` → GLES mosaic → HW `MediaCodec` H.264. Live is HLS (CMAF); recordings are `MediaMuxer` `.mp4`. Legacy `.mjpeg` / `.seg` files remain readable if present.

## Multi-app VHAL writers

Prefer a single writer for regen / drive mode / ADAS toggles when other apps also talk to VenusVehicleServer or `CarPropertyManager`.

## Parking Comfort / DVR

DVR runs in the foreground `AssistantService` (`camera` FGS type). Modes:

- **Off** — not writing (live mosaic preview still available in the Cameras UI)
- **DVR** — continuous rotate (~5 min / 100 MB per file under `dvr/`); **auto-starts on ACC/boot wake** and **stops on screen-off / vendor sleep** (Parking Comfort–safe; wake/sleep debounced ~5 s). Retention prunes oldest unlocked files by max total size and optional max age. Locked files and segment `.meta` (wall-clock `startUtcMs` / `durationMs`) live under app `files/dvr-meta/` because public `Movies/` volumes reject non-media sidecars. The cameras UI shows a **day-scoped wall-clock timeline** over segments; scrub seeks via `/api/dvr/play?atMs=`, and **Cut** remuxes a wall-clock range (possibly multi-file, sealing the active segment when needed) via `/api/dvr/cut` and **downloads** the clip to the client (not stored on the HU).

Save targets: app files, internal Movies/`OpenCarAssistant`, and mounted SD/USB volumes under `OpenCarAssistant/` (continuous files always in a `dvr/` subdir).

**Shared mosaic stream:** GPU path only — camera `SurfaceTexture` → GLES → HW `MediaCodec` → H.264. Mosaic output size is computed from each camera’s native preview size and the grid layout (no platform `dvr.fps` / `mosaicHeight`). Live UI: **HLS** (`/api/dvr/live.m3u8` + CMAF) via **hls.js** on one `<video>`. DVR: `MediaMuxer` `.mp4`. Status: measured `stream.size` / `fps`, `h264.*`, `camera2Probe`. Role entities (`camera.front`, …) come from `platform.json` → `cameras[]`.

**Camera2 probe (Antora, measured):** all four ids `0–3` open concurrently with preview Surfaces (`allOpened=true`, hardware level `limited`, preview `640x480`). No `LOGICAL_MULTI_CAMERA` ids reported. Vendor mosaic Surface not found via Camera2 caps — appside merge still required.
