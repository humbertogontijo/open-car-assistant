# Disclaimer

Open Automotive Assistant is an independent community project. It is **not**
affiliated with, endorsed by, or supported by Geely, ECARX, Flyme Auto, Antora,
or any other vehicle / head-unit OEM.

## Owned hardware only

Install, VHAL writes, and debug tooling are intended only
for **head units you own** (typically unlocked / userdebug AAOS
devices). Do not use Open Automotive Assistant to modify vehicles or systems you do not control, or
to bypass manufacturer security on production locked devices.

## Reverse-engineered platform artifacts

Some integration modules include reverse-engineered or observed platform
details gathered from hardware the contributors own, for example:

- Vendor VHAL property tables (`platform.json` → `properties`)
- Writable allowlists for comfort / ADAS / charge settings
- Hand-rolled gRPC framing for on-device VenusVehicleServer (Antora)
- Flyme Auto / ECARX broadcast action strings used for wake and status-bar hooks

These artifacts exist so the app can talk to the same interfaces the stock UI
uses. They are provided **as-is**, without warranty, and may break when the OEM
updates the head unit. They are not an invitation to redistribute proprietary
OEM firmware, platform signing keys, or closed-source binaries.

## No OEM platform keys

This repository ships only the well-known AOSP **community testkey** (see
[NOTICE](../NOTICE) and [libs/signing/README.md](../libs/signing/README.md)). OEM /
platform signing keys are **not** included and must never be committed.

## Safety

Writable properties are limited by each integration’s `access` field in `platform.json` (`w` / `rw`). See
[safety.md](safety.md) for the network threat model and install paths.

## License

Apache License 2.0 — see [LICENSE](../LICENSE). Contributions are accepted under
the same terms ([CONTRIBUTING.md](../CONTRIBUTING.md)).
