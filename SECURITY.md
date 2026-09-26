# Security Policy

## Supported versions

Security fixes are applied on the `main` branch of this repository (`open-automotive-assistant`). There are no long-lived release branches yet; report issues against the latest commit.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security-sensitive reports (remote code execution, auth bypass on the LAN UI, privilege escalation beyond intended userdebug tooling, leaked OEM keys, etc.).

Instead:

1. Email the maintainers using the address listed on the GitHub organization / repository profile, with subject `Open Automotive Assistant security: …`.
2. Include a short description, affected version/commit, and steps to reproduce on hardware you own (or a clear PoC that does not require production OEM secrets).
3. Allow a reasonable time for a fix before public disclosure.

We will acknowledge receipt when we can and coordinate a fix or public advisory.

## Expected threat model

Open Automotive Assistant is designed for **owned userdebug / remountable AAOS head units** on a trusted LAN:

- The in-car Ktor server listens on cleartext `0.0.0.0:8787`. Do not expose that port to the public internet.
- Contributor debug mode can show a short LAN token in the Lab UI and `/debug` HTML.
- Host tooling installs under `/data` (user-space). Optional on-device `su` helpers (e.g. wireless ADB) are only for hardware you own.

See [docs/safety.md](docs/safety.md) and [docs/disclaimer.md](docs/disclaimer.md).

## Secrets in this repository

- The AOSP **community testkey** under `libs/signing/` is intentional and disclosed in [NOTICE](NOTICE).
- OEM / platform signing keys must never be committed.
