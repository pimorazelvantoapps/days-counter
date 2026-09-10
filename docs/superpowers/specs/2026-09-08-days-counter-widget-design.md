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
- Anzeige: Tage bis zum Ziel oder seit ihm, „0“ am Zieltag; die Richtung (bis/seit) steht
  allein im Titel, das Blatt zeigt immer eine vorzeichenlose Zahl
- Angefangene Tage zählen voll: Am Vortag um 23:55 wird „1“ angezeigt
- Zieldatum beliebig, auch in der Vergangenheit
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
`domain` kennt nichts. `data` kennt `domain`. `ui` kennt nur `domain`, für die dort
gebündelten Compose-Textgrößen. `widget` und `config` kennen `domain`, `data` und `ui`, aber
nicht einander. Einzige Ausnahme: `widget` ruft `ConfigActivity.createIntent` auf, um den
Tipp auf ein Widget an dessen Konfiguration zu binden. Konfiguration und Widget
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
│   ├── Clock                   Schnittstelle: today(), days(): Flow<LocalDate>
│   ├── SystemClock             Produktiv-Implementierung, Gerätezeitzone
│   ├── DaysCalculator          (today, target) -> DisplayValue
│   ├── DisplayValue            Sealed: Remaining(n) | Reached | Elapsed(n), plus Anzeigetext
│   ├── DigitSizeTier           Schriftgrößen-Stufe nach Ziffernzahl (ONE_DIGIT … FIVE_OR_MORE_DIGITS)
│   ├── HeaderColor             Enum der 12 Farben mit ARGB-Wert
│   ├── WidgetConfig            title, targetDate, color
│   ├── WidgetUiState           Anzeigezustand, gemeinsam für `widget` und `config`
│   ├── WidgetUpdater           Schnittstelle: alle Widgets neu zeichnen
│   └── MidnightUpdateScheduler Schnittstelle: Mitternachtsalarm planen/entfernen
├── data/
│   ├── WidgetConfigRepository          Schnittstelle: Beobachten/Schreiben pro Widget-ID
│   └── DataStoreWidgetConfigRepository Implementierung, Preferences DataStore
├── ui/
│   └── TextSizes                Titel- und Wertgrößen je DigitSizeTier, geteilt von `widget`
│                                 und `config`; Compose-`TextUnit` gehört nicht in `domain`
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
Zeitzonenwechsel) in Tests mit festen Werten geprüft werden können. Neben `today()` liefert
sie mit `days()` den Tag als beobachtbaren Strom; `dateChanged()` meldet ihr, dass sie ihn
neu lesen muss (siehe Abschnitt 7).

## 5. Domänenlogik

### Tageszählung

Ausschließlich Kalenderdaten, keine Uhrzeiten. Damit zählen angefangene Tage automatisch
voll.

```
diff = Tage zwischen today und target
diff > 0  -> Remaining(diff)  Anzeige: Zahl (Tage bis zum Ziel)
diff == 0 -> Reached          Anzeige: "0"
diff < 0  -> Elapsed(-diff)   Anzeige: Zahl (Tage seit dem Ziel)
```

Die Richtung (bis/seit) steht bewusst nur im Typ, nicht in der Anzeige: Das Blatt zeigt in
beiden Fällen eine vorzeichenlose Zahl, ein 1x1-Feld hat keinen Platz für ein Vorzeichen -
ein Vorzeichen ist ein zusätzliches Zeichen und kostet damit nach der Schriftgrößen-Stufe
unten eine ganze Stufe - und der Titel trägt die Bedeutung („Bis Urlaub“ vs. „Seit Umzug“).

### Schriftgrößen-Stufe

| Ziffern | Stufe | Größe |
|---|---|---|
| 1, „0“, Platzhalter „–“ | `ONE_DIGIT` | 26sp |
| 2 | `TWO_DIGITS` | 21sp |
| 3 | `THREE_DIGITS` | 17sp |
| 4 | `FOUR_DIGITS` | 14sp |
| 5 und mehr | `FIVE_OR_MORE_DIGITS` | 11sp |

Die Größen liegen gebündelt in `ui.TextSizes` (Abschnitt 4), geteilt von Glance-Blatt und
Compose-Vorschau. Die statische Launcher-Vorschau (`res/layout/widget_preview.xml`) kann
kein Kotlin lesen und führt ihre eigene Kopie der beiden Größen, die sie braucht.

### Zieldatum

Jedes Datum ist ein gültiges Zieldatum, beliebig weit in Vergangenheit oder Zukunft. Es gibt
keine Validierung mehr: `ConfigUiState.isValid` bedeutet allein, dass ein Datum gewählt ist.

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

Eine unlesbare Store-Datei wird über einen `ReplaceFileCorruptionHandler` durch eine leere
ersetzt, statt jeden Lesevorgang scheitern zu lassen: ohne Launcher-Eintrag bliebe sonst nur
das Löschen der App-Daten in den Systemeinstellungen als Ausweg.

`WidgetConfigRepository` liefert `WidgetConfig` oder `null`, wenn ein Schlüssel fehlt oder
ein Wert nicht parsebar ist. `null` führt im Widget zum Platzhalter-Zustand. Gelesen wird als
Strom (`observe`), der bei jeder Änderung erneut liefert; `load` ist dessen erster Wert.

Beim Entfernen eines Widgets (`onDeleted`) löscht das Repository die drei Schlüssel. Wird das
letzte Widget entfernt, wird der Mitternachtsalarm in `onDisabled` abgemeldet; das System ruft
diesen Rückruf genau dann auf, sodass `AppWidgetManager` nicht nach übrigen Widgets befragt
werden muss.

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
`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`. In allen Fällen: dem `Clock` die Datumsänderung
melden, alle Widgets aktualisieren und den Alarm neu planen. Diese Broadcasts sind von den
Implicit-Broadcast-Beschränkungen ausgenommen und werden im Manifest registriert. Für
`BOOT_COMPLETED` wird `RECEIVE_BOOT_COMPLETED` deklariert.

### Laufende Komposition

`DaysCounterWidget` beobachtet seinen Zustand innerhalb der Komposition, Konfiguration über
`WidgetConfigRepository.observe` und Datum über `Clock.days()`, weil Glance `provideGlance` für
Updates an eine noch laufende Komposition nicht erneut aufruft. Ausführliche Begründung im
Docblock von `DaysCounterWidget.provideGlance`.

### Absicherung

Das Widget berechnet den Wert aus dem beobachteten Datum. Ein einmal verpasster Alarm wird
beim nächsten Anlass korrigiert.

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
- Platzhalter ohne Konfiguration: Standardtitel auf rotem Header, gedämpftes En-Dash „–“ im
  Blatt (bewusst kein Bindestrich, um ihn nicht mit einer Zahl zu verwechseln).
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
Der Dialog folgt dem Systemthema wie das Widget-Blatt: `values-night/themes.xml` für Fenster
und Statusleiste, dunkles Material-3-Farbschema für den Compose-Inhalt.

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
│ Zieldatum  [ 15. März 2027 ]   │  Tipp öffnet Material DatePicker, keine Einschränkung
│ Farbe      [ ●              ]  │  Kreis in aktueller Farbe, Tipp öffnet ColorPickerDialog
│         [ Abbrechen ] [ Speichern ] │
└────────────────────────────────┘
```

`ColorPickerDialog`: 3x4-Raster aus Kreisen, gewählte Farbe mit Häkchen. Auswahl
übernimmt sofort und schließt. Außerhalb tippen oder Zurück schließt ohne Änderung.

### Zustand

`ConfigViewModel`: `title`, `targetDate: LocalDate?` (beim Neuanlegen `null`), `color`.
Abgeleitet: `isValid` = ein Datum ist gewählt, beliebig in Vergangenheit oder Zukunft.
Speichern-Button sonst deaktiviert. `refreshToday()` (aufgerufen aus `onResume`) liest nur
das aktuelle Datum neu ein, damit die Live-Vorschau nicht veraltet, falls der Dialog über
Mitternacht offen bleibt - eine Validierung findet dabei nicht mehr statt.

### Speichern

Repository schreiben, `DaysCounterWidget.update(widgetId)` anstoßen,
`MidnightUpdateScheduler.schedule()`, `RESULT_OK` setzen, Activity beenden.

### Fehlerfälle

- Schreibfehler im DataStore: Snackbar, Activity bleibt offen.
- Lesefehler im DataStore: Dialog startet mit den Standardwerten, damit das Widget neu
  eingerichtet werden kann.

### Texte

`values/strings.xml` (en), `values-de/strings.xml`. Unterstützte Sprachen in
`locales_config.xml` für die Android-13-Sprachauswahl pro App deklariert. Datumsanzeige im
lokalisierten mittleren Format.

## 10. Tests

### Unit-Tests (JUnit 5, JVM)

Vollständige Abdeckung von `domain`:

- `DaysCalculator`: morgen → Remaining(1); heute → Reached; gestern → Elapsed(1); große
  Distanzen in beide Richtungen; Schaltjahr über 29. Februar; Jahreswechsel.
- Anzeigetext: `Remaining(42)` → „42“, `Reached` → „0“, `Elapsed(42)` → „42“.
- `DigitSizeTier` an den Ziffernübergängen 9|10, 99|100, 999|1000, 9999|10000 sowie „0“ und
  der Platzhalter.
- `HeaderColor`: 12 Einträge, eindeutige Namen, Rundreise Name→Enum→Name, unbekannter
  Name liefert Fehler statt Absturz.
- `SystemClock`: `days()` liefert beim Sammeln den heutigen Tag, erneut nach `dateChanged()`
  mit gewechseltem Tag und nicht zweimal für denselben Tag.
- `ConfigViewModel`: Startzustand, Laden, `isValid` (Datum gewählt, auch in der
  Vergangenheit), Speichern, `refreshToday()` hält die Vorschau aktuell; gefälschte `Clock` sowie
  gefälschtes Repository, Updater und Scheduler aus `app/src/sharedTest/java`, kein Android-
  Bezug nötig.

### Funktionale Tests (JUnit 4 + Robolectric)

- `WidgetConfigRepository`: Schreiben/Lesen, Isolation zwischen IDs, fehlende Schlüssel
  → `null`, korruptes Datum → `null`.
- `MidnightUpdateScheduler`: genau ein Alarm auf nächste Mitternacht in Gerätezeitzone,
  Neuplanung ersetzt, Abmelden entfernt (`ShadowAlarmManager`).
- `DateChangeReceiver`: jeder behandelte Broadcast meldet die Datumsänderung und löst
  Update und Neuplanung aus; die behandelten Aktionen und die Intent-Filter des Manifests
  stimmen in beiden Richtungen überein.
- `DaysCounterWidgetReceiver`: `onEnabled` plant den Alarm, `onDisabled` meldet ihn ab,
  `onDeleted` löscht die Konfiguration der entfernten Widgets.
- Glance-Layout mit `glance-testing`: Titel und Zahl für gegebene Konfiguration;
  Platzhalter-Zustand. Die Header-Farbe bleibt ungeprüft, weil die Unit-Test-Umgebung von
  Glance keinen Farb-Matcher anbietet.

### Instrumentierte Tests (Emulator, Compose UI Test)

- Vollständiger Konfigurationsflow bis `RESULT_OK` und gespeichertem Zustand; der Picker
  bietet dabei auch ein vergangenes Datum an, und das Speichern eines solchen funktioniert.
- Speichern deaktiviert ohne Datum.
- Abbrechen → `RESULT_CANCELED`, nichts geschrieben.
- Farbdialog: 12 Felder sichtbar, Auswahl aktualisiert Vorschau.
- Smoke-Test: Widget-Composable über `GlanceRemoteViews` zu `RemoteViews` übersetzen und
  in eine echte View-Hierarchie inflaten; rendert ohne Absturz, Zahl und Titel sind als
  `TextView` vorhanden. `AppWidgetHost` würde eine Shell-Berechtigung im Test erfordern.
- Laufende Komposition: über `GlanceAppWidget.runComposition` eine echte Glance-Session
  starten und prüfen, dass sie nach dem Speichern einer Konfiguration und nach einem
  Tageswechsel neue `RemoteViews` liefert (Platzhalter → „1“ → „0“).

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
