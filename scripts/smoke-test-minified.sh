#!/usr/bin/env bash
# Installs the minified staging APK unchanged and checks that the configuration screen renders
# without a crash. The instrumented tests run against the debug build: instrumenting a minified
# app needs keep rules for every library the test APK shares with it, which would hide exactly
# the shrinking bugs this check exists to catch.
set -euo pipefail

apk=app/build/outputs/apk/staging/app-staging.apk
package=com.pimorazelvanto.dayscounter
dump=/sdcard/smoke-ui.xml

adb install -r "$apk" > /dev/null
adb logcat -c

# Any widget id makes the activity show its UI instead of finishing right away.
adb shell am start -W -n "$package/.config.ConfigActivity" --ei appWidgetId 4242 > /dev/null

rendered=false
for _ in $(seq 1 20); do
  if adb shell uiautomator dump "$dump" > /dev/null 2>&1 &&
    adb shell cat "$dump" | grep -qE 'text="(Save|Speichern)"'; then
    rendered=true
    break
  fi
  sleep 1
done

if adb logcat -d -b crash | grep -q "$package"; then
  echo "Minified app crashed:" >&2
  adb logcat -d -b crash >&2
  exit 1
fi

if [ "$rendered" != true ]; then
  echo "Configuration screen of the minified app did not render" >&2
  adb shell cat "$dump" >&2 || true
  exit 1
fi

echo "Minified app starts and renders its configuration screen"
