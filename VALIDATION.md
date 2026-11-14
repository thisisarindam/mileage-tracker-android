# Validation — 14 November 2026

## Built and inspected

- `./gradlew assembleDebug`: **passed**, including a repeat run after the final source changes. Full Temurin JDK 17.0.21+9, Gradle 8.9, Android SDK 35, Build Tools 35.0.0.
- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk` (about 8.8 MiB).
- APK SHA-256: `9bdd566d9768dd29c1f9941d8c77ca6049dc8bf538a287bc223bdd795aae1580`.
- `apksigner verify --verbose`: **passed**, one signer, APK Signature Scheme v2.
- `aapt dump permissions`: no `INTERNET`, broad storage, or account permissions. Only the AndroidX signature-protected internal receiver permission is present.
- Packaged manifest inspection: `allowBackup=false`, `fullBackupContent=false`, data-extraction rules exclude cloud and device-transfer data, minSdk 26 / targetSdk 35. No Supabase SDK, remote assets, WebView or Capacitor code.
- `lintDebug`: **no issues found**, with the temporary local test-runtime exclusion described below. No app lint checks were disabled.
- Source is pushed to `codex/mileage-tracker-native` in draft PR #1. Nothing has been merged into `main`; the reference web repository is unchanged. The generated Windows Gradle launcher retains its standard CRLF endings.

## Latest test outcomes after environment publication

The standard command `./gradlew testDebugUnitTest assembleDebug` **passed** on 14 November 2026 after restoring the published environment. There were **13 executed tests, zero failures, zero errors and zero skipped tests**. No dependency exclusions, filtered suites, or test workarounds were used for this run.

- **RulesTest: 7 passed** — both original mileage denominators, per-fill rules, manual-cost rounding edge cases, zero/equal odometers, monthly spending, BOM/CRLF/quoted/multiline CSV, malformed CSV, reordered headers, invalid rows, source duplicate detection, exact decimal/timestamp preservation, synthetic fuel/maintenance export round trips, and strict JSON round trip/validation.
- **StoreTest: 5 passed** — fuel and maintenance duplicate imports preserve local edits, including stale previews; full Room backup/restore retains preferences and source IDs; invalid backup leaves all stored data intact; an injected SQLite insertion failure rolls back the restore transaction.
- **OfflineStartupTest: 1 passed** — MainActivity starts under Robolectric and the manifest has no Internet permission or automatic backup.

The original implementation run could execute only seven pure JVM tests because Maven redirected Robolectric runtime downloads to a domain outside the cloud allowlist. That prerequisite is now resolved: the full framework/native artifacts downloaded and all enabled tests ran normally. The previous subset-only result is superseded by this full run.

Generated current-run reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/test-results/testDebugUnitTest/`. These are ignored build outputs.

## Cloud development commands

```sh
source /workspace/tooling/activate-mileage-android.sh
cd /workspace/mileage-tracker-android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The published snapshot retains the verified JDK/SDK tooling, proxy CA trust, Gradle dependencies and writable test-cache configuration outside the repository. The cache configuration only redirects test-JVM cache storage; it does not disable any tests or dependencies. TLS and artifact verification remain enabled. No personal records or Supabase credentials were used as fixtures or bundled.

## GitHub review and CI

Draft PR: https://github.com/thisisarindam/mileage-tracker-android/pull/1

The push workflow runs unit tests and builds a debug APK. Follow the PR checks or the repository Actions tab for the latest remote result and the `mileage-tracker-debug` artifact after a successful run. The first remote run revealed that the setup action's default `tools` package has been removed from Google's SDK repository; the workflow now explicitly requests `platform-tools` and installs Platform 35 / Build Tools 35.0.0.

## Not verified here

No physical Nothing Phone 2a or Android emulator was available. Installation, first-launch airplane-mode UI behavior, Android document-provider interactions, rotation/keyboard behavior, and scrolling performance on the phone require the README acceptance checklist. GitHub checks report remote CI outcomes separately. APK assembly and Robolectric tests do not establish physical-device behavior.
