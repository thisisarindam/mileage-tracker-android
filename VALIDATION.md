# Validation — smooth core upgrade 1.2 (14 November 2026)

`./gradlew testDebugUnitTest assembleDebug lintDebug` **passed**. All **36 tests executed, zero failed, zero errors, zero skipped**. No suites or assertions were excluded.

| Suite | Result |
| --- | --- |
| CoreRulesTest | 5 passed |
| GarageStoreTest | 5 passed |
| InsightsTest | 2 passed |
| OfflineStartupTest | 1 passed |
| ReferenceUiTest | 3 passed |
| RulesTest | 7 passed |
| SmoothCoreUiTest | 5 passed |
| StoreTest | 5 passed |
| UpgradeTest | 3 passed |

## Covered behavior

- Original web mileage/spending rules, CSV syntax/headers/invalid rows, exact decimal values, duplicate imports and preservation of local edits remain covered.
- Independent tank algorithm: full baseline exclusion, partial estimates, full closing fill, weighted confirmed average, missed/unspecified/non-increasing-odometer resets and vehicle isolation.
- Numeric cost sorting, inclusive date/station filters, service search and unchanged mileage when filtering.
- Full JSON v3 round trip across multiple vehicles, tank/missed flags, source IDs and preferences; old v1/v2 JSON compatibility; orphan references rejected; injected restore failure rolls back the entire garage and settings.
- Global source IDs cannot duplicate or move imported records into another vehicle; stale previews and reimported native CSV exports preserve local records.
- Garage create/rename/select/delete; vehicles with records and the final vehicle are protected. SQL foreign keys prevent orphan histories.
- Real schema-1 and schema-2 SQLite migrations preserve both histories, exact values, source IDs, appearance and currency; old records receive the default vehicle and unspecified tank status.
- Actual Compose interactions: rapid tab changes during active transitions, history search/sort, vehicle switching, safe autofill and Room save, preference persistence across activity recreation, and garage forms/removal. Theme changes reuse the same prepared index and insights.
- A 1,003-record Compose history can jump-scroll, search for a specific row and retain its filter across navigation. A separate 20,000-record synthetic test exercises indexing, numeric sort/filter, complete analytics/tank preparation and bounded chart rendering data.

## Build and static checks

- JDK 17, Gradle 8.9 wrapper, SDK 35 / Build Tools 35.0.0. Minimum API 26, target API 35.
- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`, versionCode 3, versionName 1.2. Local size/hash are recorded below; GitHub builds use their own debug signing key and may have a different hash.
- Local APK: **16,793,441 bytes (16.02 MiB)**. SHA-256: `dc2b19282631d1256f9d3cc6fc1e1087cbc5e4a3523543ac2f60bf13079501fb`.
- `apksigner verify --verbose`: passed, APK Signature Scheme v2. Packaged permissions contain only the AndroidX internal signature receiver permission; no Internet, account or broad storage permissions. Packaged `allowBackup` and `fullBackupContent` are false.
- Automatic cloud backup and device-transfer extraction remain disabled. There are no cloud services or remote assets in the installed app.
- Lint: zero errors; existing pinned dependencies produce eight `GradleDependency` update notices. No UI/API lint checks were suppressed.
- `git diff --check`: passed. The web reference checkout remains unchanged. No Supabase project, real records or credentials were accessed. No code/assets from the inspected third-party APK were incorporated.

## Rendering and visual checks

Database and file operations use the I/O dispatcher. Histories, analytics, chart summaries and indexed search/filter work use the calculation dispatcher. Sort orders and normalized search text are cached; input is debounced by 120 ms. Lazy lists use stable keys. Canvas drawing uses at most 120 prepared samples, while summary statistics use every record. Appearance changes do not rebuild analytics. Motion is bounded and interruptible, with no continuous decorative animation; the local reduced-motion preference and Android animator setting are respected.

Native Compose previews use Robolectric native graphics at 393 × 852 dp with synthetic records. Updated previews are in [docs/UI.md](docs/UI.md), and tests generate additional files under `app/build/reports/ui/`. Visual review improved mileage-badge text contrast and constrained screen content to its viewport. The garage interaction test exposed a layout stall in a nested alert form; using the full-screen native vehicle editor resolved it, and create/rename/remove now pass.

These are functional stress checks, not physical-device benchmarks. JVM timing is diagnostic only; no FPS or frame-time guarantee is inferred.

## Review and physical-device limits

The push/manual GitHub workflow runs tests and assembles the APK, uploading `mileage-tracker-debug`, `unit-test-results` and synthetic `ui-previews`. Consult the review branch's successful workflow for the downloadable APK. No merge or environment publication is performed by these changes.

A physical Nothing Phone 2a was not connected. First-launch airplane-mode behavior, native file-provider behavior, keyboard/insets, larger fonts, touch handling and real frame pacing still require the README acceptance checklist.

Normal signed updates migrate records without deleting them. Export a full JSON backup to Downloads before replacing an installed debug APK. GitHub's temporary debug signing keys can differ; if Android rejects the signature, keep the backup outside app storage, uninstall, install and restore. Uninstalling without an external backup deletes local records.
