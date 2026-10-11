# F-Droid und IzzyOnDroid einrichten

Einmalige Schritte, damit die CI an jedem GitHub-Release eine APK `days-counter-X.Y.Z.apk`
anhängt, die mit einem eigenen Verteilungsschlüssel signiert ist. IzzyOnDroid übernimmt diese
APK; F-Droid baut die App reproduzierbar selbst und veröffentlicht stattdessen unsere Signatur.
Bis Schritt 2 erledigt ist, überspringt die CI den `apk`-Job; Tags und GitHub-Releases
entstehen wie bisher.

Schritte 1 bis 5 müssen erledigt sein, bevor das erste Release mit APK entsteht.

Variablen für die Befehle unten:

```sh
REPO=pimorazelvantoapps/days-counter
PACKAGE=com.pimorazelvanto.dayscounter
export GH_HOST=github.com
```

## 1. Verteilungsschlüssel erzeugen und sichern

Dieser Schlüssel signiert die APKs aus GitHub, IzzyOnDroid und F-Droid. Er ist ein anderer als
der Upload-Schlüssel des Play Stores.

```sh
mkdir -p -m 700 ~/.keys
keytool -genkeypair -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution \
  -keyalg RSA -keysize 4096 -validity 9125
chmod 600 ~/.keys/days-counter-distribution.jks
```

Keystore-Datei und beide Passwörter im Passwortmanager sichern. Nie ins Repo legen. Geht der
Schlüssel verloren, lässt sich die App bei F-Droid und IzzyOnDroid nicht mehr aktualisieren.

## 2. Secrets und Variable setzen

Die Secrets liegen im Environment `play-store` (siehe
[`play-store-setup.md`](play-store-setup.md), Schritt 2), das sie nur an Läufe von `main`
herausgibt.

```sh
base64 -i ~/.keys/days-counter-distribution.jks | gh secret set DISTRIBUTION_KEYSTORE_BASE64 --env play-store --repo $REPO
gh secret set DISTRIBUTION_KEYSTORE_PASSWORD --env play-store --repo $REPO   # fragt nach dem Wert
gh secret set DISTRIBUTION_KEY_ALIAS --env play-store --repo $REPO --body distribution
gh secret set DISTRIBUTION_KEY_PASSWORD --env play-store --repo $REPO       # fragt nach dem Wert
```

Erst danach die Variable setzen, die den `apk`-Job der CI einschaltet:

```sh
gh variable set DISTRIBUTION_KEY_CONFIGURED --body true --repo $REPO
```

## 3. Deploy-Key für den Release-Commit

Der Release-Bot pusht den Release-Commit (`version.properties`, Changelog) auf `main`. Das
Ruleset von `main` lässt das nur einen Deploy-Key tun.

```sh
ssh-keygen -t ed25519 -N "" -C days-counter-release -f ~/.keys/days-counter-release-deploy
gh repo deploy-key add ~/.keys/days-counter-release-deploy.pub --allow-write --title release-bot --repo $REPO
gh secret set RELEASE_DEPLOY_KEY --repo $REPO < ~/.keys/days-counter-release-deploy
```

Fehlt `RELEASE_DEPLOY_KEY` oder ist er falsch, fällt semantic-release auf einen Push über HTTPS
mit dem GitHub-Token zurück, und das Ruleset von `main` weist den Release-Commit ab. Die
Fehlermeldung nennt dann das Ruleset, die Ursache ist aber der Deploy-Key.

## 4. Ruleset-Ausnahme für Deploy-Keys

Das Ruleset `main` auslesen, Deploy-Keys als Ausnahme eintragen und zurückschreiben:

```sh
RULESET_ID=$(gh api repos/$REPO/rulesets --jq '.[] | select(.name=="main") | .id')
gh api repos/$REPO/rulesets/$RULESET_ID \
  | jq '{name, target, enforcement, conditions, rules,
         bypass_actors: ((.bypass_actors // []) + [{"actor_type":"DeployKey","bypass_mode":"always"}])}' \
  | gh api -X PUT repos/$REPO/rulesets/$RULESET_ID --input -
```

Danach prüfen, dass `bypass_actors` den Eintrag enthält:

```sh
gh api repos/$REPO/rulesets/$RULESET_ID --jq '.bypass_actors'
```

## 5. Bestätigung für Android-Entwickler

Google verlangt in den betroffenen Ländern, dass Apps auf zertifizierten Geräten von
registrierten Entwicklern stammen. Ohne Registrierung verweigern diese Geräte die Installation
der APKs aus GitHub, IzzyOnDroid und F-Droid. Registrierbar ist der Verteilungsschlüssel nur,
solange F-Droid unsere Signatur übernimmt, deshalb vor dem ersten APK-Release:

1. SHA-256 des Zertifikats ausgeben (siehe Schritt 6).
2. In der Play Console den Verteilungsschlüssel zur App `$PACKAGE` hinzufügen.
3. Den Besitznachweis nach Anleitung der Console erbringen.

## 6. Fingerabdruck für das F-Droid-Rezept

```sh
keytool -list -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution | grep SHA256
```

Doppelpunkte entfernen, in Kleinbuchstaben umwandeln und in `AllowedAPKSigningKeys` des Rezepts
[`fdroid/com.pimorazelvanto.dayscounter.yml`](fdroid/com.pimorazelvanto.dayscounter.yml)
eintragen. Beim Einreichen außerdem `versionName`, `versionCode`, `commit` und
`CurrentVersion*` auf das dann aktuelle Release setzen; `commit` ist der Tag des Release-Commits.

Optional lässt sich lokal prüfen, dass der Build reproduzierbar ist: `apksigcopier` verlangt
`apksigner` aus den Build-Tools des Android SDK im `PATH`:

```sh
export PATH="$HOME/Library/Android/sdk/build-tools/34.0.0:$PATH"
```

## 7. IzzyOnDroid

Ein Issue unter <https://codeberg.org/IzzyOnDroid/repo/issues> anlegen:

```
App: Days Counter (com.pimorazelvanto.dayscounter)
Source: https://github.com/pimorazelvantoapps/days-counter (MIT)
APKs: attached to every GitHub release as days-counter-X.Y.Z.apk, signed with the same key F-Droid will use (reproducible builds)
Metadata: fastlane/metadata/android (en-US, de-DE)
No trackers, no network permission.
```

## 8. F-Droid

1. `https://gitlab.com/fdroid/fdroiddata` forken.
2. Das Rezept aus Schritt 6 nach `metadata/com.pimorazelvanto.dayscounter.yml` kopieren.
3. Lokal prüfen:

   ```sh
   fdroid readmeta
   fdroid lint com.pimorazelvanto.dayscounter
   fdroid build -v -l com.pimorazelvanto.dayscounter   # optional
   ```

4. Merge Request stellen.

## 9. Wechsel zwischen Quellen

Installationen aus dem Play Store tragen Googles Signatur, Installationen aus F-Droid,
IzzyOnDroid und von GitHub den Verteilungsschlüssel. Ein Wechsel zwischen beiden Gruppen
erfordert eine Neuinstallation.
