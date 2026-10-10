# Review vor der Play-Store-Veröffentlichung – Design

Datum: 2026-10-10
Status: Entwurf abgestimmt, bereit für Implementierungsplan

## 1. Ziel

Die App ist für Google Play vorbereitet (v1.0.3, Uploads als Entwurf, erstes Review durch Google
steht aus). Ein vollständiges Review über alle Bereiche des Repos soll Probleme finden, bevor die
App öffentlich wird: Sicherheit und Datenschutz, Fehler, Wartbarkeit, Sauberkeit der
Implementierung sowie Play-spezifische Risiken (Policy, Datensicherheitsangaben, Berechtigungen,
Signierung).

Erfolgreich ist das Review, wenn eine verifizierte, nach Schweregrad priorisierte Befundliste
vorliegt, in der jeder Befund Bereich, Fundstelle, Fehlerszenario und Beleg trägt, und wenn
ausdrücklich festgehalten ist, was nicht geprüft wurde.

## 2. Umfang

### Enthalten

- Alle versionierten Dateien des Repos (Stand `b320407`) sowie die ungetrackte Datei
  `gradle/gradle-daemon-jvm.properties`
- Play-Console-Angaben, soweit sie im Repo dokumentiert sind (`docs/play-store-setup.md`,
  `PRIVACY.md`, `distribution/`)
- Builds, Analyse des minifizierten APK und des Bundles, Laufzeitprüfung auf dem Emulator

### Nicht enthalten

- Behebung der Befunde: Das Ergebnis ist ausschließlich ein Bericht. Was behoben wird, entscheidet
  der Nutzer danach, jeweils als eigene Aufgabe.
- Installation zusätzlicher SDK-Werkzeuge oder System-Images. Laufzeitprüfung nur auf dem
  vorhandenen AVD (API 34); Verhalten auf API 33 (minSdk) und 36+ gilt als „nicht geprüft“.
- Play-Console-Einstellungen, die nicht im Repo stehen; Launcher anderer Hersteller.
- Jede Änderung am Repo, Commits, Pushes, Uploads und das Anlegen von Issues.

## 3. Bereiche und Prüfkatalog

Jeder Bereich wird von einem eigenen, nur lesenden Reviewer-Agenten geprüft. Maßstab sind
`CLAUDE.md` und die Spec `2026-09-08-days-counter-widget-design.md`.

| # | Bereich | Dateien | Prüfpunkte |
|---|---|---|---|
| R1 | Kotlin-Code | `app/src/main/java/**` | Korrektheit der Datumsrechnung (Mitternacht, Sommerzeit, Zeitzone), Coroutines und Lebenszyklus, Glance-Regeln aus `CLAUDE.md`, Abhängigkeitsrichtung der Packages, Fehlerbehandlung (DataStore korrupt oder leer, gelöschte Widget-ID), Benennung, toter Code |
| R2 | Sicherheit & Datenschutz | `AndroidManifest.xml`, Receiver, `ConfigActivity`, `PRIVACY.md`, `docs/play-store-setup.md` | exportierte Komponenten, Prüfung der übergebenen `appWidgetId`, PendingIntent-Flags, Backup- und Datenextraktionsregeln, Übereinstimmung von `PRIVACY.md` und Datensicherheitsangaben mit dem tatsächlichen Verhalten, Berechtigungen; `/security-review` als zusätzliche Eingabe |
| R3 | Ressourcen & UX | `app/src/main/res/**` | Vollständigkeit und Qualität der Übersetzungen, Pluralformen, Zahlen- und Datumsformat je Locale, adaptives und monochromes Icon, Dunkelmodus, Kontrast, TalkBack und Inhaltsbeschreibungen, große Schrift, RTL, Widget-Größen und -Vorschau |
| R4 | Build, R8, Abhängigkeiten | `*.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `gradle/wrapper/`, `app/lint.xml`, `config/detekt/` | veraltete Abhängigkeiten oder solche mit bekannten CVEs, R8- und Keep-Risiken, Ausnahmen in `lint.xml`, Checksumme des Gradle-Wrappers, `versionCode`-Schema, Signierung |
| R5 | CI & Release-Pipeline | `.github/**`, `.releaserc.json`, `.commitlintrc.json`, `scripts/` | Actions per SHA oder Tag festgelegt, minimale `permissions`, Umgang mit Secrets (Keystore, Logs, Konfigurations-Cache), Branch-Policy des Environments, Workload Identity, Dependabot-Abdeckung, Rennbedingungen in semantic-release, Wiederholbarkeit des Uploads |
| R6 | Tests | `app/src/test/`, `app/src/androidTest/`, `app/src/sharedTest/` | Abdeckung der Risikopfade aus R1, Testnamen nach `CLAUDE.md`, Flakiness (Zeit, Animationen), Lücken in den Fakes |
| R7 | Doku & Play-Metadaten | `README.md`, `CLAUDE.md`, `docs/`, `distribution/`, `LICENSE` | Aktualität, Widersprüche zwischen Dokumenten, Play-Policy (Zielgruppe, Inhaltseinstufung, Länge der „Neuerungen“), Lizenzhinweise für Abhängigkeiten |
| R8 | Laufzeit | Builds, APK/AAB, Emulator | siehe Abschnitt 5 |

## 4. Schweregrade und Befundformat

Maßstab ist die Wirkung auf die Veröffentlichung im Play Store.

| Stufe | Bedeutung |
|---|---|
| Blocker | Ablehnung durch Google, Datenverlust, Sicherheitslücke, Absturz im Normalbetrieb |
| Hoch | für Nutzer sichtbarer Fehler in realistischen Fällen |
| Mittel | Wartbarkeitsrisiko, Fehler in Randfällen, Verstoß gegen `CLAUDE.md`, fehlender Test für einen Risikopfad, nicht festgelegte Action |
| Niedrig | Kosmetik und Konsistenz: Formulierung, Benennung, Ungenauigkeit in der Doku |

Format je Befund:

```
### [R2-03] <Stufe> – <Titel>
Bereich: <Bereich> · Fundstelle: <Pfad:Zeile>
Problem: Ein Satz.
Szenario: Konkrete Eingabe/Zustand → falsches Ergebnis.
Beleg: Codeauszug, Logzeile oder Screenshot-Pfad.
Vorschlag: Ein bis drei Sätze, kein Code.
Status: bestätigt | plausibel | nicht geprüft
```

Aufbau des Berichts:

1. Zusammenfassung: Ampel „bereit für die Produktion?“, Anzahl je Stufe
2. Blocker und Hoch, ausführlich
3. Mittel und Niedrig, gruppiert nach Bereich
4. Abdeckung: was geprüft wurde, was ausdrücklich nicht, welche Werkzeuge gelaufen sind,
   Anzahl verworfener Befunde
5. Anhang: Ausgaben von Builds und Analysen, Screenshots

## 5. Builds, APK-Analyse und Laufzeitprüfung (R8)

### 5a. Builds und Werkzeuge

- `./gradlew check` aus dem Wurzelverzeichnis, inklusive der Berichte von Lint und detekt
- `./gradlew assembleStaging bundleRelease -PappVersion=1.0.3`, ohne Signierung
- `./gradlew :app:dependencies` und `libs.versions.toml` gegen aktuelle Versionen und bekannte
  Schwachstellen (OSV, GitHub Advisory)

### 5b. APK- und Bundle-Analyse

Mit `aapt2`, `apksigner` aus den Build-Tools und `unzip`:

- Merged Manifest: Berechtigungen (auch aus Bibliotheken), exportierte Komponenten,
  `debuggable`, Cleartext-Traffic, Backup-Regeln
- Inhalt: verirrte Dateien, Größe, Wirkung des Ressourcen-Shrinkings
- R8-Mapping: unerwartet erhaltene oder fehlende Klassen
- Ziel- und Mindest-SDK im Bundle

### 5c. Laufzeit auf dem Emulator

AVD `Pixel_3a_API_34_extension_level_7_arm64-v8a`, `adb root` für die Zeitmanipulation.

- `connectedCheck` mit abgeschalteten Animationen; Testzahl größer als 0 prüfen.
  Danach `scripts/smoke-test-minified.sh` gegen den Staging-Build.
- Manuelle Szenarien auf dem Staging-Build, jeweils mit Screenshot und `logcat`-Auszug:
  1. Widget platzieren, konfigurieren, abbrechen (bleibt ein unkonfiguriertes Widget zurück?)
  2. Zieldatum in der Zukunft, heute, in der Vergangenheit, sehr weit entfernt (Ziffernstufen)
  3. Mitternachtswechsel: Uhr auf 23:59:30 stellen, Aktualisierung bis zu zehn Minuten abwarten
  4. Zeitzonenwechsel, manuelles Stellen der Uhr, Neustart, App-Update (`MY_PACKAGE_REPLACED`)
  5. Wechsel zwischen Hell- und Dunkelmodus bei sichtbarem Widget
  6. Sprache Deutsch und Englisch, systemweit und pro App über `localeConfig`
  7. Schriftgröße 200 %, maximale Anzeigegröße, TalkBack-Ansage des Widgets
  8. Mehrere Widgets mit unterschiedlicher Konfiguration; Widget entfernen (werden die Daten
     aufgeräumt?)
  9. Prozess beenden (`am kill`), danach Tipp auf das Widget
  10. Missbrauch: `ConfigActivity` per `adb shell am start` ohne oder mit fremder `appWidgetId`
      starten; Broadcast an den exportierten Widget-Receiver senden
- Am Ende Uhrzeit, Zeitzone, Sprache, Schrift- und Anzeigegröße und Animationen zurücksetzen.
  Den Snapshot des AVD nicht überschreiben.

## 6. Ablauf und Verifikation

1. Vorbereitung: Arbeitsverzeichnis `review/` im Scratchpad der Sitzung anlegen, Stand des Repos
   notieren.
2. Parallel: Reviewer R1 bis R7 als Subagenten starten (nur lesend, kein Build). Gleichzeitig
   führt die Hauptsitzung 5a bis 5c aus.
3. Rücklauf: Jeder Reviewer liefert Befunde im Format aus Abschnitt 4 mit Status „plausibel“.
4. Verifikation: zwei Verifikations-Agenten (Code, Sicherheit und Tests; Ressourcen, Build,
   Pipeline und Doku) versuchen, jeden Befund am Code zu widerlegen. Wo möglich, wird der Befund
   auf dem Emulator reproduziert. Ergebnis je Befund: bestätigt, plausibel oder verworfen.
   Verworfene Befunde entfallen und werden nur gezählt.
5. Konsolidierung: Doppelungen über Bereichsgrenzen auflösen, Schweregrade endgültig festlegen,
   Bericht nach `review/2026-10-10-play-store-review.md` im Scratchpad schreiben und im Chat
   zusammenfassen.

Insgesamt neun Agenten: sieben Reviewer, zwei Verifizierer.

### Leitplanken

- Am Repo ändert sich nichts außer Build-Ausgaben in ignorierten Verzeichnissen.
- Keine Commits, Pushes, Uploads oder Issues. Externe Recherche schickt nur Paketnamen und
  Versionen an Suchdienste.
- Ein Befund, der sofortiges Eingreifen erfordert (etwa ein offengelegtes Secret in der
  Git-Historie), wird sofort gemeldet statt erst im Bericht.
- Der Bericht bleibt lokal und wird nicht committet; Sicherheitsbefunde werden erst nach ihrer
  Behebung öffentlich.
