# Review vor der Play-Store-Veröffentlichung – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein verifizierter, priorisierter Befundbericht über alle Bereiche der App, lokal im Scratchpad.

**Architecture:** Sieben nur lesende Reviewer-Agenten (R1–R7) laufen im Hintergrund parallel,
während die Hauptsitzung Builds, APK-Analyse und Emulator-Szenarien (R8) ausführt. Zwei
Verifikations-Agenten versuchen danach, jeden Befund zu widerlegen. Die Hauptsitzung
konsolidiert alles zu einem Bericht.

**Tech Stack:** Gradle, Android SDK (`build-tools/37.0.0`: `aapt2`, `apksigner`; `platform-tools/adb`;
`emulator`), AVD `Pixel_3a_API_34_extension_level_7_arm64-v8a`, Agent-Tool, WebSearch.

**Spec:** `docs/superpowers/specs/2026-10-10-play-store-review-design.md`

## Global Constraints

- Ergebnis ist ausschließlich ein Bericht. Kein Befund wird behoben.
- Am Repo ändert sich nichts außer Build-Ausgaben in ignorierten Verzeichnissen. Keine Commits,
  Pushes, Uploads, Issues.
- Keine Installation von SDK-Werkzeugen oder System-Images. Laufzeit nur auf API 34; API 33 und
  36+ gelten als „nicht geprüft“.
- Externe Recherche schickt nur Paketnamen und Versionen an Suchdienste.
- Ein Befund, der sofortiges Eingreifen erfordert (etwa ein Secret in der Git-Historie), wird
  sofort an den Nutzer gemeldet.
- Bericht: `$REVIEW/2026-10-10-play-store-review.md`, wird nicht committet.
- Prüfläufe immer aus dem Wurzelverzeichnis (`./gradlew check`, nie `:app:check`).
- Schweregrade und Befundformat exakt wie in Spec Abschnitt 4.
- Abweichung von der Spec: `/security-review` prüft nur ausstehende Änderungen eines Branches
  und hätte hier keinen Diff. R2 prüft stattdessen den gesamten Code nach OWASP MASVS; im
  Bericht unter „Abdeckung“ vermerken.

## Review Focus

Fehlerquellen des Review-Vorgangs selbst, die kein Schritt automatisch abfängt:

1. `connectedCheck` meldet Erfolg mit null Tests → Testzahl immer aus den XML-Ergebnissen lesen (Task 5).
2. `am force-stop` löscht die Alarme der App und verfälscht das Mitternachts-Szenario → zum Beenden nur `am kill` verwenden (Task 6).
3. Emulatorzustand bleibt verstellt (Uhr, Sprache, Schrift, Animationen) → Task 7 läuft auch nach abgebrochenen Szenarien.
4. Reviewer erfinden Fundstellen oder bewerten Spec-konformes Verhalten als Fehler → Verifikation prüft jede Fundstelle gegen Code und Spec (Task 8).
5. Doppelte Befunde über Bereichsgrenzen (etwa `appWidgetId` in R1, R2 und R6) → Konsolidierung führt sie unter einer ID zusammen (Task 9).

---

### Task 1: Vorbereitung

**Files:**
- Create: `$REVIEW/` (Scratchpad der ausführenden Sitzung, Unterordner `review/`)
- Create: `$REVIEW/00-baseline.txt`

- [ ] **Step 1: Arbeitsverzeichnis anlegen**

`$REVIEW` ist `<Scratchpad-Verzeichnis der Sitzung>/review`. In jedem Bash-Aufruf neu setzen.

```bash
REVIEW="<scratchpad>/review"
mkdir -p "$REVIEW"/{findings,logs,screens,apk}
```

- [ ] **Step 2: Stand des Repos festhalten**

```bash
cd /Users/plook/Sites/android/days_counter
{ git log -1 --format='%H %s'; git status --short; git tag --sort=-v:refname | head -1; } > "$REVIEW/00-baseline.txt"
cat "$REVIEW/00-baseline.txt"
```

Expected: HEAD-Commit, ungetrackte `gradle/gradle-daemon-jvm.properties`, Tag `v1.0.3`.

---

### Task 2: Reviewer R1–R7 starten

**Interfaces:**
- Produces: je Reviewer eine Datei `$REVIEW/findings/R<n>.md` im Befundformat; Eingabe für Task 8.

- [ ] **Step 1: Sieben Agenten in einer Nachricht starten**

Agent-Tool, `subagent_type: general-purpose`, im Hintergrund, alle in derselben Nachricht.
Prompt = gemeinsamer Teil + Bereichsblock.

Gemeinsamer Teil (wörtlich, `<…>` ersetzen):

```
Du bist Reviewer <ID> (<Bereich>) im Review der Android-App „Days Counter“ vor der
Play-Store-Veröffentlichung. Repo: /Users/plook/Sites/android/days_counter.

Lies zuerst CLAUDE.md, docs/superpowers/specs/2026-09-08-days-counter-widget-design.md und
docs/superpowers/specs/2026-10-10-play-store-review-design.md (Abschnitt 4 = Befundformat).

Regeln:
- Nur lesen. Nichts ändern, nichts bauen, kein gradlew, kein git commit, kein Netzwerk außer
  WebSearch/WebFetch für öffentliche Doku, Policies und Versionen.
- Spec-konformes Verhalten ist kein Befund. Ein Verstoß gegen CLAUDE.md ist mindestens „Mittel“.
- Jeder Befund braucht eine echte Fundstelle (Pfad:Zeile), ein konkretes Szenario und einen
  Beleg (Codeauszug). Keine Vermutung ohne Szenario.
- Status jedes Befunds: „plausibel“.
- Findest du etwas, das sofortiges Eingreifen erfordert (Secret im Repo oder in der
  Git-Historie), beginne deine Antwort mit „SOFORT:“.
- Schreibe alle Befunde nach <REVIEW>/findings/<ID>.md, IDs <ID>-01, <ID>-02, … Ans Ende eine
  Liste „Geprüft ohne Befund“ (Prüfpunkte, die du geprüft hast und die in Ordnung sind).
- Antworte zum Schluss nur mit der Anzahl der Befunde je Stufe.

Dein Bereich:
<Bereichsblock>
```

Bereichsblöcke:

```
R1 Kotlin-Code. Dateien: app/src/main/java/**.
Prüfe: Datumsrechnung (DaysCalculator, Clock, SystemClock) bei Mitternacht, Sommerzeitwechsel,
Zeitzonenwechsel, Zieldatum weit in Zukunft/Vergangenheit; Coroutines, Scopes und Lebenszyklus
(ConfigViewModel, DaysCounterWidget, Receiver mit goAsync); Einhaltung der Glance-Regeln und der
Abhängigkeitsrichtung der Packages aus CLAUDE.md (Imports prüfen); Fehlerbehandlung bei
korruptem/leerem DataStore, unbekannter oder gelöschter appWidgetId, onDeleted-Aufräumen;
AlarmManager-Nutzung (inexakter Alarm, Neuplanung); Benennung, toter Code, Duplikate.
```

```
R2 Sicherheit & Datenschutz. Dateien: app/src/main/AndroidManifest.xml,
app/src/main/java/**/widget/*Receiver.kt, app/src/main/java/**/config/ConfigActivity.kt,
AlarmManagerMidnightUpdateScheduler.kt, PRIVACY.md, docs/play-store-setup.md, .gitignore.
Prüfe: exportierte Komponenten und was ein fremder Aufrufer damit erreicht (ConfigActivity mit
beliebiger appWidgetId, Broadcasts an DaysCounterWidgetReceiver); PendingIntent-Flags und
explizite Intents; allowBackup/dataExtractionRules/fullBackupContent; Berechtigungen;
Übereinstimmung von PRIVACY.md und der Datensicherheitsangabe „keine Daten erhoben“ mit dem
Code; Secrets oder Keystores in der Git-Historie (git log -p --all -S 'password' / '.jks' /
'BEGIN PRIVATE' u. ä., nur lesend). Prüfe nach OWASP MASVS-PLATFORM, MASVS-STORAGE und
MASVS-CODE über den gesamten Code, nicht nur über Änderungen.
```

```
R3 Ressourcen & UX. Dateien: app/src/main/res/**, app/src/main/java/**/config/*.kt (nur für
Texte, contentDescription, Semantik), app/src/main/java/**/widget/DaysCounterWidgetContent.kt.
Prüfe: jede String-Ressource in values und values-de vorhanden, Übersetzungsqualität und
Tonalität, Pluralformen (plurals statt Verkettung), Zahlen-/Datumsformat je Locale,
locales_config passend zu den values-Ordnern; adaptives Icon inkl. monochrome-Ebene
(mipmap-anydpi), Safe Zone des Vordergrunds; Farben in values/values-night, Kontrast der 12
Header-Farben gegen Text (WCAG 4.5:1 bzw. 3:1 für große Ziffern, rechne nach);
contentDescription/Semantik im Widget und in der Konfiguration; Verhalten bei großer Schrift
(sp vs. dp im 1x1-Widget); RTL; widget_info (Größen, previewLayout, description).
```

```
R4 Build, R8, Abhängigkeiten. Dateien: build.gradle.kts, settings.gradle.kts,
app/build.gradle.kts, gradle/libs.versions.toml, gradle.properties,
gradle/gradle-daemon-jvm.properties, gradle/wrapper/gradle-wrapper.properties, app/lint.xml,
config/detekt/detekt.yml.
Prüfe: jede Abhängigkeit gegen die neueste stabile Version und bekannte Schwachstellen
(WebSearch: OSV/GitHub Advisory, nur Name+Version senden); Glance/WorkManager-Constraint noch
nötig und korrekt; R8: fehlende Keep-Regeln für reflektiv genutzte Klassen (DataStore,
Glance-Actions, Receiver); Ausnahmen in lint.xml und detekt.yml begründet und nicht aufgeweicht
(CLAUDE.md); distributionSha256Sum im Wrapper gesetzt; versionCode-Schema; Signierkonfiguration
(get() auf fehlender Umgebungsvariable); Java-Toolchain; ob gradle-daemon-jvm.properties
committet werden sollte.
```

```
R5 CI & Release-Pipeline. Dateien: .github/**, .releaserc.json, .commitlintrc.json,
scripts/smoke-test-minified.sh, docs/play-store-setup.md (nur CI-Teile).
Prüfe: Actions per Commit-SHA statt Tag festgelegt (Drittanbieter wie r0adkll,
reactivecircus, wagoid besonders); permissions je Job minimal; Secrets: Keystore-Dekodierung,
Schutz vor Log-Ausgabe, Konfigurations-Cache, Aufräumen; Environment play-store und
Branch-Policy; Workload-Identity-Bedingungen (in der Doku: attribute-condition auf Repo und
Branch?); Trigger von play-upload per workflow_call/workflow_dispatch und wer dispatch auslösen
darf; Rennbedingungen bei parallelen Pushes auf main; semantic-release-Konfiguration
(Branches, Tag-Format, Assets); Dependabot deckt gradle und github-actions ab; Shell-Robustheit
des Smoke-Tests; npx mit festen Versionen.
```

```
R6 Tests. Dateien: app/src/test/**, app/src/androidTest/**, app/src/sharedTest/**, dazu die
getesteten Klassen in app/src/main/java/** zum Abgleich.
Prüfe: welche Risikopfade aus R1 ungetestet sind (Mitternacht, Sommerzeit, Zeitzone,
gelöschtes Widget, korrupter DataStore, ungültige appWidgetId); Testnamen nach CLAUDE.md
(Ausgangslage + Ergebnis); Kommentare in Tests nur zur Testlogik; Abhängigkeit von echter Zeit
oder Locale (PinnedLocaleRule, ControlledClock genutzt?); Robolectric-Tests mit
@Config(sdk = [35]); doppelte Fakes; Tests, die nichts prüfen (fehlende Assertions).
```

```
R7 Doku & Play-Metadaten. Dateien: README.md, CLAUDE.md, PRIVACY.md, LICENSE,
docs/play-store-setup.md, docs/superpowers/**, distribution/**.
Prüfe: Aussagen in README/CLAUDE.md, die nicht mehr zum Code passen (Befehle, Pfade,
Klassennamen per Grep verifizieren); Widersprüche zwischen den Dokumenten; „Neuerungen“ unter
500 Zeichen und in beiden Sprachen inhaltlich gleich; Play-Policy-Anforderungen (Zielgruppe,
Inhaltseinstufung, Datensicherheit, Kontakt-E-Mail, Datenschutzerklärung erreichbar und mit
Paketnamen/App-Namen); Lizenzhinweise für Abhängigkeiten (Apache-2.0-NOTICE-Pflichten,
Open-Source-Lizenzen in der App angezeigt?); Rechtschreibung.
```

- [ ] **Step 2: Weiter mit Task 3**

Nicht auf die Agenten warten. Meldet ein Agent „SOFORT:“, den Nutzer sofort informieren.

---

### Task 3: Builds und Abhängigkeiten (5a)

**Files:**
- Create: `$REVIEW/logs/check.txt`, `$REVIEW/logs/build.txt`, `$REVIEW/logs/dependencies.txt`, `$REVIEW/findings/R8.md`

- [ ] **Step 1: check aus dem Wurzelverzeichnis**

```bash
cd /Users/plook/Sites/android/days_counter
./gradlew check --rerun-tasks > "$REVIEW/logs/check.txt" 2>&1; echo "exit=$?"
grep -E "warning|w: |BUILD" "$REVIEW/logs/check.txt" | head -50
```

Expected: `exit=0`, `BUILD SUCCESSFUL`. Jede Warnung oder jeder Fehler wird Befund R8-xx.
Lint-Bericht `app/build/reports/lint-results-debug.html` und `build/reports/detekt/` nach
`$REVIEW/logs/` kopieren.

- [ ] **Step 2: Release-Artefakte bauen**

```bash
./gradlew assembleStaging bundleRelease -PappVersion=1.0.3 > "$REVIEW/logs/build.txt" 2>&1; echo "exit=$?"
ls -l app/build/outputs/apk/staging/ app/build/outputs/bundle/release/ app/build/outputs/mapping/release/
```

Expected: `app-staging.apk`, `app-release.aab` (unsigniert), `mapping.txt`.

- [ ] **Step 3: Abhängigkeitsbaum sichern**

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > "$REVIEW/logs/dependencies.txt" 2>&1
grep -E -- "-> |\(\*\)" "$REVIEW/logs/dependencies.txt" | grep -- "->" | sort -u | head -60
```

Die aufgelösten Versionen (insbesondere transitive wie WorkManager, Room, Protobuf, OkIO)
gehen an Task 8 als Eingabe für R4-Befunde. Für transitive Bibliotheken mit Sicherheitsrelevanz
(Protobuf, Room/SQLite) per WebSearch nach Advisories zur aufgelösten Version suchen.

---

### Task 4: APK- und Bundle-Analyse (5b)

**Files:**
- Create: `$REVIEW/apk/*`, Befunde in `$REVIEW/findings/R8.md`

- [ ] **Step 1: Merged Manifest auslesen**

```bash
BT=~/Library/Android/sdk/build-tools/37.0.0
APK=app/build/outputs/apk/staging/app-staging.apk
$BT/aapt2 dump xmltree --file AndroidManifest.xml "$APK" > "$REVIEW/apk/manifest.txt"
$BT/aapt2 dump badging "$APK" > "$REVIEW/apk/badging.txt"
grep -E "uses-permission|exported|debuggable|allowBackup|usesCleartextTraffic|dataExtractionRules|sdkVersion|targetSdkVersion" "$REVIEW/apk/manifest.txt" "$REVIEW/apk/badging.txt"
```

Prüfen: jede Berechtigung und jede exportierte Komponente (auch aus Bibliotheken, etwa
WorkManager-`SystemJobService`, Profile-Installer-Receiver) gegen `PRIVACY.md` und die
Datensicherheitsangaben; `debuggable` fehlt; `minSdkVersion 33`, `targetSdkVersion 37`.

- [ ] **Step 2: Inhalt und Größe**

```bash
unzip -l "$APK" | sort -k1 -n -r | head -30 > "$REVIEW/apk/content.txt"
unzip -l "$APK" | grep -vE "res/|classes|resources.arsc|AndroidManifest|META-INF/(MANIFEST|CERT|com/android)" >> "$REVIEW/apk/content.txt"
ls -l "$APK" app/build/outputs/bundle/release/app-release.aab >> "$REVIEW/apk/content.txt"
cat "$REVIEW/apk/content.txt"
```

Prüfen: verirrte Dateien (`*.kotlin_module`, `DebugProbesKt.bin`, `*.properties` aus
Bibliotheken, Testressourcen), ungewöhnlich große Einträge.

- [ ] **Step 3: R8-Mapping prüfen**

```bash
MAP=app/build/outputs/mapping/release
grep -E "^com\.pimorazelvanto" $MAP/mapping.txt | grep -v " -> [a-zA-Z0-9]\{1,3\}:" > "$REVIEW/apk/kept-classes.txt"
wc -l $MAP/mapping.txt $MAP/seeds.txt $MAP/usage.txt
cat "$REVIEW/apk/kept-classes.txt"
```

Erwartet unverschleiert: Application, Receiver, Activity (Manifest-Einstiegspunkte).
Unerwartet erhaltene App-Klassen sind ein Niedrig-Befund, fehlende Einstiegspunkte ein Blocker
(dann hätte der Smoke-Test in Task 5 ohnehin versagt).

- [ ] **Step 4: Signatur des Staging-APK und Bundle-Konfiguration**

```bash
$BT/apksigner verify --print-certs "$APK" | head -5
unzip -p app/build/outputs/bundle/release/app-release.aab BundleConfig.pb | wc -c
unzip -l app/build/outputs/bundle/release/app-release.aab | grep -E "base/manifest|BUNDLE-METADATA" 
```

Expected: Staging mit Debug-Zertifikat signiert (gewollt), Bundle unsigniert (kein Keystore),
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` vorhanden.

---

### Task 5: Emulator, connectedCheck und Smoke-Test

**Files:**
- Create: `$REVIEW/logs/connected.txt`

- [ ] **Step 1: Emulator starten**

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a -no-snapshot-save -no-boot-anim > "$REVIEW/logs/emulator.txt" 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb root && adb wait-for-device
```

(Mit dem Bash-Tool `run_in_background` starten statt `&`; auf den Boot per Monitor-Schleife warten.)

- [ ] **Step 2: Ausgangszustand sichern und Animationen abschalten**

```bash
for k in window_animation_scale transition_animation_scale animator_duration_scale auto_time auto_time_zone; do
  echo "global $k=$(adb shell settings get global $k)"; done > "$REVIEW/00-emulator-state.txt"
for k in font_scale; do echo "system $k=$(adb shell settings get system $k)"; done >> "$REVIEW/00-emulator-state.txt"
{ echo "tz=$(adb shell getprop persist.sys.timezone)"; echo "locale=$(adb shell getprop persist.sys.locale)"; echo "density=$(adb shell wm density)"; echo "night=$(adb shell cmd uimode night)"; } >> "$REVIEW/00-emulator-state.txt"
cat "$REVIEW/00-emulator-state.txt"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

- [ ] **Step 3: connectedCheck und Testzahl**

```bash
./gradlew connectedCheck > "$REVIEW/logs/connected.txt" 2>&1; echo "exit=$?"
grep -rhoE 'tests="[0-9]+" failures="[0-9]+" errors="[0-9]+" skipped="[0-9]+"' app/build/outputs/androidTest-results/connected/
```

Expected: `exit=0`, `tests` > 0, `failures="0" errors="0"`. Fehlschläge zweimal wiederholen;
nur reproduzierbare werden Befund, sporadische werden Befund „Flakiness“ (Mittel).

- [ ] **Step 4: Smoke-Test des minifizierten Builds**

```bash
scripts/smoke-test-minified.sh 2>&1 | tee "$REVIEW/logs/smoke.txt"
```

Expected: `Minified app starts and renders its configuration screen`. Danach bleibt der
Staging-Build für Task 6 installiert (das Debug-APK aus connectedCheck ist ersetzt; mit
`adb shell pm list packages -f com.pimorazelvanto` prüfen, dass nur die App installiert ist,
Test-APK ggf. deinstallieren: `adb uninstall com.pimorazelvanto.dayscounter.test`).

---

### Task 6: Laufzeitszenarien

**Files:**
- Create: `$REVIEW/screens/s<NN>-<name>.png`, `$REVIEW/logs/s<NN>.txt`, Befunde in `$REVIEW/findings/R8.md`

Hilfsfunktionen für jeden Bash-Aufruf:

```bash
PKG=com.pimorazelvanto.dayscounter
shot() { adb exec-out screencap -p > "$REVIEW/screens/$1.png"; }
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null && adb shell cat /sdcard/ui.xml; }
logs() { adb logcat -d -v time | grep -iE "$PKG|AndroidRuntime|Glance|AppWidget" > "$REVIEW/logs/$1.txt"; adb logcat -c; }
```

Screenshots per Read-Tool ansehen, nicht nur ablegen. Nach jedem Szenario
`adb logcat -d -b crash | grep $PKG` prüfen; jeder Absturz ist ein Blocker.

- [ ] **Step 1: S01 Platzieren, konfigurieren, abbrechen**

Home (`adb shell input keyevent HOME`), lange auf freie Fläche drücken
(`adb shell input swipe X Y X Y 1500`), „Widgets“ antippen, „Days Counter“ suchen, Widget per
`adb shell input draganddrop X1 Y1 X2 Y2 1500` auf den Home-Screen ziehen. Koordinaten jeweils
aus `ui` ermitteln. In der Konfiguration Titel „Urlaub“, Datum +10 Tage, Farbe wählen,
speichern → `shot s01-placed`. Zweites Widget platzieren und Konfiguration mit Zurück
abbrechen → `shot s01-cancelled`; erwartet: kein unkonfiguriertes Widget bleibt sichtbar.
`adb shell dumpsys appwidget | grep -A3 $PKG` → `$REVIEW/logs/s01-appwidget.txt`.

- [ ] **Step 2: S02 Zieldaten und Ziffernstufen**

Per Tipp aufs Widget die Konfiguration öffnen und nacheinander setzen: heute, +1, −1,
+999, +10000, −10000 Tage (bzw. was der DatePicker zulässt). Nach jedem Speichern
`shot s02-<fall>`. Erwartet laut Spec: vorzeichenlose Zahl, „0“ am Zieltag, Ziffern passen in
das Blatt (DigitSizeTier), Titel nicht abgeschnitten bei 20 Zeichen.

- [ ] **Step 3: S03 Mitternachtswechsel**

```bash
adb shell settings put global auto_time 0
adb shell date $(date -v+0d +%m%d)2359$(date +%Y).30   # Format MMDDhhmmYYYY.ss
adb shell input keyevent HOME; shot s03-before
```

Bis zu zehn Minuten warten (Monitor-Schleife, alle 60 s `shot s03-t<min>`), dann
`adb shell dumpsys alarm | grep -A5 $PKG > "$REVIEW/logs/s03-alarm.txt"`. Erwartet: Zahl
ändert sich spätestens zehn Minuten nach 00:00. Falls `adb shell date` den Alarm-Manager nicht
informiert, stattdessen `adb shell cmd alarm set-time <epoch-millis>` verwenden und dies im
Bericht vermerken.

- [ ] **Step 4: S04 Zeitzone, Uhr stellen, Neustart, App-Update**

```bash
adb shell settings put global auto_time_zone 0
adb shell cmd alarm set-timezone Pacific/Kiritimati; sleep 5; shot s04-tz-kiritimati
adb shell cmd alarm set-timezone Pacific/Pago_Pago; sleep 5; shot s04-tz-pagopago
adb shell cmd alarm set-time $(( ( $(date +%s) + 3*86400 ) * 1000 )); sleep 5; shot s04-time-plus3
adb reboot; adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done; adb root; adb wait-for-device; sleep 20; shot s04-after-reboot
adb install -r app/build/outputs/apk/staging/app-staging.apk; sleep 10; shot s04-after-update
logs s04
```

Erwartet: Nach jeder Aktion zeigt das Widget den zum neuen lokalen Datum passenden Wert
(Kiritimati UTC+14 und Pago Pago UTC−11 liegen an verschiedenen Kalendertagen).

- [ ] **Step 5: S05 Hell-/Dunkelmodus**

```bash
adb shell cmd uimode night yes; sleep 5; shot s05-dark
adb shell cmd uimode night no; sleep 5; shot s05-light
```

Erwartet: Blatt folgt dem Systemthema, Header behält die gewählte Farbe, Text lesbar.

- [ ] **Step 6: S06 Sprachen**

```bash
adb shell cmd locale set-app-locales $PKG --locales de-DE; sleep 3; shot s06-app-de
adb shell am start -n $PKG/.config.ConfigActivity --ei appWidgetId <ID aus s01>; sleep 2; shot s06-config-de
adb shell cmd locale set-app-locales $PKG --locales en-US; sleep 3; shot s06-app-en
adb shell cmd locale set-app-locales $PKG --locales ""
```

Zusätzlich systemweit: `adb shell setprop persist.sys.locale de-DE; adb shell stop; adb shell start`,
Boot abwarten, `shot s06-system-de` (Widget-Picker-Beschreibung und Widget), danach auf den
Wert aus `00-emulator-state.txt` zurücksetzen. Die `appWidgetId` aus
`$REVIEW/logs/s01-appwidget.txt` verwenden.

- [ ] **Step 7: S07 Schrift, Anzeigegröße, Barrierefreiheit**

```bash
adb shell settings put system font_scale 2.0; sleep 3; shot s07-font200
adb shell wm density 560; sleep 3; shot s07-density-max
adb shell wm density reset; adb shell settings put system font_scale 1.0
adb shell input keyevent HOME; ui > "$REVIEW/logs/s07-launcher-ui.xml"
grep -o 'content-desc="[^"]*"' "$REVIEW/logs/s07-launcher-ui.xml" | head
```

Ist TalkBack nicht installiert (`adb shell pm list packages | grep talkback`), die Ansage über
`content-desc`/`text` im UI-Dump beurteilen und TalkBack als „nicht geprüft“ vermerken. Auch die
Konfiguration bei `font_scale 2.0` öffnen und `shot s07-config-font200`.

- [ ] **Step 8: S08 Mehrere Widgets, Entfernen**

Drittes Widget mit anderem Titel, Datum und Farbe platzieren → `shot s08-three`. Den
DataStore vor und nach dem Entfernen eines Widgets (lange drücken → „Entfernen“) sichern:

```bash
adb shell ls -R /data/data/$PKG/files/ > "$REVIEW/logs/s08-files-before.txt"
adb shell cat /data/data/$PKG/files/datastore/*.preferences_pb | strings > "$REVIEW/logs/s08-store-before.txt"
# Widget entfernen
adb shell cat /data/data/$PKG/files/datastore/*.preferences_pb | strings > "$REVIEW/logs/s08-store-after.txt"
diff "$REVIEW/logs/s08-store-before.txt" "$REVIEW/logs/s08-store-after.txt"
```

Erwartet: Einträge des entfernten Widgets sind weg, die anderen unverändert.

- [ ] **Step 9: S09 Prozess beenden**

```bash
adb shell input keyevent HOME
adb shell am kill $PKG; sleep 2; adb shell pidof $PKG || echo "killed"
```

Widget antippen → `shot s09-config-after-kill`. Erwartet: Konfiguration öffnet mit den
gespeicherten Werten. **Nicht** `am force-stop` verwenden (löscht Alarme).

- [ ] **Step 10: S10 Missbrauch exportierter Komponenten**

```bash
adb shell am start -W -n $PKG/.config.ConfigActivity; sleep 2; shot s10-no-id
adb shell am start -W -n $PKG/.config.ConfigActivity --ei appWidgetId 999999; sleep 2; shot s10-foreign-id
adb shell dumpsys appwidget | grep -c $PKG
adb shell am broadcast -n $PKG/.widget.DaysCounterWidgetReceiver -a android.appwidget.action.APPWIDGET_UPDATE --eia appWidgetIds 999999
adb shell am broadcast -n $PKG/.widget.DaysCounterWidgetReceiver -a android.appwidget.action.APPWIDGET_DELETED --ei appWidgetId <ID aus s01>
sleep 3; shot s10-after-broadcasts; logs s10
adb shell cat /data/data/$PKG/files/datastore/*.preferences_pb | strings > "$REVIEW/logs/s10-store.txt"
```

Bewerten: Bei fremder ID wird nach „Speichern“ ein DataStore-Eintrag für ein nicht existierendes
Widget angelegt? Kann ein fremder Broadcast die Konfiguration eines echten Widgets löschen?
(Auf API 34 sind `APPWIDGET_*`-Aktionen nicht geschützt; dokumentieren, was tatsächlich
passiert.) Absturz = Blocker, Datenlöschung durch Dritte = Hoch.

---

### Task 7: Emulator zurücksetzen

Läuft auch, wenn Task 6 abgebrochen wurde.

- [ ] **Step 1: Ausgangswerte wiederherstellen**

Werte aus `$REVIEW/00-emulator-state.txt` einsetzen (`null` → `settings delete`):

```bash
adb shell settings put global auto_time <wert>
adb shell settings put global auto_time_zone <wert>
adb shell cmd alarm set-timezone <tz>
adb shell settings put system font_scale <wert>
adb shell wm density reset
adb shell cmd uimode night <wert>
adb shell cmd locale set-app-locales com.pimorazelvanto.dayscounter --locales ""
adb shell setprop persist.sys.locale <wert>
adb shell settings put global window_animation_scale <wert>
adb shell settings put global transition_animation_scale <wert>
adb shell settings put global animator_duration_scale <wert>
```

- [ ] **Step 2: Emulator beenden ohne Snapshot**

```bash
adb emu kill
```

Der Start mit `-no-snapshot-save` stellt sicher, dass der AVD-Snapshot unverändert bleibt.

---

### Task 8: Verifikation

**Interfaces:**
- Consumes: `$REVIEW/findings/R1.md` … `R8.md`, `$REVIEW/logs/*`
- Produces: `$REVIEW/findings/V1.md`, `$REVIEW/findings/V2.md` mit Urteil je Befund-ID

- [ ] **Step 1: Auf alle Reviewer warten**

Erst fortfahren, wenn R1–R7 gemeldet haben und Task 3–6 abgeschlossen sind.

- [ ] **Step 2: Zwei Verifizierer in einer Nachricht starten**

V1: Befunde aus R1, R2, R6 und die Code-/Sicherheitsbefunde aus R8.
V2: Befunde aus R3, R4, R5, R7 und die Build-/APK-Befunde aus R8.

Prompt (wörtlich, `<…>` ersetzen):

```
Du verifizierst Befunde aus dem Review der Android-App „Days Counter“.
Repo: /Users/plook/Sites/android/days_counter. Nur lesen, nichts ändern, nichts bauen.
Maßstab: CLAUDE.md, docs/superpowers/specs/2026-09-08-days-counter-widget-design.md,
docs/superpowers/specs/2026-10-10-play-store-review-design.md (Abschnitt 4).

Befunddateien: <Liste der Pfade>. Logs und Screenshots: <REVIEW>/logs, <REVIEW>/screens.

Versuche, jeden Befund zu widerlegen: Stimmt die Fundstelle? Tritt das Szenario wirklich ein
(Aufrufer, Guards, Framework-Verhalten auf API 33–37 prüfen)? Ist das Verhalten laut Spec
gewollt? Ist die Stufe angemessen?

Urteil je Befund-ID: „bestätigt“ (Szenario am Code oder Beleg nachvollzogen), „plausibel“
(nicht widerlegt, nicht nachgewiesen), „verworfen“ (mit einem Satz Begründung). Optional:
„Stufe ändern auf <Stufe>, weil …“; „Duplikat von <ID>“.
Schreibe das Ergebnis nach <REVIEW>/findings/<V1|V2>.md als Tabelle
| ID | Urteil | Stufe neu | Begründung |. Antworte nur mit der Anzahl je Urteil.
```

- [ ] **Step 3: Plausible Laufzeitbefunde nachstellen**

Ist ein Befund „plausibel“ und auf dem Emulator reproduzierbar (etwa Verhalten bei fremder
`appWidgetId`), Emulator erneut starten (Task 5 Step 1–2), nachstellen, Beleg ablegen, Urteil
auf „bestätigt“ oder „verworfen“ setzen, danach Task 7 erneut ausführen.

---

### Task 9: Bericht konsolidieren

**Files:**
- Create: `$REVIEW/2026-10-10-play-store-review.md`

- [ ] **Step 1: Befunde zusammenführen**

Verworfene Befunde entfernen und zählen. Duplikate unter der ID mit der aussagekräftigsten
Fundstelle zusammenführen, die anderen IDs als „siehe auch“ nennen. Endgültige Stufe = Stufe
nach Verifikation; bei Widerspruch die höhere, mit Begründung.

- [ ] **Step 2: Bericht schreiben**

Gliederung exakt nach Spec Abschnitt 4:

```markdown
# Review vor der Play-Store-Veröffentlichung

Stand: <Commit aus 00-baseline.txt> · Datum: 2026-10-10

## 1. Zusammenfassung
Ampel: <Rot: ≥1 Blocker | Gelb: ≥1 Hoch | Grün> – bereit für die Produktion: <ja/nein>
| Blocker | Hoch | Mittel | Niedrig |
|---|---|---|---|
| n | n | n | n |
<3–5 Sätze: wichtigste Risiken>

## 2. Blocker und Hoch
<Befunde im Format aus Spec Abschnitt 4>

## 3. Mittel und Niedrig
### R1 Kotlin-Code
…
### R8 Laufzeit
…

## 4. Abdeckung
- Geprüft: <Bereiche, Szenarien S01–S10 mit Ergebnis>
- Nicht geprüft: API 33, API 36+, Launcher anderer Hersteller, Play-Console-Einstellungen
  außerhalb des Repos, <TalkBack falls nicht installiert>, <weitere>
- Werkzeuge: <check, assembleStaging, bundleRelease, aapt2, apksigner, connectedCheck (n Tests),
  Smoke-Test, WebSearch>
- Verworfene Befunde: <n>

## 5. Anhang
<Pfade zu Logs und Screenshots>
```

- [ ] **Step 3: Repo unverändert prüfen**

```bash
cd /Users/plook/Sites/android/days_counter && git status --short
```

Expected: nur `?? gradle/gradle-daemon-jvm.properties` wie in `00-baseline.txt`.

- [ ] **Step 4: Im Chat zusammenfassen**

Ampel, Anzahl je Stufe, alle Blocker und Hoch mit einem Satz und Fundstelle, Pfad zum Bericht.
Fragen, welche Befunde behoben werden sollen. Nichts beheben.
