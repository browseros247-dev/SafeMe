# Release signing

How SafeMe release APKs are signed and verified. No release keystore is
committed — signing happens either from a local keystore on a developer
machine or, for real releases, from GitHub repository secrets via a manual
CI job.

## TWO signing keys exist — they are NOT interchangeable

| | CI key (canonical release) | Local dev key (this machine) |
|---|---|---|
| Source | GitHub secrets (`KEYSTORE_BASE64`) | `keystore/safeme-release.jks` (gitignored) |
| Certificate DN | `CN=SafeMe, OU=SafeMe, O=SafeMe, L=Dhaka, ST=Dhaka, C=BD` | `CN=SafeMe, O=SafeMe, C=BD` |
| SHA-256 fingerprint | `7635cda60fc38031613ef22ffb26674a5d0d6e01196bc8e32210e2b505399016` | `d3de8c1e81386bb5404befd2e53c532c1afa1900d38e0cbac190a2c37ef79576` |
| Pinned in | `ci.yml` `release-signing` job (`EXPECTED_CERT_SHA256`) | — |

An APK signed with one key **cannot** update an install made with the other —
Android rejects it with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the only fix
is a full uninstall (data loss). Verified 2026-08-21: the local keystore
produces `d3de8c1e…`, and the on-device install (vivo V2206) is also
`d3de8c1e…`-signed, so local builds update cleanly over each other. A CI
release installed on that device would require uninstalling first.

## CI key identity (canonical)

The fingerprint `7635cda6…9016` is the identity of a **CI release**. Every
genuine CI-built APK carries it, and CI refuses to upload an artifact signed
by any other key (pinned in `ci.yml`'s `release-signing` job as
`EXPECTED_CERT_SHA256`).

## Reference builds

| | CI build (2026-08-15) | Local build (2026-08-21) |
|---|---|---|
| APK | `app/build/outputs/apk/release/app-release.apk` | `artifacts/app-release.apk` |
| Size | 3,089,467 bytes (~2.9 MiB) | 3,091,611 bytes (~2.9 MiB) |
| SHA-256 | `48f7817d67d8c8873c3e513bf3c5aea29c71f6e6fa00adfa676835fe24e90ffb` | `a7c5fccd9846c5d0a399ff9310ff0c358303b87043dea136724e96351c73a85b` |
| Signed by | CI key (`7635cda6…`) | local key (`d3de8c1e…`) |

The APK SHA-256 changes on every rebuild (R8 minification, build inputs,
timestamps), so treat the certificate fingerprint above as the identity of a
release, not the file hash. `sha256sum` is useful for a quick integrity check
of a downloaded artifact, but only `apksigner` proves who signed it.

## Signing sources

### CI (recommended for releases)

`.github/workflows/ci.yml`, job `release-signing` — triggered **manually** via
Actions → Run workflow (`workflow_dispatch`), runs only on the `main` branch.
It consumes four repository secrets — `KEYSTORE_BASE64` (base64 of the
`.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — builds the release
APK, verifies the signature and key fingerprint, and uploads the result as the
`safeme-release-signed` artifact. No local machine or keystore is required.

### Local (developer machine)

`./gradlew :app:assembleRelease` with `keystore/safeme-release.jks` and
`keystore.properties` present (both gitignored). Intended for ad-hoc builds;
the CI path is the canonical way to produce a release.

**Note:** the local `keystore/safeme-release.jks` is a *different key* from the
CI secrets keystore (see the table above). Local builds are fine for testing
and ad-hoc installs, but they will not install over a CI release (or vice
versa) without uninstalling, and CI would reject them by fingerprint.

## Rules

- No keystore is committed. `keystore/safeme-release.jks`,
  `keystore.properties`, and the CI secrets must all stay out of the repo.
  Anyone holding a keystore can sign updates that install silently over
  existing installs made with that same key.
- Losing a keystore makes installs signed with it un-updatable: an APK signed
  with a different key cannot overwrite them without a full uninstall
  (data loss).
- All four signing secrets must be set together. CI fails loudly if any is
  missing rather than silently producing an unsigned APK, and the manual
  release job aborts if the built APK's key fingerprint does not match the
  pinned CI digest (`7635cda6…`).

## Verify an APK locally

```bash
# Proves who signed the APK (fingerprint must match the table above)
apksigner verify --print-certs app-release.apk

# Quick integrity check of a downloaded artifact
sha256sum app-release.apk
```
