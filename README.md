# Mileage Tracker

Native Kotlin / Jetpack Compose Material 3 Android app, with Room SQLite storage. Android 8.0 (API 26) or newer. Defaults to kilometres, litres and INR. No Internet permission, login, remote assets, Supabase client, or automatic cloud/device-transfer backup. Everything works locally, including the first launch. Uninstalling or clearing app storage deletes your data: keep manual JSON backups outside app storage.

## Get an installable APK

Open **GitHub → Actions → Android debug APK**, select a successful run, and download **mileage-tracker-debug** from Artifacts (GitHub sign-in may be required). Unzip it and install `app-debug.apk`. The workflow runs on pushes and through **Run workflow**, runs unit tests and `assembleDebug`, then uploads that APK. A push triggers the workflow; the APK artifact appears only after a successful run.

For local development, use Android Studio or JDK 17+, Android SDK Platform 35 and Build Tools 35.0.0. Set `ANDROID_HOME` to your SDK (or configure ignored `local.properties`), then run:

```sh
./gradlew testDebugUnitTest assembleDebug
./gradlew lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Unit test reports: `app/build/reports/tests/testDebugUnitTest/index.html`. Gradle 8.9 wrapper verifies its distribution SHA-256. Development dependency downloads require network; the installed app does not.

## Reference UI and navigation

The app uses the reference web app's Outfit typography, charcoal/white themes, rounded cards, blue pill buttons, green mileage badges and four-tab navigation, implemented entirely in native Compose. The font is bundled locally under the [SIL Open Font License](licenses/OFL-Outfit.txt); no fonts or charts download at runtime. [UI previews and reference mapping](docs/UI.md).

- **Home**: Dashboard, monthly fuel spending, average monthly distance, lifetime efficiency, swipeable price/running-cost/efficiency trends, and the last ten fuel logs. Tap **Add Entry** to choose Fuel Fill-up or Maintenance. **View All** opens History.
- **History**: Last Month Summary and Fuel Fillups / Maintenance tabs, with add, edit and delete controls.
- **Analytics**: current-month totals, savings, swipeable monthly comparison cards, current-year and all-time overviews, and station efficiency.
- **Settings**: metric units, currency dropdown and **Save Settings**, dark theme switch, and Import & Backup. The Dashboard theme icon also switches appearance. Theme changes save immediately and are included in full JSON backups. Background-orb controls, cloud login and logout are omitted for this offline app.

## Log fuel and maintenance

Use **Home → Add Entry**, or the add buttons in **History**. Tap **Edit** to change a record and **Delete** to remove it after confirmation. The calendar icon opens Android's date picker. Dates use `YYYY-MM-DD`. Odometers are km; fuel is litres. Service type is free text. Fuel total is litres × price, rounded to two decimals on manual entry, matching the web form. Editing notes or station on an imported record preserves its stored cost; changing litres or price recalculates the cost. Imported costs, litres and prices are stored as decimal strings without rounding or recalculation. Entry/service dates and original creation timestamps stay separate.

Currency selection in Settings changes the display label only, without exchange-rate conversion. Units remain metric. Local preferences are stored in a Room table so full restore can atomically replace both data and preferences.

## Import & Backup

Use **Settings → Import & Backup**. Files are opened/saved using Android's system document picker; there is no broad storage permission. Download files into local phone storage before airplane mode. No files or credentials are uploaded. Opened files must be UTF-8 and at most 20 MiB.

- **Import Fuel CSV** accepts original `fuel_entries_rows.csv` headers: `id,user_id,entry_date,odometer_km,total_cost,price_per_litre,litres,station_name,notes,created_at`.
- **Import Maintenance CSV** accepts original `maintenance_logs_rows.csv` headers: `id,user_id,service_date,service_type,odometer_km,total_cost,notes,created_at`.
- Choose each original file directly. Filenames and column order do not matter; header names do. Extra columns are ignored. Blank notes/stations, UTF-8 BOM, CRLF, quoted commas, doubled quotes and multiline fields are supported. `user_id` is ignored, never used for authentication. No user-settings importer is needed.
- The preview shows ready, already imported (including repeated IDs inside the file), and invalid counts, with invalid physical starting line numbers and reasons. Only valid, new records are inserted after **Import records**. Cancel changes nothing. Malformed CSV syntax is rejected before import. Dates/timestamps and nonnegative finite decimals are validated; zero quantities are retained and give no mileage if used as the denominator.
- Each history has a unique original source ID. Reimport never overwrites an existing record, including local edits; database uniqueness also protects against stale previews. Deleting an imported record removes its source ID, so importing that file again can recreate it.
- **Save full JSON backup** saves both histories, local IDs, original source IDs, exact decimal strings, creation timestamps and preferences in format `mileage-tracker`, version 2, including theme preference. Original version 1 backups remain supported for restore (defaulting to dark theme). Treat this file as personal data.
- **Restore full JSON backup** validates structure, version, every record, preferences and unique IDs before displaying a replacement preview. Confirm **Replace and restore** to replace both histories and settings in one SQLite transaction. Invalid files or transaction failures leave existing data intact. Empty histories in a valid backup clear those histories. Cancel changes nothing.
- **Export fuel history CSV** and **Export maintenance history CSV** are separate spreadsheet exports with the original headers (blank `user_id`). They are not full backups: use JSON for restoration. Native records export their local ID as the CSV source ID. CSV text is preserved verbatim, including spreadsheet formula-like text; view untrusted exports as text rather than enabling spreadsheet formulas.

## Calculation reference

Read-only reference: [`thisisarindam/mileage-tracker`](https://github.com/thisisarindam/mileage-tracker/tree/5b09ad636b21cd853076bc0ba78308ce27aebe9f), inspected at commit `5b09ad636b21cd853076bc0ba78308ce27aebe9f`. No Supabase project was accessed. Source fields and formulas were inspected before implementation in `js/dashboard.js` (`updateQuickStats`, `renderRecentLogs`, form submission), `js/history.js` (fuel editing, history mileage, maintenance submission), `js/analytics.js` (`calculateStats`), `dashboard.html` and `history.html`.

The web app has different mileage denominators in different screens, so the app labels them separately instead of silently picking one:

- **Per-fill mileage**: sort by odometer; `(current odometer − previous odometer) / current litres`, only when distance and litres are positive. The first record has no mileage. Equal odometers/zero litres show no mileage.
- **Lifetime mileage** (web dashboard): `(max odometer − min odometer) / sum(litres excluding the lowest-odometer fill-up)`, only for positive distance/denominator.
- **Analytics mileage (all litres)** (web analytics): same distance divided by all recorded litres. Displayed separately.
- **Total spending**: sum stored fuel costs. **This month**: sum costs with an entry date in the current local calendar month. **Distance**: maximum minus minimum odometer (zero with fewer than two records).

Calculations use exact decimal arithmetic before division; mileage display rounds to one decimal. Manual fuel cost reproduces the web form’s binary floating-point multiplication and `toFixed(2)` rounding (including `1.005 × 1 → 1.00`). No imperial conversion is offered because this metric app must keep the web data's km/litre meaning intact.

## Airplane-mode acceptance test on Nothing Phone 2a

1. Download the APK, the two original CSV files and (optionally) a prior JSON backup to **Downloads**. Install the APK, allowing installation from your chosen file manager when Android asks. Then enable airplane mode, turn Wi-Fi off, and launch the app for the first time. Confirm the dark Dashboard, Home / History / Analytics / Settings tabs, empty histories, km/litre/INR defaults, no login or network prompts. Switch the theme, restart, and verify it persists.
2. Use **Add Entry → Fuel Fill-up** to add fuel at 1000 km, 10 L, INR 100/L; then at 1200 km, 5 L, INR 100/L. Confirm second-fill mileage and lifetime mileage are **40.0 km/L**; analytics mileage is **13.3 km/L**. Edit notes, force-stop and reopen; confirm persistence. Add, edit and delete a service record.
3. Import each CSV using its separate action. Check preview counts and invalid-row reasons, cancel once to confirm nothing saves, reopen and confirm import. Inspect imported notes (including commas/newlines), dates and values. Edit an imported record locally; import the same file again and verify zero new rows and preserved edits.
4. Save a **full JSON backup** into Downloads. Export each history as CSV and reopen them with a local text/spreadsheet viewer. Verify headers and quoted notes. No rename or merge is needed.
5. Add a temporary record and change currency. Open the JSON backup for restore; check counts and cancellation first. Restore again and confirm replacement. Verify the temporary record disappears, saved preferences return, exact imported values remain, and duplicate CSV imports still do not overwrite records.
6. Make a copy of the backup with an unsupported version or invalid date using a local editor. Attempt restore and confirm an error with unchanged histories/settings. Repeat after force-stop. Keep a valid external backup before uninstalling, clearing storage, or changing devices.

Synthetic fixtures with the exact CSV headers are under `app/src/test/resources/`; these are invented records. Tests cover both original mileage rules, zero/duplicate odometers, calendar spending, quoted/multiline CSV, reordered headers, bad rows, repeated/stale imports, preservation of local edits, full JSON round trip, invalid-backup preservation, and an injected SQLite failure to verify rollback.

Physical Nothing Phone 2a behavior, first-launch airplane-mode interaction, file-provider behavior and performance on that phone require the device acceptance test above. Build/test results for this implementation are recorded in `VALIDATION.md`.

## Updating from version 1.0

Version 1.1 includes a non-destructive Room database migration; normal app updates retain both histories, exact imported values and source IDs. Old JSON backups also restore successfully. New JSON backups use version 2 to include appearance.

Save a full JSON backup to Downloads **before replacing an installed debug APK**. GitHub's temporary runners generate debug signing keys; Android may reject a later APK as having a different signature. If that happens, keep the exported backup outside app storage, uninstall the old debug app, install the new APK, then restore the backup. Uninstalling without an external backup deletes local records.
