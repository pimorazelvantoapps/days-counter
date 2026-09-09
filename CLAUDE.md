# CLAUDE.md

Anweisungen für Coding-Agents in diesem Projekt. Für menschliche Leser gilt `README.md`.

## Projekt

Android-Homescreen-Widget (1x1), das die Tage bis zu einem Zieldatum zählt.
Design und Anforderungen: `docs/superpowers/specs/2026-09-08-days-counter-widget-design.md`.
Kotlin, minSdk 33, compileSdk/targetSdk 37, Jetpack Glance (Widget), Jetpack Compose (Konfiguration).

## Befehle

```
./gradlew check            # Lint, ktlint, detekt, Unit- und Robolectric-Tests
./gradlew connectedCheck   # Instrumentierte Tests auf laufendem Emulator
./gradlew assembleDebug    # APK für Sideload
./gradlew ktlintFormat     # Formatierung anwenden
```

`check` muss vor jedem Commit fehlerfrei durchlaufen. Warnungen sind Fehler; Schwellwerte
in detekt oder Lint nicht aufweichen, Baselines nicht befüllen.

## Code

- Kotlin Official Code Style, durchgesetzt per ktlint.
- Abhängigkeitsrichtung der Packages einhalten: `domain` kennt nichts; `data` kennt
  `domain`; `widget` und `config` kennen `domain` und `data`, aber nicht einander. Einzige
  Ausnahme: `widget` ruft `ConfigActivity.createIntent` auf, um den Tipp auf ein Widget an
  dessen Konfiguration zu binden.
- `WidgetUpdater` und `MidnightUpdateScheduler` sind Schnittstellen in `domain` (wie `Clock`),
  ihre Implementierungen `GlanceWidgetUpdater` und `AlarmManagerMidnightUpdateScheduler` liegen
  in `widget`, weil sowohl `config` (zum Abschluss des Speicherns) als auch `widget` sie
  brauchen und `widget` bereits von `config` abhängt: eine Implementierung in `config` oder
  `widget` selbst würde einen Package-Zyklus erzeugen.
- Zeit nie direkt über `LocalDate.now()` lesen, immer über die `Clock`-Schnittstelle.
- Der Widget-Inhalt wird innerhalb der Komposition beobachtet (`WidgetConfigRepository.observe`,
  `Clock.days()`), nie vor `provideContent` einmalig gelesen. Grund und Konsequenzen stehen im
  Docblock von `DaysCounterWidget.provideGlance`.
- Kein DI-Framework, Abhängigkeiten werden in `AppContainer` manuell konstruiert.
- Test-Fakes und `ControlledClock` liegen in `app/src/sharedTest/java` und werden von Unit- und
  Instrumentation-Tests gemeinsam genutzt. Keine zweite Kopie anlegen.
- Robolectric unterstützt `targetSdk 37` nicht (`maxSdkVersion=36`). Robolectric-Testklassen
  tragen deshalb `@Config(sdk = [35])`; `compileSdk`/`targetSdk` des Projekts bleiben unverändert.

## Dokumentation

- Sprachkonstrukte (Klassen, Methoden, Variablen, Konstanten) tragen absichtsoffenbarende
  Namen (intention-revealing names). Ein guter Name ersetzt den Kommentar.
- Nichts doppelt dokumentieren. Für jede Information genau einen Ort wählen:
  - Name des Sprachkonstrukts: Was etwas ist oder tut
  - Docblock: Nur was der Name nicht ausdrücken kann, etwa Vor-/Nachbedingungen,
    nicht offensichtliche Randfälle, Gründe für eine Entscheidung
  - `README.md`: Für menschliche Leser, etwa Zweck, Installation, Bedienung
  - `CLAUDE.md`: Nur Anweisungen für Coding-Agents
- Bereits Dokumentiertes wird in Tests nicht wiederholt. Kommentare in Tests
  beschreiben ausschließlich die Testlogik, nie die Geschäftslogik.
- Testnamen beschreiben Ausgangslage und erwartetes Ergebnis, sodass der Testfall ohne
  Kommentar verständlich ist.
