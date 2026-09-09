# Days Counter

Ein 1x1-Homescreen-Widget für Android 13 und neuer, das die Tage bis zu einem Datum zählt.
Ersatz für das nicht mehr gepflegte Widget „days left“.

## Funktionen

- Beliebig viele Widgets, jedes mit eigenem Titel, Zieldatum und Header-Farbe (12 Farben)
- Zeigt die verbleibenden Tage, „0“ am Zieltag und „-“ danach; angefangene Tage zählen voll
- Aktualisiert sich exakt um Mitternacht sowie bei Zeitzonen- oder Zeitänderung
- Folgt dem hellen oder dunklen Systemthema
- Oberfläche auf Deutsch und Englisch

## Bedienung

1. Widget „Days Counter“ auf den Homescreen ziehen. Der Konfigurationsdialog öffnet sich.
2. Titel eingeben, Zieldatum wählen (frühestens morgen), Farbe antippen und aus dem Raster wählen.
3. Speichern. Ein Tipp auf das Widget öffnet den Dialog erneut.

## Installation aus dem Quellcode

Voraussetzungen: JDK 17 oder neuer, Android SDK mit Platform 37 und Build-Tools 37.0.0,
`sdk.dir` in `local.properties` oder `ANDROID_HOME` gesetzt.

    ./gradlew assembleDebug
    adb install app/build/outputs/apk/debug/app-debug.apk

## Entwicklung

    ./gradlew check            # Lint, ktlint, detekt, Unit- und Robolectric-Tests
    ./gradlew connectedCheck   # Instrumentierte Tests auf einem laufenden Emulator
    ./gradlew ktlintFormat     # Formatierung anwenden

Architektur und Entscheidungen: `docs/superpowers/specs/2026-09-08-days-counter-widget-design.md`.
