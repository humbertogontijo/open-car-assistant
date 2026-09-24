# App store catalog

The in-app store reads [`features/install/src/main/assets/store/extras.json`](../features/install/src/main/assets/store/extras.json), then fills the rest from F-Droid (browse recent apps with an empty query; search when typing).

Default curated extras:

| App | How it installs |
|-----|-----------------|
| MicroG RE / YouTube Morphe | Latest matching asset from public GitHub releases |
| Waze | Latest APK via [Aptoide getMeta](https://ws75.aptoide.com/api/7/app/getMeta?package_name=com.waze) (`source: aptoide`) |

Extras entries should set `iconUrl` so list/detail rows show icons (F-Droid hits already include icons from the repo).

`source: fdroid_repo` is supported when an entry sets `repoUrl` to a repo that actually indexes that `packageName`. Do **not** add generic F-Droid mirrors just to have more repos — only wire a repo if it hosts one of our curated apps (Morphe MicroG RE, YouTube Morphe, or Waze).

As of 2026-09, no public F-Droid-compatible repo indexes those three packages (Morphe F-Droid is still an [open request](https://github.com/MorpheApp/MicroG-RE/issues/124); official microG at `repo.microg.org` is a different package). Keep GitHub / Aptoide until such a repo exists.

You can also sideload with `adb install` or use the Upload APK control in System.

Do not commit proprietary APK blobs into this repository — only catalog metadata and download URLs / release resolvers.
