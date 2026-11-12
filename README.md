# KUET Bus Live for Android

The Android version of [KUET Bus Live](https://github.com/shadowfax-505/KUETBusLive), the live campus bus tracker for Khulna University of Engineering & Technology. It uses the same Firebase project and the same Firestore data as the iOS app, so a driver on an iPhone shows up on an Android rider's map and the other way round.

Built with Kotlin, Jetpack Compose (Material 3), Firebase Auth, Cloud Firestore, MapLibre with OpenStreetMap tiles, and the OSRM routing API.

## What it does

**Students and teachers**
- Live map with every running bus they may ride. Each bus is drawn as a bus seen from above that glides between GPS fixes and turns with its heading, instead of jumping as a dot.
- Tap a bus for its trip card: ETA to your stop over real roads, distance, progress from KUET, speed, signal, and how full it is.
- Report "Seats available" or "Full", and send "I'm waiting here" so the driver sees you on the road ahead.
- A notification when your bus is 5 minutes away.
- The official timetable with your next bus, routes as stop timelines, news from the transport office, and complaints with replies.
- Students only see student buses ("Normal" trips); teachers see every bus.

**Drivers**
- See the bus assigned to them today (their own, or one they cover).
- Pick the trip and route, then start. The position is shared every 5 seconds by a foreground service, also with the screen off, with an End trip button in the notification.
- A large banner, a tone and a buzz when a rider is waiting ahead.
- "I can't drive" requests for one or more days.

**Transport admin**
- Every bus on the map, timetable editing, approvals for teachers and drivers, deactivating drivers who left.
- Drivers & buses: a regular driver per bus, temporary covers, and turning absence requests into covers.
- Complaints inbox with status and replies, and news posts.

## Shared backend

Both apps follow [`docs/data-model.md`](https://github.com/shadowfax-505/KUETBusLive/blob/main/docs/data-model.md) and the same [`firestore.rules`](https://github.com/shadowfax-505/KUETBusLive/blob/main/firestore.rules) in the iOS repository. Nothing on the server changes for Android. The rules still decide who may do what: for example a driver can only put the bus they drive today on the map.

| Collection | Used for |
|---|---|
| `users` | profiles, roles, approval, favourite stop |
| `schedules`, `routes` | timetable and stops |
| `buses` (+ `waves`) | live trips and riders waiting |
| `fleet`, `driverAbsences`, `trips` | who drives which bus |
| `complaints`, `announcements` | feedback and news |

## Set up

1. Open the `kuetbuslive` project in the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with the package name `io.github.shadowfax505.kuetbuslive`.
3. Download `google-services.json` and put it in `app/`. It is ignored by git.
4. Open the project in Android Studio and run it on a phone or emulator (Android 8.0 or newer).

Without `google-services.json` the app still builds, and opens on a short screen that says the file is missing.

Debug builds accept the `@test.com` demo accounts, like the iOS debug build. Release builds only accept KUET emails.

## Map and routing

- Map tiles: [OpenFreeMap](https://openfreemap.org) vector tiles from OpenStreetMap data, no API key. Light and dark styles follow the phone's theme.
- Road paths and ETAs: the public [OSRM](https://project-osrm.org) server, asked at most every 30 seconds per bus.

## Project layout

```
app/src/main/java/io/github/shadowfax505/kuetbuslive/
  model/     data model, timetable, bus assignment and map maths (unit tested)
  data/      Firestore reads and writes, OSRM client, local settings
  map/       MapLibre map, bus icons and smooth bus movement
  auth/      sign in, sign up, verification and approval
  rider/     live map, trip card, arrival alerts
  driver/    trip screen and the location service
  admin/     approvals, drivers & buses, complaints inbox
  campus/    schedule, routes, news, complaints, profile
  schedule/  timetable screen and editor
```

## Build and test

```
./gradlew testDebugUnitTest assembleDebug
```

GitHub Actions runs the unit tests and builds the debug APK on every push; each run keeps the APK as a download.

## Author

Muttakin Rahman Shoscho ([@shadowfax-505](https://github.com/shadowfax-505))
