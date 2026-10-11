# Behebung der Review-Befunde, Pakete 0–3 – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Befunde der Stufen Hoch und Mittel aus dem Review vor der Play-Store-Veröffentlichung beheben, aufgeteilt in vier unabhängige PRs.

**Architecture:** Jedes Paket ist ein eigener Branch von `main` mit eigenem PR. Fehlerbehebungen folgen TDD: zuerst der Test aus dem Testvorschlag des Befunds, rot, dann der Fix. Gemergt wird erst nach Freigabe durch den Nutzer, weil jeder `fix:`-Commit auf `main` ein Release mit Play-Upload auslöst.

**Tech Stack:** Kotlin, Jetpack Glance 1.2, Compose, DataStore, Robolectric (`@Config(sdk = [35])`), Compose UI Test, GitHub Actions, semantic-release.

**Spec:** Befundbericht `.superpowers/sdd/2026-10-10-play-store-review/review/2026-10-10-play-store-review.md` (lokal, nicht versioniert; die Befund-IDs unten verweisen darauf) und `docs/superpowers/specs/2026-09-08-days-counter-widget-design.md`.

## Global Constraints

- `./gradlew check` aus dem Wurzelverzeichnis vor jedem Commit fehlerfrei; Warnungen sind Fehler; keine Baselines, keine aufgeweichten Schwellwerte.
- Conventional Commits, englisch, Imperativ; Typ nach Wirkung für Nutzer. Keine Claude-Attribution.
- Keine Merge-Commits; Branches vor dem Merge auf `main` rebasen.
- Robolectric-Tests mit `@Config(sdk = [35])`; Zeit nur über `Clock`; Test-Fakes in `app/src/sharedTest`.
- Abhängigkeitsrichtung der Packages aus `CLAUDE.md`.
- Keine Änderungen an GitHub- oder GCP-Einstellungen; dafür nur Befehle in `docs/play-store-setup.md`.
- Kein Merge, kein Release ohne Freigabe des Nutzers.

## Review Focus

1. Schriftgrößen in `dp` dürfen den Text bei normaler Schrift nicht sichtbar verändern: gleiche Zahlenwerte wie bisher in `sp`.
2. Das Rendern über `AndroidRemoteViews` muss den Dunkelmodus weiter im Launcher-Prozess auflösen (`setColorInt` mit Tag/Nacht), nicht per Ressource im App-Prozess.
3. Ein Scroll-Container darf den Datumsfeld-Tipp (Press-Interaction) nicht verschlucken.
4. Ohne Widgets darf kein Empfänger mehr einen Alarm planen, mit Widgets muss er es weiterhin tun.
5. SHA-Pins müssen auf die Commits zeigen, die die bisherigen Tags meinen (annotierte Tags dereferenzieren).

---

### Task 0: Aufräumen und PR für Review-Tests (Paket 0)

- [ ] `ci/play-tracks` auf `origin/ci/play-tracks` zurücksetzen (die zwei versehentlich dort gelandeten Commits liegen auf `test/play-store-review`).
- [ ] Diesen Plan auf `test/play-store-review` committen (`docs:`).
- [ ] Branch pushen, PR „test: pin clock and alarm edge cases found in the Play Store review“ öffnen.

### Task 1: Konfiguration bei Querformat und großer Schrift (Paket 1, Branch `fix/config-and-font-scale`)

**R8-03 zuerst**, damit die neuen Compose-Tests auf API 36 laufen:
- [ ] `espresso-core` 3.7.0 in `libs.versions.toml` und als `androidTestImplementation`; Commit `build: run instrumented tests on Android 16 with Espresso 3.7`.

**R3-01 – Konfiguration scrollt:**
- [ ] Instrumentierter Compose-Test in `ConfigScreenTest` (neu, androidTest): `ConfigScreen` in einer Box von 400×240 dp; `onNodeWithTag(SAVE_BUTTON).performScrollTo().assertIsDisplayed()`. Rot, weil kein Scroll-Container existiert.
- [ ] `ConfigScreen`: Spalte mit `verticalScroll(rememberScrollState())` und `imePadding()`. Grün; `ConfigActivityTest` weiter grün.
- [ ] Commit `fix: let the setup screen scroll so Save stays reachable in landscape`.

**R3-02 – Schrift wächst nicht über das Widget hinaus:**
- [ ] `TextSizes` liefert `Dp` statt `TextUnit` (gleiche Zahlenwerte). Spec-Tabelle „Schriftgrößen-Stufe“ auf dp umstellen und den Grund nennen.
- [ ] Neuer Robolectric-Test `WidgetTextViewsTest`: Die RemoteViews für Titel und Zahl, angewendet bei `fontScale = 2`, haben `textSize == dp × density`. Rot (Funktion fehlt).
- [ ] `widget/WidgetTextViews.kt`: baut `RemoteViews` aus `res/layout/widget_text.xml` (einzeiliges `TextView`), setzt Text, `COMPLEX_UNIT_DIP`-Größe, Farbe per `setColorInt(…, "setTextColor", day, night)`, Schriftstärke über zwei Layouts (`widget_title_text.xml`, `widget_value_text.xml`). `DaysCounterWidgetContent` nutzt `AndroidRemoteViews`.
- [ ] Glance-Unit-Tests, die Text über `assertHasTextEqualTo` prüfen, auf das Ergebnis der neuen Funktion umstellen; `DaysCounterWidgetRenderTest` (inflatet echte RemoteViews) bleibt unverändert grün.
- [ ] `WidgetPreview` (Compose) rechnet `Dp` über `LocalDensity` in `sp` um, sodass die Vorschau ebenfalls nicht mitwächst; `dimens.xml` für die XML-Vorschau auf `dp`.
- [ ] Commit `fix: keep the widget readable when the system font is enlarged`.

**R3-03 – Titel nach Zeichen statt UTF-16-Einheiten:**
- [ ] `ConfigViewModelTest`: 19 Zeichen + „🌴“ bleibt vollständig; ZWJ-Familie zählt als 1. Rot.
- [ ] `domain/TitleLength.kt` (`graphemeCount`, `takeGraphemes` über `java.text.BreakIterator`), genutzt in `ConfigViewModel.onTitleChanged` und für den Zähler.
- [ ] Commit `fix: count and cut widget titles by visible characters`.

### Task 2: Robustheit des Widgets (Paket 2, Branch `fix/widget-robustness`)

**R1-02 – kein Alarm ohne Widget:**
- [ ] `WidgetUpdater` erhält `suspend fun hasWidgets(): Boolean`; `GlanceWidgetUpdater` über `GlanceAppWidgetManager.getGlanceIds`; `FakeWidgetUpdater.widgetCount`.
- [ ] `DateChangeReceiverTest`: ohne Widgets plant keine Aktion einen Alarm und meldet ihn ab. Rot.
- [ ] `DateChangeReceiver`: `if (hasWidgets) schedule() else cancel()`.
- [ ] Commit `fix: stop waking the device at midnight once the last widget is gone`.

**R6-06 – Hintergrundfehler beenden den Prozess nicht:**
- [ ] `DefaultAppContainerTest` (neu, Robolectric): eine werfende Coroutine im `backgroundScope` erreicht den Uncaught-Exception-Handler des Threads nicht. Rot.
- [ ] `DefaultAppContainer.backgroundScope` mit `CoroutineExceptionHandler`, der protokolliert.
- [ ] Commit `fix: survive storage errors while widgets update or are removed`.

**R1-01 – Löschen im Glance-Lebenszyklus:**
- [ ] Test: `DaysCounterWidget.onDelete` löscht die Konfiguration des Widgets. Rot.
- [ ] Löschen aus `DaysCounterWidgetReceiver.onDeleted` nach `DaysCounterWidget.onDelete` verschieben (kein zweites `goAsync`).
- [ ] Commit `fix: delete a removed widget's settings inside Glance's own broadcast`.

**R6-05 – korrupter Store:**
- [ ] Store-Konstruktion als `widgetConfigDataStore(produceFile)` in `data` herausziehen; Test mit Zufallsbytes: `load` → `null`, danach `save` gelingt.
- [ ] Commit `test: prove a corrupt settings file is replaced instead of crashing`.

### Task 3: CI-Härtung (Paket 3, Branch `ci/harden-pipeline`)

- [ ] R4-01: Wrapper mit `distributionSha256Sum` (offizielle Prüfsumme von services.gradle.org). Commit `build: verify the Gradle distribution checksum`.
- [ ] R5-01/R5-02: Alle Actions auf Commit-SHA mit Tag-Kommentar; R5-03: Dependabot auch für `.github/actions/setup-android-build`. Commit `ci: pin every action to a commit`.
- [ ] R5-05: Upload-Job prüft, dass `v<version>` Vorfahre von `origin/main` ist; Doku für ein Tag-Ruleset `v*`. Commit `ci: upload only tags that are on main`.
- [ ] R5-04: Doku der Workload-Identity-Bedingung um `ref` und `repository_id`. Commit `docs: bind the Play upload identity to main`.
- [ ] R5-06: semantic-release mit `package.json` + Lockfile unter `.github/release`, `npm ci`, Dependabot `npm`. Commit `ci: lock semantic-release and its plugins`.
- [ ] R5-08: Emulator-Matrix API 33 und 36 statt nur 34. Commit `ci: run instrumented tests on the oldest and newest Android`.
- [ ] R4-02: Smoke-Test speichert eine Konfiguration im minifizierten Build und prüft den Crash-Puffer. Commit `ci: save a configuration in the minified smoke test`.
- [ ] R2-04: Keystore-Muster in `.gitignore`. Commit `chore: keep keystores out of the repository`.

## Bewusst nicht in diesen Paketen

- R1-03 (fremde `appWidgetId`): Eine Prüfung gegen `AppWidgetManager` würde die instrumentierten Tests und den Smoke-Test aussperren, die mit erfundenen IDs arbeiten; Niedrig, ins Backlog.
- R4-04 (`gradle-daemon-jvm.properties`): lokal von `updateDaemonJvm` erzeugt; ob der Daemon auf JDK 25 festgelegt werden soll, entscheidet der Nutzer.
- R5-04/R5-05 in GitHub/GCP selbst: nur der Nutzer; die Befehle stehen danach in `docs/play-store-setup.md`.
- Paket 4 und 5 aus dem Bericht.
