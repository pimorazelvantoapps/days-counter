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
./gradlew assembleStaging && scripts/smoke-test-minified.sh   # Minifizierten Build auf Emulator prüfen
./gradlew assembleDebug    # APK für Sideload
./gradlew ktlintFormat     # Formatierung anwenden
```

Beide Prüfläufe immer aus dem Wurzelverzeichnis starten: detekt hängt am Wurzelprojekt,
`:app:check` überspringt es stillschweigend und meldet trotzdem Erfolg.

`check` muss vor jedem Commit fehlerfrei durchlaufen. Warnungen sind Fehler; Schwellwerte
in detekt oder Lint nicht aufweichen, Baselines nicht befüllen.

## Commits

- Jede Commit-Nachricht folgt Conventional Commits (englisch, Imperativ). Der Typ bestimmt die
  nächste Version (Tabelle im `README.md`, Abschnitt „Releases“), deshalb nach der Wirkung für
  Nutzer wählen, nicht nach dem Umfang der Änderung.
- Keine Merge-Commits: Branches vor dem Merge auf `main` rebasen. Die CI prüft jeden einzelnen
  Commit eines PRs, weil jeder einzeln auf `main` landet.
- Version nie von Hand in `app/build.gradle.kts` setzen. semantic-release übergibt sie beim
  Release als `-PappVersion`.

## Code

- Kotlin Official Code Style, durchgesetzt per ktlint. `ktlintFormat` laufen lassen und sein
  Ergebnis übernehmen, statt von Hand dagegen zu formatieren.
- Abhängigkeitsrichtung der Packages einhalten: `domain` kennt nichts; `data` kennt
  `domain`; `ui` kennt nur `domain` (für `DigitSizeTier`); `widget` und `config` kennen
  `domain`, `data` und `ui`, aber nicht einander. Einzige Ausnahme: `widget` ruft
  `ConfigActivity.createIntent` auf, um den Tipp auf ein Widget an dessen Konfiguration zu
  binden.
- `ui` enthält nur, was Compose's `TextUnit` braucht und deshalb nicht in `domain` darf
  (`domain` bleibt frei von Android-/Compose-Typen): aktuell `TextSizes`, geteilt vom
  Glance-Blatt in `widget` und seiner Compose-Vorschau in `config`.
- `WidgetUpdater` und `MidnightUpdateScheduler` sind Schnittstellen in `domain` (wie `Clock`),
  ihre Implementierungen `GlanceWidgetUpdater` und `AlarmManagerMidnightUpdateScheduler` liegen
  in `widget`, weil sowohl `config` (zum Abschluss des Speicherns) als auch `widget` sie
  brauchen und `widget` bereits von `config` abhängt: eine Implementierung in `config` oder
  `widget` selbst würde einen Package-Zyklus erzeugen.
- Zeit nie direkt über `LocalDate.now()` lesen, immer über die `Clock`-Schnittstelle.
- Der Widget-Inhalt wird innerhalb der Komposition beobachtet (`WidgetConfigRepository.observe`,
  `Clock.days()`), nie vor `provideContent` einmalig gelesen. Grund und Konsequenzen stehen im
  Docblock von `DaysCounterWidget.provideGlance`.
- In Glance nie `ColorProvider(@ColorRes Int)` verwenden. Die Funktion ist `@RestrictTo`, weil
  die Farbe im Prozess des Launchers statt der App aufgelöst werden kann, was den Dunkelmodus
  bricht. Stattdessen `ColorProvider(day, night)` oder das öffentliche `background(@ColorRes)`.
- Kein DI-Framework, Abhängigkeiten werden in `AppContainer` manuell konstruiert.
- Lints `UnusedResources` ist ein Fehler: eine Ressource erst anlegen, wenn sie im selben
  Commit auch verwendet wird.
- Test-Fakes, `ControlledClock` und `PinnedLocaleRule` liegen in `app/src/sharedTest/java` und
  werden von Unit- und Instrumentation-Tests gemeinsam genutzt. Keine zweite Kopie anlegen.
- Instrumentation-Tests, die auf UI-Texte matchen, brauchen `PinnedLocaleRule`. Materials
  eigene Zeichenketten sind interne Ressourcen und lassen sich nicht wiederverwenden: Lints
  `PrivateResource` schlägt an, und das ist hier ein Fehler.
- Robolectric unterstützt `targetSdk 37` nicht (`maxSdkVersion=36`). Robolectric-Testklassen
  tragen deshalb `@Config(sdk = [35])`; `compileSdk`/`targetSdk` des Projekts bleiben unverändert.
- Release und `staging` sind mit R8 minifiziert; `staging` ist das Release mit Debug-Signatur
  und dient nur dem Smoke-Test. Die instrumentierten Tests laufen gegen Debug; nicht per
  Keep-Regeln gegen den minifizierten Build umstellen (Grund im Kopf von
  `scripts/smoke-test-minified.sh`).
- `connectedCheck` meldet Erfolg auch dann, wenn die App vor dem Test-Runner abstürzt und kein
  Test läuft. Nach Änderungen an Abhängigkeiten oder R8 die Testzahl prüfen.
- Vor `connectedCheck` die Animationen abschalten, sonst sind die Compose-Tests flaky:
  `adb shell settings put global {window,transition}_animation_scale 0` und
  `animator_duration_scale 0`.

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
