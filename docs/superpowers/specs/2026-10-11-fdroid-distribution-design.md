# Verteilung über F-Droid und IzzyOnDroid – Design

Datum: 2026-10-11
Status: Entwurf abgestimmt, bereit für Implementierungsplan

## 1. Ziel

Days Counter soll zusätzlich zum Google Play Store im F-Droid-Hauptrepository und bei
IzzyOnDroid erscheinen. Außerhalb des Play Stores tragen alle APKs dieselbe, eigene Signatur,
damit Nutzer zwischen GitHub-Releases, IzzyOnDroid und F-Droid wechseln können. F-Droid soll
diese Signatur über reproduzierbare Builds übernehmen, statt mit eigenem Schlüssel zu signieren.

Erfolgskriterien:

- Jedes Release erzeugt ohne Handarbeit ein mit dem Verteilungsschlüssel signiertes APK am
  GitHub-Release.
- F-Droid erkennt neue Versionen automatisch, baut sie aus dem Tag nach und veröffentlicht das
  APK mit unserer Signatur.
- IzzyOnDroid übernimmt neue Versionen automatisch vom GitHub-Release.
- Der Play-Store-Ablauf (Upload-Schlüssel, Play App Signing, `PLAY_TRACKS`,
  `PLAY_RELEASE_STATUS`) bleibt unverändert.

## 2. Umfang

### Enthalten

- Version im Repo (`version.properties`), geschrieben vom Release-Bot
- Release-Commit per Deploy-Key mit einer einzigen Ausnahme im Ruleset
- Build-Anpassungen für den F-Droid-Scanner und reproduzierbare Builds
- Signatur mit wählbarem Schlüssel (Upload- oder Verteilungsschlüssel)
- Job `apk`: signiertes APK am GitHub-Release
- Store-Metadaten im Fastlane-Format als einzige Quelle für F-Droid und IzzyOnDroid
- „Neuerungen“ aus einer Quelle für Play, F-Droid und IzzyOnDroid
- Abbruch veralteter Prüf-Jobs auf `main`, ununterbrechbare Release-Jobs
- Rezeptentwurf für `fdroiddata`, Antragsvorlage für IzzyOnDroid, Einrichtungsanleitung

### Nicht enthalten

- Abgleich von Store-Texten und -Bildern mit der Play Console (bleibt Handarbeit)
- Gleiche Signatur wie im Play Store (unmöglich: Play App Signing signiert mit Googles
  Schlüssel)
- Automatisches Stellen der Aufnahmeanträge

## 3. Signaturen

| Kanal | Signatur |
|---|---|
| Google Play | Googles App-Signaturschlüssel (Play App Signing); wir laden mit dem Upload-Schlüssel hoch |
| GitHub-Releases, IzzyOnDroid, F-Droid | neuer Verteilungsschlüssel, nur für diese Kanäle |

Ein Wechsel zwischen Play und den übrigen Kanälen erfordert eine Neuinstallation; das wird in
README und Store-Beschreibung nicht verschwiegen. Der Upload-Schlüssel wird bewusst nicht
wiederverwendet: Für den Verteilungsschlüssel gibt es keinen Reset, ein Leck des Upload-Schlüssels
soll die F-Droid-Nutzer nicht treffen.

## 4. Version im Repo

`version.properties` im Wurzelverzeichnis ist die einzige Quelle für `versionName` und
`versionCode`:

```
versionName=1.0.3
versionCode=1000003
```

- Gradle liest beide Werte aus der Datei. Übergangsweise hat `-PappVersion` Vorrang, damit
  Releases bis zur Umstellung der Pipeline (Schritt 3 in Abschnitt 11) nicht mit der veralteten
  Datei bauen; mit Schritt 3 entfällt `-PappVersion` als Build-Eingang.
- Die Formel `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH` bleibt an einer Stelle: Der Gradle-Task
  `writeVersionProperties -PappVersion=X.Y.Z` schreibt die Datei und lehnt ungültige Versionen ab
  (nicht `X.Y.Z`, Minor oder Patch ≥ 1000).
- Lokale Builds tragen die zuletzt veröffentlichte Version statt `0.0.0-dev`.
- F-Droid liest die Werte per `UpdateCheckData` aus der Datei im getaggten Commit.

## 5. Release-Ablauf

Nach einem Merge auf `main`:

1. `check` und `connected` grün (Abschnitt 8 zur Nebenläufigkeit).
2. semantic-release berechnet die Version. `@semantic-release/exec` ruft im `prepare`-Schritt
   `writeVersionProperties` auf und kopiert je Sprache `changelogs/default.txt` nach
   `changelogs/<versionCode>.txt`.
3. `@semantic-release/git` committet `version.properties` und die neuen Changelog-Dateien als
   `chore(release): X.Y.Z [skip ci]` und pusht über den Deploy-Key auf `main`. Der Tag `vX.Y.Z`
   zeigt auf diesen Commit; danach entsteht das GitHub-Release.
4. Job `play` (unverändert, außer den Signaturvariablen und der Herkunft der „Neuerungen“).
5. Job `apk` (neu): Checkout von `vX.Y.Z`, `assembleRelease` mit dem Verteilungsschlüssel, APK als
   `days-counter-X.Y.Z.apk` an das GitHub-Release hängen.

`version.properties` und `changelogs/<versionCode>.txt` ändert ausschließlich der Bot; das steht
in `CLAUDE.md`, damit Rebase-Merges nie mit dem Release-Commit kollidieren.

Der Deploy-Key ist ein SSH-Schlüssel mit Schreibrecht nur für dieses Repo. `actions/checkout`
nutzt ihn im Job `release` über `ssh-key`. Das Ruleset für `main` erhält eine Ausnahme für den
Akteur-Typ „Deploy key“; alle übrigen Regeln (PR, Rebase, lineare Historie, Pflicht-Checks, kein
Force-Push) bleiben für alle anderen bestehen.

## 6. Build

### Signatur

Die Umgebungsvariablen heißen `SIGNING_KEYSTORE_FILE`, `SIGNING_KEYSTORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. `play` belegt sie mit den vorhandenen
`UPLOAD_*`-Secrets, `apk` mit den neuen `DISTRIBUTION_KEYSTORE_BASE64`,
`DISTRIBUTION_KEYSTORE_PASSWORD`, `DISTRIBUTION_KEY_ALIAS`, `DISTRIBUTION_KEY_PASSWORD` im
Environment `play-store`. Ohne Variablen bleibt `release` unsigniert; F-Droid baut so.

### F-Droid-Scanner und Reproduzierbarkeit

- `dependenciesInfo { includeInApk = false; includeInBundle = false }`: entfernt den mit Googles
  Schlüssel verschlüsselten Block „Dependency metadata“, den der F-Droid-Scanner ablehnt.
- ART-Profil-Tasks deaktiviert: `baseline.prof` ist laut F-Droid-Doku nicht zuverlässig
  reproduzierbar; der Startvorteil ist für ein Widget vernachlässigbar.
- VCS-Info bleibt eingeschaltet: Beide Seiten bauen aus dem getaggten Commit.
- Keine Zeitstempel oder maschinenabhängigen Werte im Build.
- CI baut mit Temurin 21, F-Droid mit dem JDK 21 aus Debian. Die Übereinstimmung wird lokal mit
  zwei unabhängigen Builds geprüft und endgültig durch die Verifikation von F-Droid und den
  IzzyOnDroid-Rebuildern bestätigt.

## 7. Store-Metadaten

```
fastlane/metadata/android/{en-US,de-DE}/
├── title.txt
├── short_description.txt
├── full_description.txt
├── changelogs/default.txt
├── changelogs/<versionCode>.txt   (nur vom Bot)
└── images/icon.png, featureGraphic.png, phoneScreenshots/1.png … 4.png
```

- Inhalte: die abgestimmten Play-Store-Texte (englisch mit „color“) und die erzeugten Grafiken.
- `changelogs/default.txt` ersetzt `distribution/whatsnew/`. `play-upload.yml` erzeugt daraus vor
  dem Upload das Verzeichnis `whatsnew-<locale>` für die Upload-Action.
- `CLAUDE.md`: Store-Texte nur unter `fastlane/` ändern.

## 8. Nebenläufigkeit auf `main`

- `check` und `connected` erhalten auf `main` eine Concurrency-Gruppe mit Abbruch: Ein neuer Merge
  bricht die Prüfung eines älteren Stands ab, der neuere Lauf prüft beide Änderungen zusammen.
- `release`, `play` und `apk` laufen in einer gemeinsamen Gruppe ohne Abbruch: Ein begonnenes
  Release läuft immer vollständig durch.
- Zweite Sicherung: semantic-release veröffentlicht nichts, wenn `main` auf GitHub weiter ist als
  der geprüfte Stand; der Push des Release-Commits scheitert bei einem zwischenzeitlichen Merge,
  bevor ein Tag entsteht.

## 9. F-Droid-Rezept und IzzyOnDroid

Entwurf `docs/fdroid/com.pimorazelvanto.dayscounter.yml` für einen Merge Request bei
`fdroiddata`:

- `License: MIT`, `Categories: [Time]`, Links auf Quellcode, Issues und Releases
- Build: `commit: v<version>`, `subdir: app`, `gradle: [yes]`, keine Signaturvariablen
- `Binaries: https://github.com/pimorazelvantoapps/days-counter/releases/download/v%v/days-counter-%v.apk`
- `AllowedAPKSigningKeys: <SHA-256 des Verteilungszertifikats>`
- `AutoUpdateMode: Version`, `UpdateCheckMode: Tags ^v[0-9.]+$`,
  `UpdateCheckData: version.properties|versionCode=(\d+)|.|versionName=(.+)`

IzzyOnDroid: Antrag als Issue im IzzyOnDroid-Repo auf Codeberg mit Link zum GitHub-Repo; Vorlage
in der Anleitung.

`docs/fdroid-setup.md` beschreibt die einmaligen Schritte: Verteilungsschlüssel in `~/.keys`
erzeugen und sichern, Secrets anlegen, Deploy-Key erzeugen und eintragen, Ruleset-Ausnahme,
Zertifikats-Fingerabdruck ins Rezept, Anträge stellen.

## 10. Tests und Verifikation

- Unit-Tests für `writeVersionProperties` (Formel, Datei-Inhalt, Fehlerfälle) und das Lesen von
  `version.properties`.
- Prüfskript, das die Signaturblöcke eines APKs ausliest und bei „Dependency metadata“
  (0x504b4453) fehlschlägt; läuft im Job `apk` und lokal.
- Reproduzierbarkeit lokal: Release aus zwei getrennten, sauberen Klonen bauen, unsignierte APKs
  byte-genau vergleichen; mit `apksigcopier` die Signatur des signierten APKs auf das unsignierte
  übertragen und verifizieren.
- semantic-release-Trockenlauf gegen ein Wegwerf-Remote: Release-Commit mit `version.properties`
  und Changelog, Tag auf diesem Commit.
- Erster echter Lauf: Release-Commit auf `main`, Tag zeigt darauf, APK am Release, Play-Upload
  weiterhin grün.

## 11. Umsetzung in Schritten

Jeder Schritt ist ein eigener PR:

1. Build: `version.properties`, `writeVersionProperties`, Signaturvariablen, `dependenciesInfo`,
   ART-Profile; `-PappVersion` hat noch Vorrang vor der Datei (`fix:`, Patch-Release).
2. Metadaten: Fastlane-Struktur, Changelog aus einer Quelle, `play-upload.yml` angepasst.
3. Release-Pipeline: Release-Commit per Deploy-Key, Job `apk`, Nebenläufigkeit; `-PappVersion`
   als Build-Eingang entfällt. Erst mergen, wenn Secrets, Deploy-Key und Ruleset-Ausnahme
   eingerichtet sind, sonst scheitert der Release-Commit.
4. Doku und Rezeptentwurf; README und Store-Beschreibung nennen die getrennten Signaturen von Play
   und den übrigen Kanälen.

Danach stellt der Maintainer die Anträge bei IzzyOnDroid und F-Droid.
