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
- **History**: Last Month Summary and Fuel Fillups / Maintenance tabs, with add, edit and delete controls. Search station/service, notes, date, odometer or cost; filter by an inclusive date range and fuel station; sort newest, oldest or highest cost. Filters change the displayed records, while mileage still uses the complete selected vehicle history.
- **Analytics**: current-month totals, savings, swipeable monthly comparison cards, current-year and all-time overviews, and station efficiency.
- **Settings**: metric units, currency dropdown and **Save Settings**, dark theme and reduced-motion switches, vehicle management, and Import & Backup. The Dashboard theme icon also switches appearance. Appearance and motion preferences save immediately and are included in full JSON backups. Background-orb controls, cloud login and logout are omitted for this offline app.

## Vehicles, tank status and motion (1.2)

Use the vehicle selector above each screen or **Garage** to add and rename vehicles, optionally recording model and registration. A new vehicle becomes active. Fuel, maintenance, summaries, trends and autofill use only the selected vehicle. A vehicle with records cannot be deleted; at least one vehicle must remain. Existing installations migrate all records into **My Vehicle**, without changing their values or source IDs.

On a new fuel form, **Fill from last entry** copies only that vehicle's latest station and price. Enter the date, odometer and litres yourself; notes, tank flags and identity are never copied. Edit forms preserve their original vehicle.

Choose **Unspecified**, **Full tank** or **Partial fill-up**, and flag a missed fill-up when needed. Unspecified is the default for existing/imported data and keeps the original per-fill rule. Explicit tank entries add a separate tank-cycle efficiency card and **Baseline / Estimate / Confirmed** badges. The algorithm is documented below. These are independently implemented features inspired by the reviewed app; no proprietary code or assets were incorporated, and its unrecovered calculations are not assumed.

Tabs slide/fade, dialogs enter/exit, graphs reveal, changed metrics crossfade and history changes animate. Animations finish in about 90–240 ms, are interruptible, and respect **Reduced motion** and Android's disabled-animator setting. There are no continuous background animations. Room/file work runs on the I/O dispatcher; analytics, chart preparation and cached search/sort indexes run on the calculation dispatcher. Search is debounced by 120 ms. Lists compose visible rows using stable keys; chart drawing uses at most 120 prepared points while totals use every record. Theme and currency changes reuse the prepared history. Physical-device frame pacing still needs the acceptance check below.

This update covers the core upgrade. Reminders, PDF reports, trips and encrypted/QR backups are deferred.

## Log fuel and maintenance

Use **Home → Add Entry**, or the add buttons in **History**. Tap **Edit** to change a record and **Delete** to remove it after confirmation. The calendar icon opens Android's date picker. Dates use `YYYY-MM-DD`. Odometers are km; fuel is litres. Service type is free text. Fuel total is litres × price, rounded to two decimals on manual entry, matching the web form. Editing notes or station on an imported record preserves its stored cost; changing litres or price recalculates the cost. Imported costs, litres and prices are stored as decimal strings without rounding or recalculation. Entry/service dates and original creation timestamps stay separate.

Currency selection in Settings changes the display label only, without exchange-rate conversion. Units remain metric. Local preferences are stored in a Room table so full restore can atomically replace both data and preferences.

## Import & Backup

Use **Settings → Import & Backup**. Files are opened/saved using Android's system document picker; there is no broad storage permission. Download files into local phone storage before airplane mode. No files or credentials are uploaded. Opened files must be UTF-8 and at most 20 MiB.

- **Import Fuel CSV** accepts original `fuel_entries_rows.csv` headers: `id,user_id,entry_date,odometer_km,total_cost,price_per_litre,litres,station_name,notes,created_at`.
- **Import Maintenance CSV** accepts original `maintenance_logs_rows.csv` headers: `id,user_id,service_date,service_type,odometer_km,total_cost,notes,created_at`.
- Choose each original file directly. Filenames and column order do not matter; header names do. Extra columns are ignored. Blank notes/stations, UTF-8 BOM, CRLF, quoted commas, doubled quotes and multiline fields are supported. `user_id` is ignored, never used for authentication. No user-settings importer is needed.
- Select the destination vehicle before opening a CSV. Its name is shown in the confirmation preview and remains fixed for that import. CSV files have no vehicle or tank-status fields: imported rows are **Unspecified**, with no inferred full/partial/missed flags.
- The preview shows ready, already imported (including repeated IDs inside the file), and invalid counts, with invalid physical starting line numbers and reasons. Only valid, new records are inserted after **Import records**. Cancel changes nothing. Malformed CSV syntax is rejected before import. Dates/timestamps and nonnegative finite decimals are validated; zero quantities are retained and give no mileage if used as the denominator.
- Each record type has unique original source IDs across all vehicles. Reimport never moves or overwrites an existing record, including local edits; commit-time checks and database uniqueness also protect against stale previews. Deleting an imported record removes its source ID, so importing that file again can recreate it.
- **Save full JSON backup** saves all vehicles and both histories, local IDs, original source IDs, exact decimal strings, creation timestamps, tank/missed flags, selected vehicle and preferences in format `mileage-tracker`, version 3. Original version 1 and 2 backups remain supported, restoring into **My Vehicle** with unspecified tank status (v1 defaults to dark theme). Treat this file as personal data.
- **Restore full JSON backup** validates structure, version, every record, vehicle references, preferences and unique IDs before displaying a replacement preview. Confirm **Replace and restore** to replace the entire garage and settings in one SQLite transaction. Invalid files or transaction failures leave existing data intact. Empty histories in a valid backup clear those histories. Cancel changes nothing.
- **Export fuel history CSV** and **Export maintenance history CSV** export the selected vehicle only, with the original headers (blank `user_id`). They omit vehicle details, tank/missed flags and preferences: use JSON for full restoration. Native records export their local ID as the CSV source ID; reimporting a native export also preserves existing records. CSV text is preserved verbatim, including spreadsheet formula-like text; view untrusted exports as text rather than enabling spreadsheet formulas.

## Calculation reference

Read-only reference: [`thisisarindam/mileage-tracker`](https://github.com/thisisarindam/mileage-tracker/tree/5b09ad636b21cd853076bc0ba78308ce27aebe9f), inspected at commit `5b09ad636b21cd853076bc0ba78308ce27aebe9f`. No Supabase project was accessed. Source fields and formulas were inspected before implementation in `js/dashboard.js` (`updateQuickStats`, `renderRecentLogs`, form submission), `js/history.js` (fuel editing, history mileage, maintenance submission), `js/analytics.js` (`calculateStats`), `dashboard.html` and `history.html`.

The web app has different mileage denominators in different screens, so the app labels them separately instead of silently picking one:

- **Per-fill mileage**: sort by odometer; `(current odometer − previous odometer) / current litres`, only when distance and litres are positive. The first record has no mileage. Equal odometers/zero litres show no mileage.
- **Lifetime mileage** (web dashboard): `(max odometer − min odometer) / sum(litres excluding the lowest-odometer fill-up)`, only for positive distance/denominator.
- **Analytics mileage (all litres)** (web analytics): same distance divided by all recorded litres. Displayed separately.
- **Total spending**: sum stored fuel costs. **This month**: sum costs with an entry date in the current local calendar month. **Distance**: maximum minus minimum odometer (zero with fewer than two records).

Calculations use exact decimal arithmetic before division; mileage display rounds to one decimal. Manual fuel cost reproduces the web form’s binary floating-point multiplication and `toFixed(2)` rounding (including `1.005 × 1 → 1.00`). No imperial conversion is offered because this metric app must keep the web data's km/litre meaning intact.

### Explicit tank-cycle rules

These additional rules are implemented independently. Records are processed per vehicle by date, numeric odometer, creation timestamp and ID. The first full tank starts a baseline; its litres are excluded. Partial fill-ups accumulate fuel and show an estimate: distance since baseline ÷ all litres added since baseline. The next full tank confirms that same calculation, including its closing-fill litres, and becomes the next baseline. The summary is total confirmed distance ÷ total confirmed litres, rather than an average of the displayed cycle values.

A missed entry, unspecified entry, or non-increasing odometer resets the cycle. A full tank at that reset starts a new baseline; a partial fill waits for a baseline. Full/partial entries require positive litres. Original dashboard and analytics averages remain separately labelled and keep the reference rules. Example: full at 1000 km; partial at 1050 km adding 4 L → **12.5 estimated**; full at 1200 km adding 6 L → **20.0 confirmed** (200 ÷ 10). Filtering cannot change these readings.

## Airplane-mode acceptance test on Nothing Phone 2a

1. Download the APK, the two original CSV files and (optionally) a prior JSON backup to **Downloads**. Install the APK, allowing installation from your chosen file manager when Android asks. Then enable airplane mode, turn Wi-Fi off, and launch the app for the first time. Confirm the dark Dashboard, Home / History / Analytics / Settings tabs, empty histories, km/litre/INR defaults, no login or network prompts. Switch the theme, restart, and verify it persists.
2. Use **Add Entry → Fuel Fill-up** to add fuel at 1000 km, 10 L, INR 100/L; then at 1200 km, 5 L, INR 100/L. Confirm second-fill mileage and lifetime mileage are **40.0 km/L**; analytics mileage is **13.3 km/L**. Edit notes, force-stop and reopen; confirm persistence. Add, edit and delete a service record.
3. Import each CSV using its separate action. Check preview counts and invalid-row reasons, cancel once to confirm nothing saves, reopen and confirm import. Inspect imported notes (including commas/newlines), dates and values. Edit an imported record locally; import the same file again and verify zero new rows and preserved edits.
4. Save a **full JSON backup** into Downloads. Export each history as CSV and reopen them with a local text/spreadsheet viewer. Verify headers and quoted notes. No rename or merge is needed.
5. Add a temporary record and change currency. Open the JSON backup for restore; check counts and cancellation first. Restore again and confirm replacement. Verify the temporary record disappears, saved preferences return, exact imported values remain, and duplicate CSV imports still do not overwrite records.
6. Make a copy of the backup with an unsupported version or invalid date using a local editor. Attempt restore and confirm an error with unchanged histories/settings. Repeat after force-stop. Keep a valid external backup before uninstalling, clearing storage, or changing devices.
7. In Garage, rename **My Vehicle**, add a second vehicle, and add a fuel/service record to it. Switch back and forth; check isolated histories, summaries and autofill. Confirm vehicles with records cannot be removed. Import CSV into a selected vehicle and reimport into another: existing source IDs must stay in their original vehicle. Save/restore JSON and verify both vehicles and the selected vehicle return; CSV exports should contain only the chosen history.
8. Test the full/partial example above in a fresh vehicle. Confirm baseline/estimate/confirmed badges, then flag a missed entry and verify the cycle resets. Search/filter/sort without changing the calculated result. Clear the filters to show all records again.
9. Switch tabs quickly during transitions; swipe graphs; scroll a large synthetic history; open/cancel forms, filter sheets and date pickers with the keyboard shown. Check text/input visibility at larger font sizes. Toggle light/dark, restart, and test Reduced motion plus Android's disabled animations. Check responsiveness and dropped frames on the phone; JVM tests cannot measure real-device smoothness.

Synthetic fixtures with the exact CSV headers are under `app/src/test/resources/`; these are invented records. Tests cover both original mileage rules, zero/duplicate odometers, calendar spending, quoted/multiline CSV, reordered headers, bad rows, repeated/stale imports, preservation of local edits, full JSON round trip, invalid-backup preservation, and an injected SQLite failure to verify rollback.

Physical Nothing Phone 2a behavior, first-launch airplane-mode interaction, file-provider behavior and performance on that phone require the device acceptance test above. Build/test results for this implementation are recorded in `VALIDATION.md`.

## Updating from versions 1.0 or 1.1

Version 1.2 includes tested Room migrations from both released schemas into schema 3; normal signed app updates retain both histories, exact imported values, source IDs and appearance. Existing records belong to **My Vehicle** with unspecified tank status. New JSON backups use version 3; v1/v2 restores remain supported.

Save a full JSON backup to Downloads **before replacing an installed debug APK**. GitHub's temporary runners generate debug signing keys; Android may reject a later APK as having a different signature. If that happens, keep the exported backup outside app storage, uninstall the old debug app, install the new APK, then restore the backup. Uninstalling without an external backup deletes local records.
