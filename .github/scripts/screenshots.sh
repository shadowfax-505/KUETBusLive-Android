#!/usr/bin/env bash
# Runs the screenshot tour on the emulator and copies the pictures out.
set -x
APP=io.github.shadowfax505.kuetbuslive
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# Somewhere between KUET and Fulbarigate, so walking times make sense.
adb emu geo fix 89.5060 22.8945
./gradlew --no-daemon installDebug installDebugAndroidTest
adb shell am instrument -w -e class $APP.ShowcaseTest $APP.test/$APP.ShowcaseRunner | tee instrument.log
mkdir -p screenshots
for f in $(adb shell run-as $APP ls files/shots | tr -d '\r'); do
  adb exec-out run-as $APP cat "files/shots/$f" > "screenshots/$f"
done
adb logcat -d -s Showcase:E AndroidRuntime:E > screenshots/log.txt || true
ls -la screenshots
