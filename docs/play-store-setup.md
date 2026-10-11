# Play Store einrichten

Einmalige Schritte, damit die CI jede neue Version signiert in den **geschlossenen Test**
(Track `alpha`) hochlädt, auf Wunsch zusätzlich in den internen Test (siehe Schritt 7). Bis Schritt 7 erledigt ist, überspringt die CI den Upload; Tags und
GitHub-Releases entstehen wie bisher.

Reihenfolge einhalten: Die Play Developer API kann keine App anlegen, die erste Version muss
von Hand hochgeladen werden (Schritt 4).

Variablen für die Befehle unten:

```sh
REPO=pimorazelvantoapps/days-counter
PACKAGE=com.pimorazelvanto.dayscounter
export GH_HOST=github.com
```

## 1. Upload-Schlüssel erzeugen

Google signiert die ausgelieferte App mit seinem eigenen Schlüssel (Play App Signing). Der
Upload-Schlüssel beweist nur, dass ein Upload von dir kommt; geht er verloren, setzt der
Play-Support ihn zurück.

```sh
mkdir -p -m 700 ~/.keys
keytool -genkeypair -v -keystore ~/.keys/days-counter-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 9125
chmod 600 ~/.keys/days-counter-upload.jks
```

Keystore-Datei und beide Passwörter im Passwortmanager sichern. Nie ins Repo legen.

## 2. GitHub-Environment und Secrets

Das Environment `play-store` gibt die Secrets nur an Läufe von `main` heraus.

```sh
gh api -X PUT repos/$REPO/environments/play-store \
  -F 'deployment_branch_policy[protected_branches]=false' \
  -F 'deployment_branch_policy[custom_branch_policies]=true'
gh api -X POST repos/$REPO/environments/play-store/deployment-branch-policies \
  -f name=main -f type=branch

base64 -i ~/.keys/days-counter-upload.jks | gh secret set UPLOAD_KEYSTORE_BASE64 --env play-store --repo $REPO
gh secret set UPLOAD_KEYSTORE_PASSWORD --env play-store --repo $REPO   # fragt nach dem Wert
gh secret set UPLOAD_KEY_ALIAS --env play-store --repo $REPO --body upload
gh secret set UPLOAD_KEY_PASSWORD --env play-store --repo $REPO       # fragt nach dem Wert
```

Die Branch-Policy prüft nur den Ref, von dem ein Lauf startet, nicht den Tag, den der Upload-Job
baut und signiert. Der Job bricht deshalb ab, wenn der Tag kein Vorfahre von `main` ist. Optional
verhindert zusätzlich ein Ruleset, dass jemand außer Admins und der CI Versions-Tags anlegt,
verschiebt oder löscht (15368 ist die App „GitHub Actions“, mit deren Token semantic-release die
Tags anlegt; nach dem ersten Release danach prüfen, dass der Tag entstanden ist):

```sh
gh api -X POST repos/$REPO/rulesets --input - <<'JSON'
{
  "name": "release tags",
  "target": "tag",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["refs/tags/v*"], "exclude": [] } },
  "rules": [{ "type": "creation" }, { "type": "update" }, { "type": "deletion" }],
  "bypass_actors": [
    { "actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always" },
    { "actor_id": 15368, "actor_type": "Integration", "bypass_mode": "always" }
  ]
}
JSON
```

## 3. App in der Play Console anlegen

1. *App erstellen*: Name „Days Counter“, Standardsprache Englisch (USA), App, kostenlos.
2. *Store-Eintrag*: Kurz- und Langbeschreibung, Symbol 512×512, Funktionsgrafik 1024×500,
   mindestens zwei Screenshots. Übersetzung Deutsch hinzufügen: Die CI liefert die
   „Neuerungen“ aus `fastlane/metadata/android/<sprache>/changelogs/default.txt` für `en-US` und `de-DE`.
3. *App-Inhalte*: Datenschutzerklärung
   `https://github.com/pimorazelvantoapps/days-counter/blob/main/PRIVACY.md`;
   Datensicherheit „Es werden keine Nutzerdaten erhoben oder geteilt“; Einstufung des
   Inhalts; Zielgruppe; keine Werbung.
4. *Test → Geschlossener Test*: Track anlegen, Testerliste (E-Mail-Liste oder Google Group)
   mit mindestens 12 Personen. Für den Produktionszugang müssen sie 14 Tage ohne
   Unterbrechung eingetragen bleiben.

## 4. Erste Version von Hand hochladen

Die aktuelle Version lokal signiert bauen, ohne dass Passwörter in der Shell-History landen:

```sh
VERSION=$(git describe --tags --abbrev=0 | sed 's/^v//')
git switch --detach v$VERSION
read -rs -p "Keystore-Passwort: " SIGNING_KEYSTORE_PASSWORD; echo
read -rs -p "Schlüssel-Passwort: " SIGNING_KEY_PASSWORD; echo
SIGNING_KEYSTORE_FILE=~/.keys/days-counter-upload.jks SIGNING_KEY_ALIAS=upload \
  SIGNING_KEYSTORE_PASSWORD=$SIGNING_KEYSTORE_PASSWORD SIGNING_KEY_PASSWORD=$SIGNING_KEY_PASSWORD \
  ./gradlew bundleRelease --no-configuration-cache
git switch main
```

`app/build/outputs/bundle/release/app-release.aab` im geschlossenen Test als neue Version
hochladen, `app/build/outputs/mapping/release/mapping.txt` als Deobfuskierungsdatei
hinzufügen, Release zur Überprüfung einreichen.

Tags vor dem ersten Release-Commit enthalten keine passende `version.properties`; die erste
Version von Hand nur ab einem Tag mit Release-Commit bauen.

## 5. Google Cloud: Workload Identity Federation

Die CI meldet sich ohne gespeicherten Schlüssel an: GitHub stellt pro Lauf ein Token aus,
Google tauscht es nur für Läufe dieses Repositorys gegen kurzlebige Zugangsdaten, die im
Environment `play-store` auf `main` laufen. Geprüft wird die unveränderliche Repository-ID, nicht
der Name, der sich umbenennen und danach neu vergeben lässt.

```sh
PROJECT_ID=days-counter-ci        # weltweit eindeutig wählen
gcloud projects create $PROJECT_ID
gcloud config set project $PROJECT_ID
gcloud services enable androidpublisher.googleapis.com iam.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')
REPO_ID=$(gh api repos/$REPO --jq .id)

gcloud iam service-accounts create play-upload --display-name="Play upload from GitHub Actions"
SERVICE_ACCOUNT=play-upload@$PROJECT_ID.iam.gserviceaccount.com

gcloud iam workload-identity-pools create github --location=global \
  --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc days-counter --location=global \
  --workload-identity-pool=github \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping=google.subject=assertion.sub,attribute.repository_id=assertion.repository_id \
  --attribute-condition="assertion.repository_id == '$REPO_ID' && assertion.environment == 'play-store' && assertion.ref == 'refs/heads/main'"

gcloud iam service-accounts add-iam-policy-binding $SERVICE_ACCOUNT \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository_id/$REPO_ID"
```

Ein früher eingerichteter Provider, der nur den Repository-Namen prüft, wird so nachgezogen
(dieselben Variablen wie oben):

```sh
gcloud iam workload-identity-pools providers update-oidc days-counter --location=global \
  --workload-identity-pool=github \
  --attribute-mapping=google.subject=assertion.sub,attribute.repository_id=assertion.repository_id \
  --attribute-condition="assertion.repository_id == '$REPO_ID' && assertion.environment == 'play-store' && assertion.ref == 'refs/heads/main'"
gcloud iam service-accounts add-iam-policy-binding $SERVICE_ACCOUNT \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository_id/$REPO_ID"
gcloud iam service-accounts remove-iam-policy-binding $SERVICE_ACCOUNT \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"
```

## 6. Service Account in der Play Console berechtigen

*Nutzer und Berechtigungen → Neue Nutzer einladen*: E-Mail-Adresse aus `$SERVICE_ACCOUNT`,
unter *App-Berechtigungen* „Days Counter“ hinzufügen und „Apps in Test-Tracks
veröffentlichen“ erlauben. Keine Kontoberechtigungen vergeben.

## 7. Upload in der CI einschalten

Die Werte sind nicht geheim und liegen deshalb als Repository-Variablen vor; der Job `play`
prüft sie, bevor er startet.

```sh
gh variable set GCP_WORKLOAD_IDENTITY_PROVIDER --repo $REPO \
  --body "projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/days-counter"
gh variable set GCP_SERVICE_ACCOUNT --repo $REPO --body "$SERVICE_ACCOUNT"
```

Solange Google noch kein Release der App geprüft hat, gilt sie als Entwurf und nimmt per API
nur Entwürfe an. Bis dahin die Uploads als Entwurf anlegen lassen und in der Play Console
selbst zur Überprüfung einreichen:

```sh
gh variable set PLAY_RELEASE_STATUS --repo $REPO --body draft
```

Nach der ersten Freigabe die Variable löschen (`gh variable delete PLAY_RELEASE_STATUS --repo
$REPO`); danach veröffentlicht die CI jede Version direkt im Track.

Die Ziel-Tracks bestimmt die Variable `PLAY_TRACKS` (kommagetrennt, ohne sie nur `alpha`).
Der interne Test braucht keine Prüfung durch Google und steht nach wenigen Minuten bereit,
zählt aber nicht für die 12 Tester über 14 Tage; deshalb beide Tracks bedienen:

```sh
gh variable set PLAY_TRACKS --repo $REPO --body internal,alpha
```

Ab jetzt lädt jedes Release den Build in die gewählten Tracks. Scheitert ein Upload,
lässt er sich unter *Actions → Play upload → Run workflow* mit der Versionsnummer wiederholen.
