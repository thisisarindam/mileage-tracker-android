# Validation — 14 November 2026

## Built and inspected

- `./gradlew assembleDebug`: **passed**, including a repeat run after the final source changes. Full Temurin JDK 17.0.21+9, Gradle 8.9, Android SDK 35, Build Tools 35.0.0.
- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk` (about 8.8 MiB).
- APK SHA-256: `9bdd566d9768dd29c1f9941d8c77ca6049dc8bf538a287bc223bdd795aae1580`.
- `apksigner verify --verbose`: **passed**, one signer, APK Signature Scheme v2.
- `aapt dump permissions`: no `INTERNET`, broad storage, or account permissions. Only the AndroidX signature-protected internal receiver permission is present.
- Packaged manifest inspection: `allowBackup=false`, `fullBackupContent=false`, data-extraction rules exclude cloud and device-transfer data, minSdk 26 / targetSdk 35. No Supabase SDK, remote assets, WebView or Capacitor code.
- `lintDebug`: **no issues found**, with the temporary local test-runtime exclusion described below. No app lint checks were disabled.
- `git diff --check`: passed. No commits, pushes, merges, or original-web-repository modifications were made.

## Test outcomes

All 13 unit tests compile. The seven tests in `RulesTest` **executed and passed**:

1. Original dashboard vs analytics denominators, per-fill mileage and web form rounding edge cases.
2. Equal odometers, zero litres and monthly spending.
3. CSV BOM/CRLF, commas, quotes, embedded newlines and physical row numbers.
4. Malformed CSV rejection.
5. Reordered headers, invalid rows, source duplicate detection and exact imported decimal/timestamp preservation.
6. Synthetic fuel and maintenance CSV import/export round trips.
7. JSON exact round trip and rejection of invalid versions, missing fields, bad numeric values and duplicate IDs.

The full normal command `./gradlew testDebugUnitTest assembleDebug lintDebug` **did not pass**: the cloud proxy rejected a Maven Central redirect for `org.robolectric:nativeruntime-dist-compat:1.0.16` to `gcp.mirrors.maven.org` (HTTP 403).

To execute the independent pure JVM tests, a temporary **outside-repository** Gradle init script excluded that unused native test-runtime artifact, and selected only `com.arindam.mileage.RulesTest`. This command passed all seven tests. It did not change production code, assertions, dependency declarations, or the committed workflow.

A subsequent diagnostic run with that same local exclusion attempted all 13 tests and lint using `--continue`. Seven tests passed; **six failed during Robolectric setup before their test bodies**, because the Android framework test artifact (`android-all-instrumented:9-robolectric-4913185-2-i7`) could not be downloaded through the proxy (HTTP 403). An initial read-only home-cache issue was fixed with a writable test-JVM cache location, and the retry confirmed the remaining network blocker. Lint completed with no issues. Therefore these six checks remain **unverified**, rather than passed:

- Fuel duplicate import and preservation of local edits through Room (including stale previews).
- Maintenance duplicate import and preservation of local edits through Room.
- Full database backup/restore including preferences and source IDs.
- Invalid backup leaves existing database records and preferences intact.
- An actual SQLite insertion failure rolls back the entire restore transaction.
- MainActivity startup and manifest checks under Robolectric.

These tests remain enabled in the repository and GitHub Actions. No test exclusions or network workarounds were added to the project. Current full diagnostic reports are under `app/build/reports/tests/testDebugUnitTest/`; lint reports are under `app/build/reports/lint-results-debug.*` (generated/ignored).

## Environment follow-up

The environment draft saves an additive custom network destination, `gcp.mirrors.maven.org`, preserving package-manager presets. Review and save it in environment settings, then retry the full suite. Publish the environment to retain the prepared snapshot/configuration. The draft also saves tested Android activation/build instructions; saving the draft does not itself apply the network policy or publish anything.

In this cloud instance:

```sh
source /workspace/tooling/activate-mileage-android.sh
cd /workspace/mileage-tracker-android
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The snapshot has verified JDK/SDK tooling, proxy CA trust, Gradle dependencies and writable cache configuration outside the repository. TLS and distribution/artifact verification remain enabled. No credentials or personal records were requested, used as fixtures, bundled or added to source.

## Not verified here

No physical Nothing Phone 2a or Android emulator was available. Installation, first-launch airplane-mode UI behavior, Android document-provider interactions, rotation/keyboard behavior, and scrolling performance on the phone require the README acceptance checklist. The GitHub workflow was authored but has not been pushed or remotely executed. APK assembly and source-level logic tests do not establish those device/CI outcomes.
