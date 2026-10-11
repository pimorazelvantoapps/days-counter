# F-Droid und IzzyOnDroid einrichten

Einmalige Schritte, damit die CI an jedem GitHub-Release eine APK `days-counter-X.Y.Z.apk`
anhängt, die mit einem eigenen Verteilungsschlüssel signiert ist. IzzyOnDroid übernimmt diese
APK; F-Droid baut die App reproduzierbar selbst und veröffentlicht stattdessen unsere Signatur.
Sobald der Pipeline-PR gemergt ist, braucht jedes Release die Schritte 3 und 4 (Deploy-Key und
Ruleset-Ausnahme); sonst weist das Ruleset den Release-Commit ab und das Release schlägt fehl.
Deshalb die Schritte 1 bis 5 vor dem Merge erledigen. Die erste Version mit Release-Commit und
APK ist das erste `feat`/`fix`/`build(deps)`-Release nach dem Merge; deren Version gehört in
das Rezept (Schritt 6).

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

Bei PKCS12-Keystores (dem Standard von `keytool`) ist das Schlüsselpasswort gleich dem
Keystore-Passwort; `DISTRIBUTION_KEY_PASSWORD` muss daher denselben Wert wie
`DISTRIBUTION_KEYSTORE_PASSWORD` haben.

## 3. Deploy-Key für den Release-Commit

Der Release-Bot pusht den Release-Commit (`version.properties`, Changelog) auf `main`. Das
Ruleset von `main` lässt das nur einen Deploy-Key tun.

Der Private Key liegt im Environment `release`, das ihn nur an Läufe von `main` herausgibt; ein
Repository-Secret wäre aus dem Workflow jedes Branches lesbar und umginge so das Ruleset von
`main`. Das Environment wie in [`play-store-setup.md`](play-store-setup.md), Schritt 2, anlegen:

```sh
gh api -X PUT repos/$REPO/environments/release \
  -F 'deployment_branch_policy[protected_branches]=false' \
  -F 'deployment_branch_policy[custom_branch_policies]=true'
gh api -X POST repos/$REPO/environments/release/deployment-branch-policies \
  -f name=main -f type=branch

ssh-keygen -t ed25519 -N "" -C days-counter-release -f ~/.keys/days-counter-release-deploy
gh repo deploy-key add ~/.keys/days-counter-release-deploy.pub --allow-write --title release-bot --repo $REPO
gh secret set RELEASE_DEPLOY_KEY --env release --repo $REPO < ~/.keys/days-counter-release-deploy
```

Die Ruleset-Ausnahme `DeployKey` (Schritt 4) gilt für jeden Deploy-Key mit Schreibrecht; deshalb
darf nur dieser eine Schreibrecht haben.

Fehlt `RELEASE_DEPLOY_KEY`, nutzt der Checkout HTTPS mit dem GitHub-Token, und das Ruleset von
`main` weist den Release-Commit ab; die Fehlermeldung nennt dann das Ruleset, die Ursache ist
aber der fehlende Deploy-Key. Ist der Key falsch oder nicht berechtigt, scheitert schon der
Fetch in `actions/checkout` oder die Push-Prüfung von semantic-release.

## 4. Ruleset-Ausnahme für Deploy-Keys

Das Ruleset des Branches `main` heißt `main`. Auslesen, Deploy-Keys als Ausnahme eintragen und
zurückschreiben:

```sh
RULESET_ID=$(gh api repos/$REPO/rulesets --jq '.[] | select(.name=="main") | .id')
[ -n "$RULESET_ID" ] || { echo "Ruleset main nicht gefunden" >&2; false; }
gh api repos/$REPO/rulesets/$RULESET_ID \
  | jq '{name, target, enforcement, conditions, rules,
         bypass_actors: ((.bypass_actors // []) + [{"actor_type":"DeployKey","bypass_mode":"always"}])}' \
  | gh api -X PUT repos/$REPO/rulesets/$RULESET_ID --input -
gh api repos/$REPO/rulesets/$RULESET_ID --jq '.bypass_actors'   # muss den Eintrag enthalten
```

Nur nötig, falls das optionale Ruleset `release tags` aus
[`play-store-setup.md`](play-store-setup.md), Schritt 2, eingerichtet wurde: semantic-release
pusht die Tags per SSH als Deploy-Key, das Ruleset würde sie sonst abweisen. Dieselbe Ausnahme
dort eintragen:

```sh
TAG_RULESET_ID=$(gh api repos/$REPO/rulesets --jq '.[] | select(.name=="release tags") | .id')
if [ -n "$TAG_RULESET_ID" ]; then
  gh api repos/$REPO/rulesets/$TAG_RULESET_ID \
    | jq '{name, target, enforcement, conditions, rules,
           bypass_actors: ((.bypass_actors // []) + [{"actor_type":"DeployKey","bypass_mode":"always"}])}' \
    | gh api -X PUT repos/$REPO/rulesets/$TAG_RULESET_ID --input -
else
  echo "Kein Ruleset 'release tags' vorhanden, nichts zu tun"
fi
```

## 5. Bestätigung für Android-Entwickler

Google verlangt in den betroffenen Ländern, dass Apps auf zertifizierten Geräten von
registrierten Entwicklern stammen. Ohne Registrierung verweigern diese Geräte die Installation
der APKs aus GitHub, IzzyOnDroid und F-Droid. Registrierbar ist der Verteilungsschlüssel nur,
solange F-Droid unsere Signatur übernimmt, deshalb vor dem ersten APK-Release:

1. SHA-256 des Zertifikats ausgeben; die Play Console erwartet die Form mit Doppelpunkten in
   Großbuchstaben, wie `keytool` sie ausgibt:

   ```sh
   keytool -list -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution | grep SHA256
   ```

2. In der Play Console den Verteilungsschlüssel zur App `com.pimorazelvanto.dayscounter`
   hinzufügen.
3. Den Besitznachweis nach Anleitung der Console erbringen.
4. Erst danach die Variable setzen, die den `apk`-Job der CI einschaltet und damit die
   Veröffentlichung der APKs startet:

   ```sh
   gh variable set DISTRIBUTION_KEY_CONFIGURED --body true --repo $REPO
   ```

## 6. Fingerabdruck für das F-Droid-Rezept

`AllowedAPKSigningKeys` verlangt den Fingerabdruck in Kleinbuchstaben ohne Doppelpunkte:

```sh
keytool -list -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution \
  | sed -n 's/.*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f'
```

Ergebnis in das Rezept
[`fdroid/com.pimorazelvanto.dayscounter.yml`](fdroid/com.pimorazelvanto.dayscounter.yml)
eintragen. Beim Einreichen außerdem die Platzhalter `versionName`, `versionCode`, `commit` und
`CurrentVersion*` füllen: mit der ersten Version, die einen Release-Commit und eine APK hat
(das erste `feat`/`fix`/`build(deps)`-Release nach dem Merge der Pipeline); `commit` ist der Tag
des Release-Commits.

Optional lässt sich lokal prüfen, dass der Build reproduzierbar ist: `apksigcopier` verlangt
`apksigner` aus den Build-Tools des Android SDK im `PATH`. Der Pfad ist ein Beispiel und hängt
von Betriebssystem und installierten Build-Tools ab (das Projekt nutzt Build-Tools 37.0.0):

```sh
export PATH="$HOME/Library/Android/sdk/build-tools/37.0.0:$PATH"
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

Siehe den Abschnitt „Releases“ im [`README.md`](../README.md).

Der Satz zum Signaturwechsel am Ende von `fastlane/metadata/android/*/full_description.txt`
gehört nur in den Eintrag bei F-Droid und IzzyOnDroid; nicht in den Play-Console-Eintrag
kopieren.
