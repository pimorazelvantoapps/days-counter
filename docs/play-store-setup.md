# Play Store einrichten

Einmalige Schritte, damit die CI jede neue Version signiert in den **geschlossenen Test**
(Track `alpha`) hochlädt. Bis Schritt 7 erledigt ist, überspringt die CI den Upload; Tags und
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

## 3. App in der Play Console anlegen

1. *App erstellen*: Name „Days Counter“, Standardsprache Englisch (USA), App, kostenlos.
2. *Store-Eintrag*: Kurz- und Langbeschreibung, Symbol 512×512, Funktionsgrafik 1024×500,
   mindestens zwei Screenshots. Übersetzung Deutsch hinzufügen: Die CI liefert die
   „Neuerungen“ aus `distribution/whatsnew/` für `en-US` und `de-DE`.
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
read -rs -p "Keystore-Passwort: " UPLOAD_KEYSTORE_PASSWORD; echo
read -rs -p "Schlüssel-Passwort: " UPLOAD_KEY_PASSWORD; echo
UPLOAD_KEYSTORE_FILE=~/.keys/days-counter-upload.jks UPLOAD_KEY_ALIAS=upload \
  UPLOAD_KEYSTORE_PASSWORD=$UPLOAD_KEYSTORE_PASSWORD UPLOAD_KEY_PASSWORD=$UPLOAD_KEY_PASSWORD \
  ./gradlew bundleRelease -PappVersion=$VERSION --no-configuration-cache
git switch main
```

`app/build/outputs/bundle/release/app-release.aab` im geschlossenen Test als neue Version
hochladen, `app/build/outputs/mapping/release/mapping.txt` als Deobfuskierungsdatei
hinzufügen, Release zur Überprüfung einreichen.

Erst Versionen nach `v1.0.2` enthalten die Signatur-Konfiguration. Ist `v1.0.2` noch die
neueste Version, statt des Tags den aktuellen Stand von `main` mit `-PappVersion=1.0.2`
bauen (also `git switch --detach v$VERSION` weglassen): Er unterscheidet sich von `v1.0.2`
nur in CI, Dokumentation und eben der Signatur.

## 5. Google Cloud: Workload Identity Federation

Die CI meldet sich ohne gespeicherten Schlüssel an: GitHub stellt pro Lauf ein Token aus,
Google tauscht es nur für dieses Repository gegen kurzlebige Zugangsdaten.

```sh
PROJECT_ID=days-counter-ci        # weltweit eindeutig wählen
gcloud projects create $PROJECT_ID
gcloud config set project $PROJECT_ID
gcloud services enable androidpublisher.googleapis.com iam.googleapis.com \
  iamcredentials.googleapis.com sts.googleapis.com
PROJECT_NUMBER=$(gcloud projects describe $PROJECT_ID --format='value(projectNumber)')

gcloud iam service-accounts create play-upload --display-name="Play upload from GitHub Actions"
SERVICE_ACCOUNT=play-upload@$PROJECT_ID.iam.gserviceaccount.com

gcloud iam workload-identity-pools create github --location=global \
  --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers create-oidc days-counter --location=global \
  --workload-identity-pool=github \
  --issuer-uri=https://token.actions.githubusercontent.com \
  --attribute-mapping=google.subject=assertion.sub,attribute.repository=assertion.repository \
  --attribute-condition="assertion.repository == '$REPO'"

gcloud iam service-accounts add-iam-policy-binding $SERVICE_ACCOUNT \
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

Ab jetzt lädt jedes Release den Build in den geschlossenen Test. Scheitert ein Upload,
lässt er sich unter *Actions → Play upload → Run workflow* mit der Versionsnummer wiederholen.
