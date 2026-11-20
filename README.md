# Mobile Solar Tracker

Offline Android app (Jetpack Compose) for the Solar Tracker readings data.

It reads and writes the **same SQLite schema** as the Python web app:

```
meter_records(id, date TEXT UNIQUE, import REAL, export REAL, solar_gen REAL, addedon TEXT)
```

Readings are cumulative meter snapshots; every figure on screen is a delta between
consecutive readings.

## Behaviour

- **Single user, offline.** No login, no network permission, no server.
- **Empty by default.** On first launch an empty database (`solartracker.db`) is created
  with the schema, so you can start logging immediately. *Settings → Open database…* copies
  any `.db` file you pick and makes it live; *Save a copy…* exports the current one;
  *Restore sample data* loads the bundled `meter_logs.db` (13 readings).
- The app never rewrites an existing schema. If a chosen file has no
  `meter_records` table, it is reported and you can explicitly create it.
- Edits apply to the private copy. Use *Save a copy…* to move them back out.

## Screens

| Tab | Contents |
|---|---|
| Entry | Date picker, three cumulative values, insert-or-update |
| Logged | Totals summary, paged reading list with per-period deltas, delete |
| Charts | Energy Flow, Daily Rates, Cumulative — 6/12/24/all periods |
| Settings | Active database, switch/import/export, restore seed |

## Build

The project is set up for the local Android SDK (platform 37, build-tools 36):

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Install on a connected device or emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- minSdk 26 (uses `java.time` directly), targetSdk 37.
- Charts are drawn with Compose `Canvas` — no charting dependency.
- Domain maths is a direct port of `libsolar.py`; `DomainTest` pins the values.
