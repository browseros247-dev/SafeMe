# Release signing

How SafeMe release APKs are signed and verified. The release keystore is
**never** committed — signing happens either from a local keystore on a
developer machine or, for real releases, from GitHub repository secrets via a
manual CI job.

## Release key identity

- **Signer certificate DN:** `CN=SafeMe, OU=SafeMe, O=SafeMe, L=Dhaka, ST=Dhaka, C=BD`
- **Certificate SHA-256 digest (fingerprint):** `7635cda60fc38031613ef22ffb26674a5d0d6e01196bc8e32210e2b505399016`

The fingerprint is **stable across builds** — it identifies the SafeMe release
key, not a particular APK. Every genuine release APK carries this fingerprint,
and CI refuses to upload an artifact signed by any other key (the key digest is
pinned in `ci.yml`'s `release-signing` job).

## Reference build (local, 2026-08-15)

| | |
|---|---|
| APK | `app/build/outputs/apk/release/app-release.apk` |
| Size | 3,089,467 bytes (~2.9 MiB) |
| SHA-256 | `48f7817d67d8c8873c3e513bf3c5aea29c71f6e6fa00adfa676835fe24e90ffb` |

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

## Rules

- The release keystore (`keystore/safeme-release.jks`) and
  `keystore.properties` are gitignored and must **never** be committed. Anyone
  holding the keystore can sign updates that install silently over existing
  SafeMe releases.
- Losing the keystore makes installed releases un-updatable: an APK signed
  with a different key cannot overwrite an existing install without a full
  uninstall (data loss).
- All four signing secrets must be set together. CI fails loudly if any is
  missing rather than silently producing an unsigned APK, and the manual
  release job aborts if the built APK's key fingerprint does not match the
  pinned SafeMe digest.

## Verify an APK locally

```bash
# Proves who signed the APK (fingerprint must match the table above)
apksigner verify --print-certs app-release.apk

# Quick integrity check of a downloaded artifact
sha256sum app-release.apk
```
