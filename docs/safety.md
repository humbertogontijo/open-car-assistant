# Safety and ops

## Writable allowlist

Only properties listed in each integration's `platform.json` `writableAllowlist` may be written via memory reapply, web controls, or `/debug` POST. Comfort / ADAS / charge / hybrid energy modes only — no steering, braking, or propulsion torque writes.

## Threat model / network

- Ktor binds to LAN (`0.0.0.0:8787`) with cleartext HTTP for the in-car WebView and same-LAN browsers. **Do not** expose the port to the public internet.
- Contributor writes and sensitive debug reads require a token when Contributor mode is on; the token may appear in `/debug` HTML while that mode is enabled.
- Host CLI may use `adb root`, remount, and priv-app install; the on-device UI may attempt `su` elevate. These paths are for **owned userdebug / remountable** head units only — not production locked cars or third-party devices.
- Home Assistant long-lived access tokens are stored in app prefs when configured via the UI/API; they are not shipped in the repo. GET APIs return only a masked token hint.

## Redaction

- VIN is redacted in property reads and debug exports.

## Install model (Antora) — two VHAL interfaces

Antora exposes **two backends**; the session picks one at connect time:

| Install | Backend | How |
|---------|---------|-----|
| **Unprivileged** `/data` (default) | **gRPC** `vhal_proto.VehicleServer` on `127.0.0.1:40004` (VenusVehicleServer) | Plaintext OkHttp channel, `client_id` / `session_id` metadata, bidi `SetProperty` + property value stream. No `CAR_VENDOR_EXTENSION` needed. |
| **Privileged** priv-app / platform key | **CarPropertyManager** | Formal `CAR_VENDOR_EXTENSION` + `CONTROL_CAR_CLIMATE` grants via privapp whitelist. |

**Default:** normal `/data` install with the community testkey. That keeps the app uninstallable from HU Settings and uses the gRPC path for vendor / drive-mode / climate writes.

`android.car.permission.CAR_VENDOR_EXTENSION` and `CONTROL_CAR_CLIMATE` remain **`signature|privileged`**. They are only required when you opt into the CarPropertyManager backend (priv-app).

| Path | Formal vendor/HVAC grants? | Notes |
|------|----------------------------|-------|
| Normal `adb install` + testkey (`:signing:signApk`) | **No** (uses gRPC) | Recommended default. Community testkey fingerprint `d7f1f224`. Runtime `CAR_SPEED` / `CAR_ENERGY` + install `CAR_INFO` / `CAR_POWERTRAIN`. |
| `pm grant … CAR_VENDOR_EXTENSION` | **No** | Not a changeable/runtime permission. |
| OEM **platform** signature | Yes → CarProperty | OEM platform keys are **not** in this repo. |
| **`/system/priv-app` + privapp whitelist XML** | Yes → CarProperty | Optional. Unlocked/userdebug HUs with `adb root` + `remount`. App cannot be uninstalled from Settings. |

The unprivileged `/data` + testkey install does **not** receive `CAR_VENDOR_EXTENSION`; it talks to VenusVehicleServer over gRPC, not `CarPropertyManager`.

### Host setup (recommended)

Replace `CAR_IP` with the HU address (required — there is no default LAN IP).

```bash
# Default: /data install + runtime grants (uninstallable)
./tools/oca-setup -i antora1000 -H CAR_IP setup

# Optional later: formal vendor + HVAC privileges (priv-app, reboot required)
./tools/oca-setup -i antora1000 -H CAR_IP setup --privileged
adb -s CAR_IP:5566 reboot
# after boot:
./tools/oca-setup -i antora1000 -H CAR_IP grant
./tools/oca-setup -i antora1000 -H CAR_IP check
./tools/oca-setup -i antora1000 -H CAR_IP start
```

`install-privileged` / in-app elevate will:

1. `adb root` + remount (or `su` from the UI on userdebug)
2. Uninstall the `/data` copy when using host CLI
3. Push APK to `/system/priv-app/OpenCarAssistant/`
4. Install `privapp-permissions.xml` from `integrations/<id>/`
5. Require reboot so PackageManager applies `PRIVILEGED`

Web UI setup (`/api/setup/actions/*`): request runtime permissions; elevate/host commands stay available but are optional.

### Camera note

Live and DVR share one GPU path: camera `SurfaceTexture` → GLES mosaic → HW `MediaCodec` H.264. Live is HLS (CMAF); recordings are `MediaMuxer` `.mp4`. Legacy `.mjpeg` / `.seg` files remain readable if present.

## Multi-app VHAL writers

Prefer a single writer for regen / drive mode / ADAS toggles when other apps also talk to VenusVehicleServer or `CarPropertyManager`. Only elevate to priv-app if you want the CarPropertyManager backend (formal grants) — the default gRPC path works with the community testkey `/data` install.

## Parking Comfort / DVR

DVR runs in the foreground `AssistantService` (`camera` FGS type). Modes:

- **Off** — not writing (live mosaic preview still available in the Cameras UI)
- **DVR** — continuous rotate (~5 min / 100 MB per file under `dvr/`); **auto-starts on ACC/boot wake** and **stops on screen-off / vendor sleep** (Parking Comfort–safe; wake/sleep debounced ~5 s). Retention prunes oldest unlocked files by max total size and optional max age. Locked files and segment `.meta` (wall-clock `startUtcMs` / `durationMs`) live under app `files/dvr-meta/` because public `Movies/` volumes reject non-media sidecars. The cameras UI shows a **day-scoped wall-clock timeline** over segments; scrub seeks via `/api/dvr/play?atMs=`, and **Cut** remuxes a wall-clock range (possibly multi-file, sealing the active segment when needed) via `/api/dvr/cut` and **downloads** the clip to the client (not stored on the HU).

Save targets: app files, internal Movies/`OpenCarAssistant`, and mounted SD/USB volumes under `OpenCarAssistant/` (continuous files always in a `dvr/` subdir).

**Shared mosaic stream:** GPU path only — camera `SurfaceTexture` → GLES → HW `MediaCodec` → H.264. Live UI: **HLS** (`/api/dvr/live.m3u8` + CMAF) via **hls.js** on one `<video>`. DVR: `MediaMuxer` `.mp4`. Status: `stream.format`, `h264.*`, `camera2Probe`.

**Camera2 probe (Antora, measured):** all four ids `0–3` open concurrently with preview Surfaces (`allOpened=true`, hardware level `limited`, preview `640x480`). No `LOGICAL_MULTI_CAMERA` ids reported. Vendor mosaic Surface not found via Camera2 caps — appside merge still required.
