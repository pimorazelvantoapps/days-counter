# Days Counter

[![CI](https://github.com/pimorazelvantoapps/days-counter/actions/workflows/ci.yml/badge.svg)](https://github.com/pimorazelvantoapps/days-counter/actions/workflows/ci.yml)

Ein 1x1-Homescreen-Widget für Android 13 und neuer, das die Tage bis zu einem Datum zählt.
Ersatz für das nicht mehr gepflegte Widget „days left“.

> **Studienprojekt:** Dieses Repository dient dazu, KI-gestützte Softwareentwicklung und die
> Entwicklung für Android einzuüben. Code, Tests, Dokumentation und Build-Konfiguration sind
> weitgehend in Zusammenarbeit mit einem KI-Coding-Assistenten entstanden. Die App ist
> funktionsfähig und getestet, wird aber als Lern- und Testprojekt gepflegt, ohne Zusagen zu
> Support oder Weiterentwicklung.

## Funktionen

- Beliebig viele Widgets, jedes mit eigenem Titel, Zieldatum und Header-Farbe (12 Farben)
- Zieldatum beliebig in Vergangenheit oder Zukunft; zeigt die verbleibenden Tage, „0“ am
  Zieltag oder die seither vergangenen Tage – die Richtung steht im Titel, nicht auf dem
  Blatt. Angefangene Tage zählen voll
- Aktualisiert sich exakt um Mitternacht sowie bei Zeitzonen- oder Zeitänderung
- Folgt dem hellen oder dunklen Systemthema
- Oberfläche auf Deutsch und Englisch

## Bedienung

1. Widget „Days Counter“ auf den Homescreen ziehen. Der Konfigurationsdialog öffnet sich.
2. Titel eingeben, Zieldatum wählen (beliebig, auch in der Vergangenheit), Farbe antippen und
   aus dem Raster wählen.
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

Jeder Pull Request durchläuft dieselben Prüfungen in GitHub Actions, die instrumentierten Tests
auf einem Emulator. Gemergt wird nur per Rebase, Merge-Commits sind nicht erlaubt.

## Releases

Versionen entstehen automatisch: Jeder Push auf `main` mit einem release-relevanten Commit
erzeugt per [semantic-release](https://semantic-release.gitbook.io/) einen Tag `vX.Y.Z` und
ein GitHub-Release mit Änderungsübersicht. Dafür folgt jede Commit-Nachricht
[Conventional Commits](https://www.conventionalcommits.org/de/v1.0.0/):

| Commit | Wirkung |
|---|---|
| `feat: …` | neue Minor-Version (1.2.0 → 1.3.0) |
| `fix: …`, `perf: …` | neue Patch-Version (1.2.0 → 1.2.1) |
| `feat!: …` oder Footer `BREAKING CHANGE:` | neue Major-Version (1.2.0 → 2.0.0) |
| `docs`, `test`, `build`, `ci`, `refactor`, `chore` | kein Release |

Lokale Builds tragen die Version `0.0.0-dev`.

## Lizenz

MIT, siehe [`LICENSE`](LICENSE).
