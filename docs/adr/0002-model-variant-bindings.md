# ADR-0002: SKU model + energy profile own product surface

## Status

Accepted (revised)

## Context

A single `models/phev.json` mixed **build/SKU** identity (`antora1000_p145_eu`) with **energy architecture** (`phev`). Those are different axes: the same Antora SoC can be flashed as `p145_eu` vs another market SKU, and each flash can be PHEV or BEV.

On device:

| Signal | Example | Layer |
|--------|---------|--------|
| `ro.product.device` | `antora1000_p145_eu` | **SKU** → `p145_eu` |
| `ro.product.model` | `Geely EX5` | Marketing label only |
| Fuel / hybrid VHAL | `INFO_FUEL_CAPACITY`, `hybrid_soc` | **Profile** → `phev` / `bev` |

## Decision

Three-level identity:

1. **Integration** (`antora1000`) — SoC / HU family; owns `platform.json` catalog (no `properties[].entity`).
2. **SKU** (`models/<id>.json`, e.g. `p145_eu`) — matched via `matchDevice` against `ro.product.device`; optional `properties` allowlist from Lab probe (which catalog keys become product bindings).
3. **Profile** (`profiles/<id>.json`, e.g. `phev`) — detected from VHAL; owns `detect`, `extraCapabilities`, and label. Does **not** list property bindings.

Runtime: `PlatformConfig.matchSku(fingerprint)` → `detectProfile` → `forSelection(skuId, profileId)` identity-binds every SKU-allowlisted property (binding key = VHAL property key).

- **Atomic entity id** = VHAL property key (`MIRROR_FOLD`, `PERF_VEHICLE_SPEED`).
- **Composites** keep HA ids (`climate.cabin`); attributes point at property keys.
- **Auto-entities**: catalog props not claimed by curated registry become cards via `CatalogEntityFactory`.
- **UI layout**: `group` = nav page; `section` = subsection (climate / lock / adas…); card widget still uses `domain`.

`PlatformVariant.id` = profile id; `PlatformVariant.skuId` = SKU id; label joins both (`P145 EU · PHEV / EM-i`).

## Consequences

- New market flash → new `models/<sku>.json` (probe allowlist), not a new integration.
- New energy layout → new/edited `profiles/*.json` (`detect` / capabilities), not a bindings dump.
- Marketing badges stay out of folder names.
