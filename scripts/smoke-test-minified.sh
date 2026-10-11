#!/usr/bin/env bash
# Installs the minified staging APK unchanged, checks that the configuration screen renders and
# saves a configuration without a crash. Saving runs DataStore, the Glance update and the alarm
# scheduling through R8, which rendering alone never reaches. The instrumented tests run against
# the debug build: instrumenting a minified app needs keep rules for every library the test APK
# shares with it, which would hide exactly the shrinking bugs this check exists to catch.
#
# Expects an emulator in English (US), as in CI: the date is typed as MMDDYYYY and the date
# picker's controls are found by their English labels.
set -euo pipefail

apk=app/build/outputs/apk/staging/app-staging.apk
package=com.pimorazelvanto.dayscounter
activity="$package/.config.ConfigActivity"
dump=/sdcard/smoke-ui.xml

# Read into a variable first: piping straight into grep -q would let pipefail report the
# writer's SIGPIPE as a failure once grep stops reading.
fail_on_crash() {
  local crashes
  crashes=$(adb logcat -d -b crash)
  if grep -q "$package" <<< "$crashes"; then
    echo "Minified app crashed:" >&2
    echo "$crashes" >&2
    exit 1
  fi
}

screen() {
  adb shell uiautomator dump "$dump" > /dev/null 2>&1 && adb shell cat "$dump"
}

# Taps the centre of the first node matching an attribute pattern, such as 'text="Save"'.
# Polls like the render check, because a dump right after a tap can miss the next screen.
tap() {
  local node="" left top right bottom
  for _ in $(seq 1 10); do
    node=$(screen | grep -oE "$1[^>]*bounds=\"[^\"]+\"" | awk 'NR == 1') || true
    [ -n "$node" ] && break
    sleep 1
  done
  if [ -z "$node" ]; then
    echo "Smoke test found no node matching $1" >&2
    screen >&2 || true
    exit 1
  fi
  read -r left top right bottom <<< "$(sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]"/\1 \2 \3 \4/' <<< "$node")"
  adb shell input tap $(((left + right) / 2)) $(((top + bottom) / 2))
  sleep 1
}

adb install -r "$apk" > /dev/null
adb logcat -c

# Any widget id makes the activity show its UI instead of finishing right away.
adb shell am start -W -n "$activity" --ei appWidgetId 4242 > /dev/null

rendered=false
for _ in $(seq 1 20); do
  if screen | grep -qE 'text="(Save|Speichern)"'; then
    rendered=true
    break
  fi
  sleep 1
done

fail_on_crash

if [ "$rendered" != true ]; then
  echo "Configuration screen of the minified app did not render" >&2
  screen >&2 || true
  exit 1
fi

resumed() {
  local top
  top=$(adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity') || true
  grep -q "$activity" <<< "$top"
}

# Otherwise "no longer resumed" below would also hold if the check could not see the activity.
if ! resumed; then
  echo "Configuration screen is not reported as the resumed activity" >&2
  exit 1
fi

tap 'text="Target date"'
tap 'content-desc="Switch to text input mode"'
tap 'text="Date"'
adb shell input text 01012030
tap 'text="OK"'
tap 'text="Save"'

finished=false
for _ in $(seq 1 10); do
  if ! resumed; then
    finished=true
    break
  fi
  sleep 1
done

fail_on_crash

if [ "$finished" != true ]; then
  echo "Saving in the minified app did not close the configuration screen" >&2
  screen >&2 || true
  exit 1
fi

echo "Minified app renders its configuration screen and saves a configuration"
