## Summary

<!-- What does this PR change and why? -->

## Type of change

- [ ] Bug fix
- [ ] New platform integration (`integrations/<id>/` only)
- [ ] New plugin (`plugin-<id>/` only)
- [ ] Feature / shell / web UI
- [ ] Docs / CI / tooling

## Checklist

- [ ] `./gradlew :app:assembleDebug` passes locally
- [ ] No OEM platform keys, `local.properties`, APKs, or personal LAN IPs
- [ ] Integration/plugin PRs stay under their folder tree when possible
- [ ] New controls: `WellKnownProperties` + `ControlCatalog` + common i18n (+ `platform.json` bindings)
- [ ] Hardware-related: tested with `./tools/oca-setup -i <id> -H <ip> …` (or noted why not)

## Test plan

<!-- How reviewers can verify -->
