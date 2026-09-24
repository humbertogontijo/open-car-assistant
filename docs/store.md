# App store catalog

The in-app store reads [`features/install/src/main/assets/store/extras.json`](../features/install/src/main/assets/store/extras.json).

Default entries:

| App | How it installs |
|-----|-----------------|
| F-Droid | Direct APK from f-droid.org |
| MicroG RE / YouTube Morphe | Latest matching asset from public GitHub releases |
| Waze | Opt-in: set `apkUrl` to a mirror you control (empty by default; APK is not redistributed in this repo) |

You can also sideload with `adb install` or use the Upload APK control in System.

Do not commit proprietary APK blobs into this repository — only catalog metadata and download URLs / release resolvers.
