#!/usr/bin/env bash
# Copies each language's changelogs/default.txt to changelogs/<versionCode>.txt, the name F-Droid
# reads, using the versionCode semantic-release just wrote to version.properties.
set -euo pipefail

code=$(sed -n 's/^versionCode=//p' version.properties)
[ -n "$code" ] || { echo "version.properties has no versionCode" >&2; exit 1; }

for dir in fastlane/metadata/android/*/; do
  source="$dir/changelogs/default.txt"
  [ -f "$source" ] || { echo "$source is missing" >&2; exit 1; }
  cp "$source" "$dir/changelogs/$code.txt"
done
