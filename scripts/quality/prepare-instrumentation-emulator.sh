#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SERIAL:?Select the dedicated emulator with ANDROID_SERIAL}"
[[ "$ANDROID_SERIAL" =~ ^emulator-[0-9]+$ ]]
timeout 300s bash -euc '
  adb wait-for-device
  until [[ $(adb shell getprop sys.boot_completed | tr -d "\r") == 1 &&
           $(adb shell settings get global device_provisioned | tr -d "\r") == 1 &&
           $(adb shell settings get secure user_setup_complete | tr -d "\r") == 1 ]]; do
    sleep 2
  done
'

[[ $(adb shell getprop ro.build.version.sdk | tr -d "\r") == 37 ]]
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU
adb shell wm dismiss-keyguard
adb shell wm size 732x1640
adb shell wm density 320
adb shell settings put system font_scale 1.0
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put system screen_off_timeout 2147483647
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell cmd uimode night no
adb shell input keyevent KEYCODE_HOME

timeout 90s bash -euc '
  until [[ $(adb shell am get-config) == *en-rUS*sw366dp-w366dp-h820dp*notnight*xhdpi* &&
           $(adb shell settings get secure navigation_mode | tr -d "\r") == 2 &&
           $(adb shell dumpsys window | grep "mCurrentFocus=") == *LauncherActivity* ]]; do
    sleep 2
  done
  sleep 15
  [[ $(adb shell dumpsys window | grep "mCurrentFocus=") == *LauncherActivity* ]]
'

adb shell am get-config
adb shell getprop ro.build.fingerprint
adb shell wm size
adb shell wm density
