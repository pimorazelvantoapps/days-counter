# Days Counter Widget – Design

Datum: 2026-09-08
Status: Entwurf abgestimmt, bereit für Implementierungsplan

## 1. Ziel

Ersatz für das nicht mehr gepflegte Android-Widget „days left“. Ein 1x1-Homescreen-Widget
zeigt die Anzahl der Tage bis zu einem vom Nutzer gewählten Datum. Mehrere unabhängige
Instanzen mit eigenem Titel, Zieldatum und Header-Farbe sind möglich.

## 2. Umfang

### Enthalten

- 1x1-Widget, nicht skalierbar, beliebig viele Instanzen
- Pro Instanz: Titel (max. 20 Zeichen), Zieldatum, Header-Farbe aus 12 festen Farben
- Anzeige: Tage bis zum Ziel, „0“ am Zieltag, „-“ nach dem Zieltag
- Angefangene Tage zählen voll: Am Vortag um 23:55 wird „1“ angezeigt
- Zieldatum bei Eingabe frühestens morgen
- Exakte Aktualisierung um Mitternacht, außerdem bei Zeitzonen-/Zeitänderung, Neustart, App-Update
- Konfigurationsdialog beim Platzieren und bei Tipp auf das Widget
- Blatt folgt dem Systemthema (hell/dunkel), Header behält gewählte Farbe
- Deutsch und Englisch, Englisch als Fallback
- Unit-, funktionale und instrumentierte Tests; statische Analyse im Build

### Nicht enthalten

- Symbole/Emojis auf dem Blatt
- Andere Widgetgrößen oder Skalierung
- Launcher-Eintrag für die App
- Store-Veröffentlichung, Release-Signing, CI-Pipeline
- Dependency-Injection-Framework, Multi-Modul-Aufbau
- Periodisches `updatePeriodMillis`, WorkManager

## 3. Rahmenbedingungen

| Punkt | Entscheidung |
|---|---|
| Sprache | Kotlin |
| minSdk / compileSdk / targetSdk | 33 / 37 / 37 |
| Widget-UI | Jetpack Glance |
| Konfigurations-UI | Jetpack Compose, Material 3 |
| Persistenz | App-weites Preferences DataStore, Schlüssel suffigiert je Widget-ID (nicht Glance-eigen, siehe Abschnitt 6) |
| Zeitplanung | AlarmManager, exakter Alarm, Berechtigung `USE_EXACT_ALARM` |
| Build | Gradle Kotlin DSL, Version Catalog |
| Coding Standard | Kotlin Official Code Style, durchgesetzt per ktlint |
| Statische Analyse | ktlint, detekt, Android Lint, Compiler-Warnungen als Fehler |
| Verteilung | Privat per Sideload (Debug-APK) |
| Package | `com.pimorazelvanto.dayscounter` |

## 4. Architektur

Ein Gradle-Modul `app`. Trennung über Packages mit fester Abhängigkeitsrichtung:
`domain` kennt nichts. `data` kennt `domain`. `widget` und `config` kennen `domain` und
`data`, aber nicht einander. Einzige Ausnahme: `widget` ruft `ConfigActivity.createIntent`
auf, um den Tipp auf ein Widget an dessen Konfiguration zu binden. Konfiguration und Widget
kommunizieren sonst nur über den Speicher: `config` schreibt und stößt ein Widget-Update an,
`widget` liest.

`Clock`, `WidgetUpdater` und `MidnightUpdateScheduler` sind Schnittstellen in `domain`.
`WidgetUpdater` und `MidnightUpdateScheduler` werden sowohl von `config` (zum Abschluss des
Speicherns) als auch von `widget` gebraucht; da `widget` bereits von `config` abhängt, würde
eine Implementierung in `config` einen Package-Zyklus erzeugen. Die Implementierungen
`GlanceWidgetUpdater` und `AlarmManagerMidnightUpdateScheduler` liegen deshalb in `widget`.

```
com.pimorazelvanto.dayscounter
├── DaysCounterApplication      Application, hält AppContainer
├── AppContainer                Manuelle Konstruktion der Abhängigkeiten
├── domain/                     Reine Kotlin-Logik, keine Android-Abhängigkeit
│   ├── Clock                   Schnittstelle: today(): LocalDate
│   ├── SystemClock             Produktiv-Implementierung, Gerätezeitzone
│   ├── DaysCalculator          (today, target) -> DisplayValue
│   ├── DisplayValue            Sealed: Days(n) | Reached | Passed, plus Anzeigetext
│   ├── DigitSizeTier           Schriftgrößen-Stufe nach Ziffernzahl
│   ├── TargetDateValidator     (today, candidate) -> Valid | NotInFuture
│   ├── HeaderColor             Enum der 12 Farben mit ARGB-Wert
│   ├── WidgetConfig            title, targetDate, color
│   ├── WidgetUiState           Anzeigezustand, gemeinsam für `widget` und `config`
│   ├── WidgetUpdater           Schnittstelle: alle Widgets neu zeichnen
│   └── MidnightUpdateScheduler Schnittstelle: Mitternachtsalarm planen/entfernen
├── data/
│   ├── WidgetConfigRepository          Schnittstelle: Lesen/Schreiben pro Widget-ID
│   └── DataStoreWidgetConfigRepository Implementierung, Preferences DataStore
├── widget/
│   ├── DaysCounterWidget                    GlanceAppWidget
│   ├── DaysCounterWidgetReceiver            GlanceAppWidgetReceiver, Lifecycle
│   ├── GlanceWidgetUpdater                 Implementierung von WidgetUpdater
│   ├── AlarmManagerMidnightUpdateScheduler Implementierung von MidnightUpdateScheduler
│   └── DateChangeReceiver                  Alarm, TIMEZONE_CHANGED, TIME_CHANGED,
│                                            BOOT_COMPLETED, MY_PACKAGE_REPLACED
└── config/
    ├── ConfigActivity          Einstieg beim Platzieren und bei Tipp
    ├── ConfigViewModel         Zustand, Validierung, Speichern
    ├── ConfigScreen            Composable: Vorschau, Titel, Datum, Farbe
    ├── ColorPickerDialog       Dialog mit 3x4-Farbraster
    └── WidgetPreview           Compose-Nachbildung des Blatts
```

Die Zeitquelle `Clock` wird injiziert, damit Randfälle (Vortag 23:55, Mitternacht,
Zeitzonenwechsel) in Tests mit festen Werten geprüft werden können.

## 5. Domänenlogik

### Tageszählung

Ausschließlich Kalenderdaten, keine Uhrzeiten. Damit zählen angefangene Tage automatisch
voll.

```
diff = Tage zwischen today und target
diff > 0  -> Days(diff)     Anzeige: Zahl
diff == 0 -> Reached        Anzeige: "0"
diff < 0  -> Passed         Anzeige: "-"
```

### Schriftgrößen-Stufe

| Ziffern | Stufe |
|---|---|
| 1–2, „0“, „-“ | groß |
| 3 | mittel |
| 4 und mehr | klein |

### Validierung

`TargetDateValidator.validate(today, candidate)` liefert `Valid` genau dann, wenn
`candidate > today`. Gilt nur bei der Eingabe. Gespeicherte Daten dürfen später `Reached`
und `Passed` werden.

### Farben

`HeaderColor` mit 12 Einträgen. Persistiert wird der Enum-Name, nicht der Farbwert. Alle
Töne so gewählt, dass weißer Text lesbar bleibt (Gelb als kräftiger Amber-Ton).
Rasterreihenfolge zeilenweise:

| | | |
|---|---|---|
| Rot | Orange | Gelb |
| Grün | Türkis | Blau |
| Indigo | Lila | Pink |
| Braun | Grau | Schwarz |

Standardfarbe für ein neues Widget: **Rot**.

### Standardtitel

Aus den Sprachressourcen: „Tage“ (de), „days“ (en). Maximal 20 Zeichen, längere Eingabe
wird im Textfeld abgeschnitten. Im Widget einzeilig mit Auslassungspunkten gekürzt.

## 6. Speicher

Ein Preferences-DataStore der App (Datei `widget_configs`), Schlüssel pro Widget-ID mit Suffix `_<appWidgetId>`. Bewusst nicht der Glance-eigene Zustand, weil dessen `GlanceId` nur für real existierende Widgets erzeugt werden kann und damit in JVM-Tests nicht verfügbar ist. Drei Schlüssel pro Widget:

| Schlüssel | Typ | Inhalt |
|---|---|---|
| `title_<id>` | String | Anzeigetitel |
| `target_date_<id>` | String | ISO-8601, z. B. `2027-03-15` |
| `color_<id>` | String | Enum-Name aus `HeaderColor` |

`WidgetConfigRepository` liefert `WidgetConfig` oder `null`, wenn ein Schlüssel fehlt oder
ein Wert nicht parsebar ist. `null` führt im Widget zum Platzhalter-Zustand.

Beim Entfernen eines Widgets (`onDeleted`) löscht das Repository die drei Schlüssel. Meldet
`AppWidgetManager` danach keine Widgets dieser App mehr, wird der Mitternachtsalarm abgemeldet.

## 7. Aktualisierung

### Mitternachtsalarm

`MidnightUpdateScheduler` plant über `AlarmManager.setExactAndAllowWhileIdle` einen
einzigen Alarm für alle Widgets auf 00:00:00 lokaler Zeit des nächsten Tages. Die
Berechtigung `USE_EXACT_ALARM` (ab API 33 normal, immer erteilt) wird im Manifest
deklariert. Kein Fallback nötig. `setAlarmClock` wird nicht verwendet, weil es ein
Wecker-Symbol in der Statusleiste einblendet.

Planung ist idempotent (gleicher PendingIntent, Neuplanung ersetzt). Neu geplant wird:

- nach jedem Feuern
- beim Speichern einer Konfiguration
- nach `BOOT_COMPLETED` (Alarme überleben Neustart nicht)
- nach `MY_PACKAGE_REPLACED`

### Zeitänderungen

`DateChangeReceiver` reagiert auf den Alarm sowie `TIMEZONE_CHANGED`, `TIME_CHANGED`,
`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`. In allen Fällen: alle Widgets aktualisieren und
den Alarm neu planen. Diese Broadcasts sind von den Implicit-Broadcast-Beschränkungen
ausgenommen und werden im Manifest registriert. Für `BOOT_COMPLETED` wird
`RECEIVE_BOOT_COMPLETED` deklariert.

### Absicherung

Das Widget berechnet den Wert bei jedem Rendern aus dem aktuellen Datum. Ein einmal
verpasster Alarm wird beim nächsten Anlass korrigiert.

## 8. Widget-Darstellung

```
┌──────────────┐
│    Tage      │  Header ~1/3 Höhe: Farbfläche, weißer Titel, einzeilig, gekürzt
├──────────────┤
│              │
│     42       │  Blatt ~2/3 Höhe: Zahl zentriert, Größe nach DigitSizeTier
│              │
└──────────────┘
```

- Flache Farbflächen, keine Schatten, kein Rahmen. Ecken über `cornerRadius` mit
  `system_app_widget_background_radius`.
- Blatt: Glance-Farbressourcen mit Hell-/Dunkelvariante (hell: fast weiß / dunkle Zahl;
  dunkel: dunkelgrau / helle Zahl). Header immer die gewählte `HeaderColor`, Titel weiß.
  Keine Material-You-Dynamikfarben.
- Widget-Info: `targetCellWidth=1`, `targetCellHeight=1`, `minWidth/minHeight=40dp`,
  `resizeMode=none`, `configure=ConfigActivity`, kein `updatePeriodMillis`.
- Tipp auf die gesamte Fläche: `actionStartActivity(ConfigActivity)` mit Widget-ID.
- Platzhalter ohne Konfiguration: Standardtitel auf rotem Header, „–“ gedämpft im Blatt.
- Launcher-Vorschau über `previewLayout`, ein kleines XML-Layout, das Header und Zahl mit
  denselben Farb- und Maßressourcen nachbildet. Glance-Composables lassen sich nicht ohne
  platziertes Widget als Vorschau registrieren, und die App hat keinen Startpunkt davor.

## 9. Konfigurationsoberfläche

### Einstieg

1. Beim Platzieren über `configure` im Widget-Info-XML. `RESULT_CANCELED` als Vorgabe,
   `RESULT_OK` mit Widget-ID erst beim Speichern; ohne Speichern entfernt der Launcher das
   Widget.
2. Beim Tipp auf ein bestehendes Widget mit gespeicherten Werten als Startzustand.
   Abbrechen lässt das Widget unverändert.

Ohne gültige Widget-ID im Intent beendet sich die Activity sofort. Kein Launcher-Eintrag.

### Aufbau

```
┌────────────────────────────────┐
│ Widget einrichten              │  Top App Bar
├────────────────────────────────┤
│ ┌────────┐                     │
│ │  Tage  │  Live-Vorschau      │  WidgetPreview, gleiche Konstanten wie Glance
│ │   42   │                     │
│ └────────┘                     │
│ Titel      [ Tage          ]   │  TextField, max. 20 Zeichen, Zeichenzähler
│ Zieldatum  [ 15. März 2027 ]   │  Tipp öffnet Material DatePicker, Untergrenze morgen
│ Farbe      [ ●              ]  │  Kreis in aktueller Farbe, Tipp öffnet ColorPickerDialog
│         [ Abbrechen ] [ Speichern ] │
└────────────────────────────────┘
```

`ColorPickerDialog`: 3x4-Raster aus Kreisen, gewählte Farbe mit Häkchen. Auswahl
übernimmt sofort und schließt. Außerhalb tippen oder Zurück schließt ohne Änderung.

### Zustand

`ConfigViewModel`: `title`, `targetDate: LocalDate?` (beim Neuanlegen `null`), `color`.
Abgeleitet: `isValid` = Datum vorhanden und Validator liefert `Valid`. Speichern-Button
sonst deaktiviert.

### Speichern

Repository schreiben, `DaysCounterWidget.update(widgetId)` anstoßen,
`MidnightUpdateScheduler.schedule()`, `RESULT_OK` setzen, Activity beenden.

### Fehlerfälle

- Datum wird bei offenem Dialog über Mitternacht ungültig: Speichern deaktiviert,
  Hinweistext unter dem Datumsfeld.
- Schreibfehler im DataStore: Snackbar, Activity bleibt offen.

### Texte

`values/strings.xml` (en), `values-de/strings.xml`. Unterstützte Sprachen in
`locales_config.xml` für die Android-13-Sprachauswahl pro App deklariert. Datumsanzeige im
lokalisierten mittleren Format.

## 10. Tests

### Unit-Tests (JUnit 5, JVM)

Vollständige Abdeckung von `domain`:

- `DaysCalculator`: morgen → 1; heute → Reached; gestern → Passed; große Distanzen;
  Schaltjahr über 29. Februar; Jahreswechsel.
- Anzeigetext: `Days(42)` → „42“, `Reached` → „0“, `Passed` → „-“.
- `DigitSizeTier` für 1, 2, 3, 4, 5 Ziffern sowie „0“ und „-“.
- `TargetDateValidator`: heute ungültig, morgen gültig, gestern ungültig.
- `HeaderColor`: 12 Einträge, eindeutige Namen, Rundreise Name→Enum→Name, unbekannter
  Name liefert Fehler statt Absturz.
- `ConfigViewModel`: Startzustand, Laden, `isValid`, Speichern; gefälschte `Clock` sowie
  gefälschtes Repository, Updater und Scheduler aus `app/src/sharedTest/java`, kein Android-
  Bezug nötig.

### Funktionale Tests (JUnit 4 + Robolectric)

- `WidgetConfigRepository`: Schreiben/Lesen, Isolation zwischen IDs, fehlende Schlüssel
  → `null`, korruptes Datum → `null`.
- `MidnightUpdateScheduler`: genau ein Alarm auf nächste Mitternacht in Gerätezeitzone,
  Neuplanung ersetzt, Abmelden entfernt (`ShadowAlarmManager`).
- `DateChangeReceiver`: jeder behandelte Broadcast löst Update und Neuplanung aus.
- Glance-Layout mit `glance-testing`: Titel, Zahl, Farbe für gegebene Konfiguration;
  Platzhalter-Zustand.

### Instrumentierte Tests (Emulator, Compose UI Test)

- Vollständiger Konfigurationsflow bis `RESULT_OK` und gespeichertem Zustand.
- Speichern deaktiviert ohne Datum.
- Abbrechen → `RESULT_CANCELED`, nichts geschrieben.
- Farbdialog: 12 Felder sichtbar, Auswahl aktualisiert Vorschau.
- Smoke-Test: Widget-Composable über `GlanceRemoteViews` zu `RemoteViews` übersetzen und
  in eine echte View-Hierarchie inflaten; rendert ohne Absturz, Zahl und Titel sind als
  `TextView` vorhanden. `AppWidgetHost` würde eine Shell-Berechtigung im Test erfordern.

### Statische Analyse (im Gradle-Task `check`)

- ktlint, Kotlin Official Style, `ktlintFormat` zum Formatieren, `check` bricht bei
  Verstößen ab.
- detekt, Standardkonfiguration plus Compose-Regeln, leere Baseline.
- Android Lint mit `warningsAsErrors = true`, `abortOnError = true`.
- Kotlin-Compiler mit `allWarningsAsErrors = true`.

### Ausführung

```
./gradlew check            # Lint, ktlint, detekt, Unit- und Robolectric-Tests
./gradlew connectedCheck   # Instrumentierte Tests auf laufendem Emulator
./gradlew assembleDebug    # APK für Sideload
```
