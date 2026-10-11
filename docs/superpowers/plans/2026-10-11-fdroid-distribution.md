# F-Droid- und IzzyOnDroid-Verteilung – Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jedes Release erzeugt ein reproduzierbar gebautes, mit eigenem Verteilungsschlüssel signiertes APK am GitHub-Release, das F-Droid und IzzyOnDroid automatisch übernehmen; der Play-Store-Ablauf bleibt unverändert.

**Architecture:** Die Version liegt in `version.properties` und wird von semantic-release in einem Release-Commit per Deploy-Key gepflegt; eine kleine Kotlin-Klasse in `buildSrc` ist die einzige Stelle für Parsen, Formel und Dateiformat. Gradle signiert `release` mit dem Schlüssel aus neutralen `SIGNING_*`-Variablen, die der Play-Job mit dem Upload- und der neue APK-Job mit dem Verteilungsschlüssel füllt. Store-Texte und -Bilder liegen im Fastlane-Format unter `fastlane/metadata/android/`.

**Tech Stack:** Gradle 9.6 (Kotlin DSL, `buildSrc` mit `kotlin-dsl`), AGP 9.4, JUnit 5, semantic-release 25 mit `@semantic-release/git` 11.0.1, GitHub Actions, Python 3 (Standardbibliothek) für das Prüfskript, `apksigcopier` nur lokal.

**Spec:** `docs/superpowers/specs/2026-10-11-fdroid-distribution-design.md`

## Global Constraints

- `versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`; Minor und Patch < 1000; Version muss `MAJOR.MINOR.PATCH` sein.
- `version.properties` im Wurzelverzeichnis, exakt zwei Zeilen `versionName=X.Y.Z` und `versionCode=N`, LF, keine Kommentare, kein Zeitstempel.
- `version.properties` und `fastlane/metadata/android/*/changelogs/<versionCode>.txt` ändert nur der Release-Bot.
- Signaturvariablen: `SIGNING_KEYSTORE_FILE`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`; ohne sie bleibt `release` unsigniert.
- Neue Secrets im Environment `play-store`: `DISTRIBUTION_KEYSTORE_BASE64`, `DISTRIBUTION_KEYSTORE_PASSWORD`, `DISTRIBUTION_KEY_ALIAS`, `DISTRIBUTION_KEY_PASSWORD`; Repository-Secret `RELEASE_DEPLOY_KEY`.
- APK-Dateiname am Release: `days-counter-X.Y.Z.apk`.
- Release-Commit-Nachricht: `chore(release): X.Y.Z [skip ci]`.
- Sprachen der Store-Metadaten: `en-US` (Rückfall) und `de-DE`; englische Texte schreiben „color“.
- Bestehende Projektregeln aus `CLAUDE.md` gelten: Conventional Commits, `./gradlew check` aus dem Wurzelverzeichnis vor jedem Commit, keine Claude-Attribution in Commits, Änderungen nur per PR mit Rebase-Merge.

## Review Focus

- `version.properties` fehlt oder ist kaputt (alter Tag, Handfehler): Der Build muss mit klarer Meldung abbrechen statt mit Version 0 oder 1 zu bauen → Tests in Task 1.
- Zwei Merges kurz nacheinander: kein Release-Commit ohne Tag, kein Tag ohne APK; der neuere Lauf releast beide → Verifikation in Task 8.
- Eine Sprache ohne `changelogs/default.txt`: Der Release muss vor Commit und Tag scheitern → Prüfung im Skript von Task 6.
- Ein künftiges AGP-Update schreibt den Block „Dependency metadata“ wieder ins APK: Der APK-Job darf dann nichts hochladen → Prüfskript in Task 4, Einsatz in Task 7.
- Manueller Nachlauf (`workflow_dispatch`) für einen Tag, der nicht auf `main` liegt oder älter als die Fastlane-Metadaten ist: klarer Abbruch statt Upload → Schritte in Task 5 und Task 7.

---

## PR A: Reproduzierbarer Build (Branch `build/fdroid-reproducible`)

### Task 1: `AppVersion` in `buildSrc`

**Files:**
- Create: `buildSrc/settings.gradle.kts`
- Create: `buildSrc/build.gradle.kts`
- Create: `buildSrc/src/main/kotlin/AppVersion.kt`
- Create: `buildSrc/src/test/kotlin/AppVersionTest.kt`
- Modify: `build.gradle.kts` (Root: `check` hängt an den `buildSrc`-Tests)

**Interfaces:**
- Produces: `data class AppVersion(val name: String, val code: Int)` mit
  `AppVersion.fromSemanticVersion(semanticVersion: String): AppVersion`,
  `AppVersion.parseProperties(text: String): AppVersion`,
  `fun toProperties(): String`.

- [ ] **Step 1: Build-Gerüst anlegen**

`buildSrc/settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "buildSrc"
```

`buildSrc/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Failing tests schreiben**

`buildSrc/src/test/kotlin/AppVersionTest.kt`:

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppVersionTest {
    @Test
    fun `semantic version packs major minor and patch into the version code`() {
        assertEquals(AppVersion("1.2.3", 1_002_003), AppVersion.fromSemanticVersion("1.2.3"))
    }

    @Test
    fun `semantic version with fewer than three parts is rejected`() {
        val error = assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.2") }
        assertEquals("Version must look like MAJOR.MINOR.PATCH, was '1.2'", error.message)
    }

    @Test
    fun `semantic version with a pre-release suffix is rejected`() {
        assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.2.3-beta.1") }
    }

    @Test
    fun `minor of 1000 does not fit the version code scheme`() {
        val error = assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.1000.0") }
        assertEquals(
            "Version '1.1000.0' does not fit the versionCode scheme: minor and patch must be below 1000",
            error.message,
        )
    }

    @Test
    fun `patch of 1000 does not fit the version code scheme`() {
        assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.0.1000") }
    }

    @Test
    fun `properties text holds exactly name and code`() {
        assertEquals("versionName=1.0.7\nversionCode=1000007\n", AppVersion("1.0.7", 1_000_007).toProperties())
    }

    @Test
    fun `properties text parses back into the same version`() {
        val version = AppVersion.fromSemanticVersion("2.5.9")
        assertEquals(version, AppVersion.parseProperties(version.toProperties()))
    }

    @Test
    fun `properties text without version code is rejected`() {
        val error = assertThrows<IllegalArgumentException> { AppVersion.parseProperties("versionName=1.0.7\n") }
        assertEquals("version.properties lacks versionCode", error.message)
    }

    @Test
    fun `properties text with a non-numeric version code is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                AppVersion.parseProperties("versionName=1.0.7\nversionCode=seven\n")
            }
        assertEquals("version.properties has a non-numeric versionCode 'seven'", error.message)
    }

    @Test
    fun `properties text whose code does not match its name is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                AppVersion.parseProperties("versionName=1.0.7\nversionCode=1000008\n")
            }
        assertEquals("version.properties pairs versionName 1.0.7 with versionCode 1000008, expected 1000007", error.message)
    }
}
```

- [ ] **Step 3: Test laufen lassen, er muss fehlschlagen**

Run: `./gradlew -p buildSrc test`
Expected: Kompilierfehler `Unresolved reference 'AppVersion'`.

- [ ] **Step 4: Implementierung**

`buildSrc/src/main/kotlin/AppVersion.kt`:

```kotlin
/**
 * The app's release version as Google Play and F-Droid read it. The code packs
 * `MAJOR.MINOR.PATCH` into `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`, so every release gets a
 * strictly greater `versionCode` than its predecessor; minor and patch must therefore stay below
 * 1000. F-Droid reads both values with a regex from `version.properties`, hence the fixed,
 * comment-free format.
 */
data class AppVersion(
    val name: String,
    val code: Int,
) {
    fun toProperties(): String = "versionName=$name\nversionCode=$code\n"

    companion object {
        private val SEMANTIC_VERSION = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun fromSemanticVersion(semanticVersion: String): AppVersion {
            val (major, minor, patch) =
                requireNotNull(SEMANTIC_VERSION.matchEntire(semanticVersion)) {
                    "Version must look like MAJOR.MINOR.PATCH, was '$semanticVersion'"
                }.destructured.toList().map(String::toInt)
            require(minor < 1_000 && patch < 1_000) {
                "Version '$semanticVersion' does not fit the versionCode scheme: minor and patch must be below 1000"
            }
            return AppVersion(semanticVersion, major * 1_000_000 + minor * 1_000 + patch)
        }

        fun parseProperties(text: String): AppVersion {
            val values =
                text
                    .lines()
                    .filter { '=' in it }
                    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            val name = requireNotNull(values["versionName"]) { "version.properties lacks versionName" }
            val codeText = requireNotNull(values["versionCode"]) { "version.properties lacks versionCode" }
            val code =
                requireNotNull(codeText.toIntOrNull()) {
                    "version.properties has a non-numeric versionCode '$codeText'"
                }
            val expected = fromSemanticVersion(name)
            require(expected.code == code) {
                "version.properties pairs versionName $name with versionCode $code, expected ${expected.code}"
            }
            return expected
        }
    }
}
```

- [ ] **Step 5: Tests grün**

Run: `./gradlew -p buildSrc test`
Expected: `BUILD SUCCESSFUL`, 10 Tests bestanden (Bericht `buildSrc/build/reports/tests/test/index.html`).

- [ ] **Step 6: `check` des Wurzelprojekts führt die `buildSrc`-Tests aus**

In `build.gradle.kts` (Root) den bestehenden Block erweitern:

```kotlin
tasks.check {
    dependsOn(tasks.named("detekt"))
    dependsOn(gradle.includedBuild("buildSrc").task(":test"))
}
```

Run: `./gradlew check --dry-run | grep -i buildsrc`
Expected: eine Zeile mit `:buildSrc:test`. Falls Gradle `includedBuild("buildSrc")` nicht findet, statt dessen in `.github/workflows/ci.yml` im Job `check` vor `./gradlew check assembleDebug` die Zeile `./gradlew -p buildSrc test` ergänzen und in `CLAUDE.md` unter „Befehle“ aufnehmen.

- [ ] **Step 7: Volle Prüfung und Commit**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

```bash
git add buildSrc build.gradle.kts
git commit -m "build: model the release version in buildSrc"
```

### Task 2: Version aus `version.properties`, Task `writeVersionProperties`

**Files:**
- Create: `version.properties`
- Modify: `app/build.gradle.kts:8-24` (alte `versionCodeOf`-Funktion und `releaseVersion` ersetzen), `defaultConfig`
- Modify: `build.gradle.kts` (Root: Task registrieren)
- Modify: `README.md` (Satz „Lokale Builds tragen die Version `0.0.0-dev`.“)

**Interfaces:**
- Consumes: `AppVersion.fromSemanticVersion`, `AppVersion.parseProperties`, `AppVersion.toProperties` (Task 1).
- Produces: Gradle-Task `writeVersionProperties` (Root), Aufruf `./gradlew writeVersionProperties -PappVersion=X.Y.Z`; Datei `version.properties`.

- [ ] **Step 1: Startdatei mit dem aktuellen Release anlegen**

Run:

```bash
version=$(git describe --tags --abbrev=0 origin/main | sed 's/^v//')
echo "$version"
```

Expected: `1.0.6` (oder neuer). `version.properties` mit dem zugehörigen Code anlegen, für 1.0.6:

```
versionName=1.0.6
versionCode=1000006
```

- [ ] **Step 2: Task `writeVersionProperties` im Root registrieren**

In `build.gradle.kts` (Root) ergänzen:

```kotlin
// Called by semantic-release while it prepares a release; the release commit carries the file,
// so F-Droid builds the same version from the tag.
tasks.register("writeVersionProperties") {
    val semanticVersion = providers.gradleProperty("appVersion")
    val target = layout.projectDirectory.file("version.properties")
    doLast {
        target.asFile.writeText(AppVersion.fromSemanticVersion(semanticVersion.get()).toProperties())
    }
}
```

- [ ] **Step 3: Task prüfen**

Run:

```bash
./gradlew writeVersionProperties -PappVersion=9.8.7 -q && cat version.properties
./gradlew writeVersionProperties -PappVersion=9.8 -q 2>&1 | grep "MAJOR.MINOR.PATCH"
git checkout -- version.properties
```

Expected: `versionName=9.8.7` / `versionCode=9008007`; zweiter Aufruf meldet `Version must look like MAJOR.MINOR.PATCH, was '9.8'`; Datei danach wieder auf 1.0.6.

- [ ] **Step 4: App liest die Version aus der Datei, `-PappVersion` hat übergangsweise Vorrang**

In `app/build.gradle.kts` die Zeilen 8–24 (`releaseVersion` und `versionCodeOf`) ersetzen durch:

```kotlin
// -PappVersion takes precedence only until semantic-release writes version.properties in the
// release commit; see docs/superpowers/specs/2026-10-11-fdroid-distribution-design.md.
val appVersion: AppVersion =
    providers.gradleProperty("appVersion").orNull?.let(AppVersion::fromSemanticVersion)
        ?: AppVersion.parseProperties(
            providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get(),
        )
```

und in `defaultConfig`:

```kotlin
        versionCode = appVersion.code
        versionName = appVersion.name
```

- [ ] **Step 5: Verhalten prüfen**

Run:

```bash
B=~/Library/Android/sdk/build-tools/37.0.0/aapt2
./gradlew assembleDebug -q && $B dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
./gradlew assembleDebug -PappVersion=1.2.3 -q && $B dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
cp version.properties /tmp/vp && printf 'versionName=1.0.6\n' > version.properties && ./gradlew assembleDebug -q 2>&1 | grep "lacks versionCode"; cp /tmp/vp version.properties
```

Expected: `versionCode='1000006' versionName='1.0.6'`; dann `versionCode='1002003' versionName='1.2.3'`; dann die Meldung `version.properties lacks versionCode`.

- [ ] **Step 6: README anpassen**

In `README.md` den Satz „Lokale Builds tragen die Version `0.0.0-dev`.“ ersetzen durch:

```
Die Version steht in `version.properties`; semantic-release aktualisiert sie in einem
Release-Commit, lokale Builds tragen daher die zuletzt veröffentlichte Version.
```

- [ ] **Step 7: Prüfen und Commit**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

```bash
git add version.properties build.gradle.kts app/build.gradle.kts README.md
git commit -m "build: read the version from version.properties"
```

### Task 3: Signatur mit neutralen `SIGNING_*`-Variablen

**Files:**
- Modify: `app/build.gradle.kts` (`uploadKeystoreFile`, `signingConfigs`, `release.signingConfig`)
- Modify: `.github/workflows/play-upload.yml` (Schritte „Decode upload keystore“, „Build signed bundle“, „Remove upload keystore“)
- Modify: `docs/play-store-setup.md` (Abschnitt 4, lokaler signierter Build)

**Interfaces:**
- Produces: Signatur-Config `release` liest `SIGNING_KEYSTORE_FILE`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

- [ ] **Step 1: Gradle umstellen**

In `app/build.gradle.kts`:

```kotlin
/**
 * Keystore that signs the release build: the Play upload key for the bundle, the distribution key
 * for the APK on GitHub, IzzyOnDroid and F-Droid. Without it the build stays unsigned, as F-Droid
 * and pull requests build it.
 */
val signingKeystoreFile: String? = providers.environmentVariable("SIGNING_KEYSTORE_FILE").orNull
```

```kotlin
    signingConfigs {
        if (signingKeystoreFile != null) {
            create("release") {
                storeFile = file(signingKeystoreFile)
                storePassword = providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }
```

und im Build-Typ `release` den Kommentar und die Zeile ersetzen durch:

```kotlin
            signingConfig = signingConfigs.findByName("release")
```

- [ ] **Step 2: Lokal mit Wegwerf-Schlüssel prüfen**

Run:

```bash
S=$(mktemp -d)
keytool -genkeypair -keystore $S/t.jks -alias t -keyalg RSA -keysize 2048 -validity 1 -storepass testpass -keypass testpass -dname CN=Test >/dev/null 2>&1
SIGNING_KEYSTORE_FILE=$S/t.jks SIGNING_KEYSTORE_PASSWORD=testpass SIGNING_KEY_ALIAS=t SIGNING_KEY_PASSWORD=testpass \
  ./gradlew assembleRelease --no-configuration-cache -q
~/Library/Android/sdk/build-tools/37.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | grep DN
rm -rf app/build/outputs/apk/release; ./gradlew assembleRelease -q; ls app/build/outputs/apk/release/ | grep apk
rm -rf $S
```

Expected: `certificate DN: CN=Test`; ohne Variablen `app-release-unsigned.apk`.

- [ ] **Step 3: `play-upload.yml` auf die neuen Namen umstellen**

Die drei Schritte ersetzen durch:

```yaml
      - name: Decode upload keystore
        env:
          UPLOAD_KEYSTORE_BASE64: ${{ secrets.UPLOAD_KEYSTORE_BASE64 }}
        run: |
          echo "$UPLOAD_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/signing.jks"
          echo "SIGNING_KEYSTORE_FILE=$RUNNER_TEMP/signing.jks" >> "$GITHUB_ENV"

      # Without the configuration cache: it would store the key passwords on disk.
      - name: Build signed bundle
        env:
          SIGNING_KEYSTORE_PASSWORD: ${{ secrets.UPLOAD_KEYSTORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.UPLOAD_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.UPLOAD_KEY_PASSWORD }}
        run: ./gradlew bundleRelease -PappVersion="$VERSION" --no-configuration-cache

      # The upload steps below are third-party code and need only the bundle.
      - name: Remove upload keystore
        if: ${{ always() }}
        run: rm -f "$RUNNER_TEMP/signing.jks"
```

Run: `actionlint`
Expected: keine Ausgabe.

- [ ] **Step 4: Anleitung anpassen**

In `docs/play-store-setup.md`, Abschnitt 4, im Befehl `UPLOAD_KEYSTORE_FILE`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_PASSWORD` durch `SIGNING_KEYSTORE_FILE`, `SIGNING_KEY_ALIAS`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_PASSWORD` ersetzen (die `read`-Variablennamen ebenso). Die Secret-Namen in Abschnitt 2 bleiben.

- [ ] **Step 5: Prüfen und Commit**

Run: `./gradlew check && actionlint`
Expected: `BUILD SUCCESSFUL`, keine actionlint-Ausgabe.

```bash
git add app/build.gradle.kts .github/workflows/play-upload.yml docs/play-store-setup.md
git commit -m "build: sign the release build with whichever key SIGNING_* names"
```

### Task 4: Abhängigkeitsblock, ART-Profile, Prüfskript, Reproduzierbarkeit

**Files:**
- Create: `scripts/check-apk-signing-blocks.py`
- Modify: `app/build.gradle.kts` (`android { dependenciesInfo { … } }`, Tasks für ART-Profile)
- Modify: `CLAUDE.md` (Befehl für das Prüfskript)

**Interfaces:**
- Produces: `scripts/check-apk-signing-blocks.py <apk>` – Exit 0 ohne Block „Dependency metadata“, Exit 1 mit Meldung sonst, Exit 2 bei einer Datei ohne APK-Signaturblock.

- [ ] **Step 1: Prüfskript schreiben**

`scripts/check-apk-signing-blocks.py`:

```python
#!/usr/bin/env python3
"""Fails if an APK's signing block carries AGP's "Dependency metadata" entry.

AGP encrypts the app's dependency list with Google's public key and stores it in the APK
Signing Block (id 0x504b4453). F-Droid's scanner rejects APKs that contain it.
"""
import struct
import sys

DEPENDENCY_METADATA = 0x504B4453
SIGNING_BLOCK_MAGIC = b"APK Sig Block 42"
END_OF_CENTRAL_DIRECTORY = b"PK\x05\x06"


def signing_block_ids(data: bytes) -> list[int]:
    eocd = data.rfind(END_OF_CENTRAL_DIRECTORY)
    if eocd < 0:
        raise ValueError("not a ZIP file")
    central_directory = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[central_directory - 16:central_directory] != SIGNING_BLOCK_MAGIC:
        raise ValueError("no APK Signing Block (unsigned APK?)")
    block_size = struct.unpack_from("<Q", data, central_directory - 24)[0]
    offset = central_directory - block_size - 8 + 8
    end = central_directory - 24
    ids = []
    while offset < end:
        entry_size, entry_id = struct.unpack_from("<QI", data, offset)
        ids.append(entry_id)
        offset += 8 + entry_size
    return ids


def main() -> int:
    path = sys.argv[1]
    with open(path, "rb") as apk:
        data = apk.read()
    try:
        ids = signing_block_ids(data)
    except ValueError as error:
        print(f"{path}: {error}", file=sys.stderr)
        return 2
    if DEPENDENCY_METADATA in ids:
        print(f"{path}: contains the 'Dependency metadata' signing block F-Droid rejects", file=sys.stderr)
        return 1
    print(f"{path}: signing block ids {', '.join(hex(i) for i in ids)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

```bash
chmod +x scripts/check-apk-signing-blocks.py
```

- [ ] **Step 2: Skript muss am heutigen Build anschlagen**

Run:

```bash
S=$(mktemp -d)
keytool -genkeypair -keystore $S/t.jks -alias t -keyalg RSA -keysize 2048 -validity 1 -storepass testpass -keypass testpass -dname CN=Test >/dev/null 2>&1
SIGNING_KEYSTORE_FILE=$S/t.jks SIGNING_KEYSTORE_PASSWORD=testpass SIGNING_KEY_ALIAS=t SIGNING_KEY_PASSWORD=testpass \
  ./gradlew assembleRelease --no-configuration-cache -q
python3 -I scripts/check-apk-signing-blocks.py app/build/outputs/apk/release/app-release.apk; echo exit=$?
```

Expected: Meldung „contains the 'Dependency metadata' signing block F-Droid rejects“, `exit=1`. (Zeigt das Skript Exit 0, prüfen, ob die IDs `0x504b4453` enthalten; erst weiter, wenn es den heutigen Block erkennt.)

- [ ] **Step 3: Block abschalten und ART-Profile deaktivieren**

In `app/build.gradle.kts` innerhalb von `android { }` ergänzen:

```kotlin
    // AGP would store the dependency list, encrypted for Google, in the APK signing block; F-Droid's
    // scanner rejects that block, and the app does not need it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
```

und nach dem `android`-Block:

```kotlin
// baseline.prof is not reliably reproducible across builds (F-Droid's Reproducible Builds guide);
// for a widget the startup gain is negligible, a reproducible APK is not.
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}
```

- [ ] **Step 4: Skript muss jetzt grün sein, Profile fehlen**

Run (gleiche Variablen wie Step 2):

```bash
SIGNING_KEYSTORE_FILE=$S/t.jks SIGNING_KEYSTORE_PASSWORD=testpass SIGNING_KEY_ALIAS=t SIGNING_KEY_PASSWORD=testpass \
  ./gradlew assembleRelease --no-configuration-cache -q
python3 -I scripts/check-apk-signing-blocks.py app/build/outputs/apk/release/app-release.apk; echo exit=$?
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c "dexopt/baseline"
```

Expected: Liste der Block-IDs ohne `0x504b4453`, `exit=0`; `0` Treffer für `dexopt/baseline`.

- [ ] **Step 5: CLAUDE.md und Commit**

In `CLAUDE.md` unter „Befehle“ ergänzen:

```
python3 -I scripts/check-apk-signing-blocks.py <apk>   # F-Droid-Scanner: kein „Dependency metadata“-Block
```

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

```bash
git add scripts/check-apk-signing-blocks.py app/build.gradle.kts CLAUDE.md
git commit -m "fix: build a reproducible APK that F-Droid's scanner accepts"
```

- [ ] **Step 6: Reproduzierbarkeit lokal nachweisen (auf dem Commit aus Step 5)**

Run:

```bash
R=$(mktemp -d)
for n in 1 2; do git clone -q --no-local . "$R/c$n" && git -C "$R/c$n" checkout -q "$(git rev-parse HEAD)"; cp local.properties "$R/c$n/"; done
for n in 1 2; do (cd "$R/c$n" && ./gradlew assembleRelease --no-configuration-cache --no-build-cache -q); done
shasum -a 256 $R/c1/app/build/outputs/apk/release/app-release-unsigned.apk $R/c2/app/build/outputs/apk/release/app-release-unsigned.apk
python3 -m venv $R/venv && $R/venv/bin/pip -q install apksigcopier
SIGNING_KEYSTORE_FILE=$S/t.jks SIGNING_KEYSTORE_PASSWORD=testpass SIGNING_KEY_ALIAS=t SIGNING_KEY_PASSWORD=testpass \
  ./gradlew assembleRelease --no-configuration-cache -q
$R/venv/bin/apksigcopier compare app/build/outputs/apk/release/app-release.apk --unsigned $R/c1/app/build/outputs/apk/release/app-release-unsigned.apk && echo "signature copies onto the independent build"
rm -rf $S $R
```

Expected: beide SHA-256-Werte gleich; `apksigcopier compare` erfolgreich. Weichen die Hashes ab: mit `diffoscope` (oder `unzip` beider APKs und `diff -r`) die abweichende Datei finden und hier stoppen. Scheitert nur `apksigcopier`: Signatur im APK-Job (Task 7) mit `apksigner` aus build-tools 34 statt mit Gradle erzeugen und das im PR vermerken.

- [ ] **Step 7: PR A öffnen**

Push, PR mit Titel `fix: build a reproducible APK that F-Droid's scanner accepts`; Beschreibung nennt Tasks 1–4 und die Ergebnisse aus Task 4 Step 6. Nach grünen Checks Merge durch den Maintainer (erzeugt ein Patch-Release; Play-Upload läuft wie bisher über `-PappVersion`).

---

## PR B: Store-Metadaten (Branch `docs/fastlane-metadata`)

### Task 5: Fastlane-Struktur und „Neuerungen“ aus einer Quelle

**Files:**
- Create: `fastlane/metadata/android/{en-US,de-DE}/{title.txt,short_description.txt,full_description.txt,changelogs/default.txt}`
- Create: `fastlane/metadata/android/{en-US,de-DE}/images/{icon.png,featureGraphic.png,phoneScreenshots/1.png … 4.png}`
- Delete: `distribution/whatsnew/`
- Modify: `.github/workflows/play-upload.yml` (Schritt „Prepare release notes“, `whatsNewDirectory`)
- Modify: `CLAUDE.md`, `docs/play-store-setup.md` (Verweis auf `distribution/whatsnew/`)

**Interfaces:**
- Produces: `fastlane/metadata/android/<locale>/changelogs/default.txt` (von Task 6 kopiert, von `play-upload.yml` gelesen).

- [ ] **Step 1: Texte anlegen**

```bash
for l in en-US de-DE; do mkdir -p fastlane/metadata/android/$l/changelogs fastlane/metadata/android/$l/images/phoneScreenshots; done
printf 'Days Counter\n' > fastlane/metadata/android/en-US/title.txt
printf 'Days Counter\n' > fastlane/metadata/android/de-DE/title.txt
printf 'Countdown widget counting the days until or since any date. No ads, no tracking.\n' > fastlane/metadata/android/en-US/short_description.txt
printf 'Countdown-Widget: zählt die Tage bis zu oder seit einem Datum. Ohne Werbung.\n' > fastlane/metadata/android/de-DE/short_description.txt
git show HEAD:distribution/whatsnew/whatsnew-en-US > fastlane/metadata/android/en-US/changelogs/default.txt
git show HEAD:distribution/whatsnew/whatsnew-de-DE > fastlane/metadata/android/de-DE/changelogs/default.txt
```

`fastlane/metadata/android/en-US/full_description.txt`:

```
How many days until your vacation? How long since you met? Days Counter shows you at a glance – as a small widget right on your home screen.

How it works
• Drag the "Days Counter" widget onto your home screen
• Enter a title, pick a date, choose a color – done
• Tap the widget to change its settings at any time

Features
• Countdown or count-up: for a future date the widget shows the days left, 0 on the day itself, and the days elapsed afterwards
• As many widgets as you like, each with its own title, date and one of 12 colors
• Compact 1×1 size, styled like a calendar page
• Updates automatically shortly after midnight and when the time zone or time changes
• Follows your device's light or dark theme
• Available in English and German

Perfect for
Vacations, birthdays, weddings, anniversaries, exams, moving day, retirement, smoke-free since … – or any other date that matters to you.

Privacy
Days Counter collects no data. The app has no internet access, no ads, no tracking and needs no account. Your entries stay on your device only.

Open source
The source code is available on GitHub under the MIT license: github.com/pimorazelvantoapps/days-counter

Note: Days Counter is a widget only. After installing, you'll find it in your home screen's widget picker, not in the app drawer.
```

`fastlane/metadata/android/de-DE/full_description.txt`:

```
Wie viele Tage noch bis zum Urlaub? Seit wann seid ihr zusammen? Days Counter zeigt es dir auf einen Blick – als kleines Widget direkt auf deinem Startbildschirm.

So funktioniert's
• Widget „Days Counter“ auf den Startbildschirm ziehen
• Titel eingeben, Datum wählen, Farbe aussuchen – fertig
• Ein Tipp auf das Widget öffnet die Einstellungen erneut

Funktionen
• Countdown oder Zähler: Liegt das Datum in der Zukunft, zeigt das Widget die verbleibenden Tage, am Zieltag eine 0, danach die vergangenen Tage
• Beliebig viele Widgets, jedes mit eigenem Titel, Datum und einer von 12 Farben
• Kompaktes 1×1-Format im Stil eines Kalenderblatts
• Aktualisiert sich automatisch kurz nach Mitternacht sowie bei Zeitzonen- oder Zeitänderung
• Passt sich dem hellen oder dunklen Design deines Geräts an
• Auf Deutsch und Englisch

Ideal für
Urlaub, Geburtstag, Hochzeit, Jahrestag, Prüfung, Umzug, Renteneintritt, rauchfrei seit … – oder jedes andere Datum, das dir wichtig ist.

Privatsphäre
Days Counter sammelt keine Daten. Die App hat keinen Internetzugriff, enthält keine Werbung, kein Tracking und braucht kein Konto. Deine Einträge bleiben ausschließlich auf deinem Gerät.

Open Source
Der Quellcode ist unter der MIT-Lizenz auf GitHub verfügbar: github.com/pimorazelvantoapps/days-counter

Hinweis: Days Counter ist ein reines Widget. Nach der Installation findest du es in der Widget-Auswahl deines Startbildschirms, nicht in der App-Übersicht.
```

- [ ] **Step 2: Bilder übernehmen**

Quelle: `/private/tmp/claude-922442484/-Users-plook-Sites-android-days-counter/002583af-ca25-4cd8-9e3c-313bbdc416c1/scratchpad/store/final` (vom Maintainer gesichert, falls das Verzeichnis nicht mehr existiert – dann nach dem Pfad fragen).

```bash
F=<Quelle>
M=fastlane/metadata/android
for l in en-US de-DE; do cp $F/app-icon-512.png $M/$l/images/icon.png; done
cp $F/feature-graphic-en.png $M/en-US/images/featureGraphic.png
cp $F/feature-graphic-de.png $M/de-DE/images/featureGraphic.png
cp $F/en-1-home.png $M/en-US/images/phoneScreenshots/1.png
cp $F/en-2-setup.png $M/en-US/images/phoneScreenshots/2.png
cp $F/en-3-colors.png $M/en-US/images/phoneScreenshots/3.png
cp $F/en-4-dark.png $M/en-US/images/phoneScreenshots/4.png
cp $F/de-1-home.png $M/de-DE/images/phoneScreenshots/1.png
cp $F/de-2-einrichten.png $M/de-DE/images/phoneScreenshots/2.png
cp $F/de-3-farben.png $M/de-DE/images/phoneScreenshots/3.png
cp $F/de-4-dunkel.png $M/de-DE/images/phoneScreenshots/4.png
```

- [ ] **Step 3: Längengrenzen prüfen**

Run:

```bash
for f in fastlane/metadata/android/*/short_description.txt; do echo "$(($(wc -m < $f) - 1)) $f"; done
for f in fastlane/metadata/android/*/full_description.txt; do wc -m < $f; done
for f in fastlane/metadata/android/*/changelogs/default.txt; do wc -m < $f; done
```

Expected: Kurzbeschreibungen ≤ 80, Langbeschreibungen ≤ 4000, Changelogs ≤ 500 Zeichen.

- [ ] **Step 4: `play-upload.yml` liest die Neuerungen aus `fastlane/`**

Vor dem Schritt `google-github-actions/auth` einfügen:

```yaml
      # The upload action expects one whatsnew-<locale> file per language; fastlane holds them as
      # changelogs/default.txt, shared with F-Droid and IzzyOnDroid.
      - name: Prepare release notes
        run: |
          mkdir -p "$RUNNER_TEMP/whatsnew"
          for dir in fastlane/metadata/android/*/; do
            locale=$(basename "$dir")
            cp "$dir/changelogs/default.txt" "$RUNNER_TEMP/whatsnew/whatsnew-$locale"
          done
```

und `whatsNewDirectory: distribution/whatsnew` ersetzen durch:

```yaml
          whatsNewDirectory: ${{ runner.temp }}/whatsnew
```

Run: `git rm -r -q distribution/whatsnew && actionlint`
Expected: keine actionlint-Ausgabe.

- [ ] **Step 5: Doku anpassen**

`CLAUDE.md`, Abschnitt „Commits“, ergänzen:

```
- Store-Texte, -Bilder und „Neuerungen“ nur unter `fastlane/metadata/android/` ändern.
  `changelogs/<versionCode>.txt` und `version.properties` schreibt ausschließlich der Release-Bot.
```

`docs/play-store-setup.md`: „Neuerungen“ aus `distribution/whatsnew/` → „Neuerungen“ aus `fastlane/metadata/android/<sprache>/changelogs/default.txt`.

- [ ] **Step 6: Prüfen, Commit, PR B**

Run: `./gradlew check && actionlint`
Expected: `BUILD SUCCESSFUL`.

```bash
git add fastlane .github/workflows/play-upload.yml CLAUDE.md docs/play-store-setup.md
git add -u distribution
git commit -m "docs: keep store listing texts, images and release notes in fastlane metadata"
```

PR `docs: keep store listing texts, images and release notes in fastlane metadata` (kein Release).

---

## Einrichtung durch den Maintainer (vor PR C)

Nicht automatisierbar ohne seine Bestätigung; Befehle stehen nach Task 9 in `docs/fdroid-setup.md`, für PR C müssen sie vorher erledigt sein:

1. Verteilungsschlüssel: `keytool -genkeypair -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution -keyalg RSA -keysize 4096 -validity 9125`, `chmod 600`, im Passwortmanager sichern.
2. Secrets im Environment `play-store`: `DISTRIBUTION_KEYSTORE_BASE64`, `DISTRIBUTION_KEYSTORE_PASSWORD`, `DISTRIBUTION_KEY_ALIAS` (`distribution`), `DISTRIBUTION_KEY_PASSWORD`.
3. Deploy-Key: `ssh-keygen -t ed25519 -N "" -C days-counter-release -f ~/.keys/days-counter-release-deploy`; öffentlichen Teil mit Schreibrecht eintragen (`gh repo deploy-key add ~/.keys/days-counter-release-deploy.pub --allow-write --title release-bot`), privaten Teil als Repository-Secret `RELEASE_DEPLOY_KEY` (`gh secret set RELEASE_DEPLOY_KEY < ~/.keys/days-counter-release-deploy`).
4. Ruleset-Ausnahme für Deploy-Keys (Agent führt nach Bestätigung aus):
   `gh api repos/pimorazelvantoapps/days-counter/rulesets/<id>` lesen, `bypass_actors: [{"actor_type":"DeployKey","bypass_mode":"always"}]` setzen, per `PUT` zurückschreiben.
5. Bestätigung für Android-Entwickler: in der Play Console den Verteilungsschlüssel (SHA-256 aus `keytool -list -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution`) zur App `com.pimorazelvanto.dayscounter` hinzufügen und den Besitznachweis nach Anleitung der Console erbringen. Muss vor dem ersten APK-Release (PR C) abgeschlossen sein.

---

## PR C: Release-Pipeline (Branch `ci/release-commit`)

### Task 6: Release-Commit mit `version.properties` und Changelogs

**Files:**
- Create: `scripts/copy-release-changelogs.sh`
- Modify: `.github/release/package.json`, `.github/release/package-lock.json`
- Modify: `.releaserc.json`
- Modify: `.github/workflows/ci.yml` (Job `release`)

**Interfaces:**
- Consumes: Task `writeVersionProperties` (Task 2), `changelogs/default.txt` (Task 5).
- Produces: Release-Commit `chore(release): X.Y.Z [skip ci]` mit `version.properties` und `fastlane/metadata/android/*/changelogs/<versionCode>.txt`; Tag `vX.Y.Z` auf diesem Commit; Job-Output `version` (unverändert).

- [ ] **Step 1: Kopierskript**

`scripts/copy-release-changelogs.sh`:

```bash
#!/usr/bin/env bash
# Copies each language's changelogs/default.txt to changelogs/<versionCode>.txt, the name F-Droid
# reads, using the versionCode semantic-release just wrote to version.properties.
set -euo pipefail

code=$(sed -n 's/^versionCode=//p' version.properties)
[ -n "$code" ] || { echo "version.properties has no versionCode" >&2; exit 1; }

for dir in fastlane/metadata/android/*/; do
  source="$dir/changelogs/default.txt"
  [ -f "$source" ] || { echo "$source is missing" >&2; exit 1; }
  cp "$source" "$dir/changelogs/$code.txt"
done
```

```bash
chmod +x scripts/copy-release-changelogs.sh
```

- [ ] **Step 2: Skript prüfen, auch den Fehlerfall**

Run:

```bash
scripts/copy-release-changelogs.sh && ls fastlane/metadata/android/*/changelogs/
git clean -fq fastlane
mv fastlane/metadata/android/de-DE/changelogs/default.txt /tmp/d && scripts/copy-release-changelogs.sh; echo exit=$?; mv /tmp/d fastlane/metadata/android/de-DE/changelogs/default.txt; git clean -fq fastlane
```

Expected: je Sprache `default.txt` und `1000006.txt` (aktueller Code); im zweiten Lauf „…/de-DE/changelogs/default.txt is missing“ und `exit=1`.

- [ ] **Step 3: Plugin aufnehmen**

Run: `npm install --prefix .github/release --save-dev --save-exact @semantic-release/git@11.0.1`
Expected: `package.json` listet `"@semantic-release/git": "11.0.1"`, Lockfile aktualisiert.

- [ ] **Step 4: `.releaserc.json` erweitern**

Den `@semantic-release/exec`-Eintrag ersetzen und `@semantic-release/git` vor `@semantic-release/github` einfügen:

```json
    [
      "@semantic-release/exec",
      {
        "prepareCmd": "./gradlew --no-configuration-cache -q writeVersionProperties -PappVersion=${nextRelease.version} && scripts/copy-release-changelogs.sh",
        "publishCmd": "echo \"version=${nextRelease.version}\" >> \"$GITHUB_OUTPUT\""
      }
    ],
    [
      "@semantic-release/git",
      {
        "assets": ["version.properties", "fastlane/metadata/android/*/changelogs/*.txt"],
        "message": "chore(release): ${nextRelease.version} [skip ci]"
      }
    ],
```

Run: `python3 -I -m json.tool .releaserc.json > /dev/null && echo ok`
Expected: `ok`.

- [ ] **Step 5: Job `release` pusht über den Deploy-Key**

In `.github/workflows/ci.yml`, Job `release`, Checkout und Setup ersetzen durch:

```yaml
      # The deploy key is the only actor the main ruleset lets push directly; semantic-release uses
      # it to push the release commit that carries version.properties.
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7
        with:
          fetch-depth: 0
          ssh-key: ${{ secrets.RELEASE_DEPLOY_KEY }}
      - uses: ./.github/actions/setup-android-build
      - uses: actions/setup-node@949feb2413d6458794dcd2491c4babbbce0c15c1 # v7
        with:
          node-version: lts/*
```

und dem Schritt „Release“ die Commit-Identität mitgeben:

```yaml
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GIT_AUTHOR_NAME: github-actions[bot]
          GIT_AUTHOR_EMAIL: 41898282+github-actions[bot]@users.noreply.github.com
          GIT_COMMITTER_NAME: github-actions[bot]
          GIT_COMMITTER_EMAIL: 41898282+github-actions[bot]@users.noreply.github.com
```

Run: `actionlint`
Expected: keine Ausgabe.

- [ ] **Step 6: Trockenlauf mit echtem Commit gegen ein Wegwerf-Remote**

Run (aus dem Repo-Wurzelverzeichnis; `S` = Scratchpad):

```bash
S=$(mktemp -d)
SR=$(git rev-parse --show-toplevel)/.github/release/node_modules/.bin/semantic-release
git init -q --bare $S/remote.git
git worktree add -q -b probe-release $S/wt HEAD
git push -q $S/remote.git probe-release "$(git describe --tags --abbrev=0)"
git --git-dir=$S/remote.git symbolic-ref HEAD refs/heads/probe-release
python3 -I -c "import json,sys; c=json.load(open(sys.argv[1])); c['plugins']=[p for p in c['plugins'] if (p[0] if isinstance(p,list) else p)!='@semantic-release/github']; c['branches']=['probe-release']; json.dump(c,open(sys.argv[2],'w'))" .releaserc.json $S/wt/.releaserc.json
cp local.properties $S/wt/
(cd $S/wt && git commit -q --allow-empty -m "fix: probe" && GITHUB_OUTPUT=$S/out "$SR" --no-ci --repository-url "file://$S/remote.git" 2>&1 | grep -E "next release|Published|rror")
git --git-dir=$S/remote.git log --oneline -2 probe-release
git --git-dir=$S/remote.git show probe-release --stat | tail -4
git --git-dir=$S/remote.git describe --tags probe-release
cat $S/out
git worktree remove --force $S/wt; git branch -D -q probe-release; git tag -l | grep -vxF "$(git tag -l --merged origin/main)" | xargs -r git tag -d; rm -rf $S
```

Expected: oberster Commit `chore(release): 1.0.7 [skip ci]` (bzw. nächste Version) mit `version.properties` und zwei `changelogs/<code>.txt`; `describe` liefert genau `v1.0.7` (Tag auf dem Release-Commit); `$S/out` enthält `version=1.0.7`; danach keine lokalen Probe-Tags mehr.

- [ ] **Step 7: Commit**

```bash
git add scripts/copy-release-changelogs.sh .github/release/package.json .github/release/package-lock.json .releaserc.json .github/workflows/ci.yml
git commit -m "ci: commit version.properties and F-Droid changelogs in a release commit"
```

### Task 7: Job `apk` – signiertes APK am GitHub-Release

**Files:**
- Create: `.github/workflows/apk-release.yml`
- Modify: `.github/workflows/ci.yml` (Job `apk`)
- Modify: `.github/workflows/play-upload.yml` (`-PappVersion` entfernen)
- Modify: `app/build.gradle.kts` (Übergang `-PappVersion` entfernen)

**Interfaces:**
- Consumes: `SIGNING_*` (Task 3), `scripts/check-apk-signing-blocks.py` (Task 4), Job-Output `release.outputs.version` (Task 6).
- Produces: Release-Asset `days-counter-X.Y.Z.apk`; Workflow `apk-release.yml` mit Eingabe `version`.

- [ ] **Step 1: Wiederverwendbarer Workflow**

`.github/workflows/apk-release.yml`:

```yaml
name: APK release

# Called by the release job for every new version. Run it by hand to attach the APK to a release
# whose run failed after the tag was created.
on:
  workflow_call:
    inputs:
      version:
        required: true
        type: string
  workflow_dispatch:
    inputs:
      version:
        description: Released version without the leading v, e.g. 1.2.3
        required: true
        type: string

permissions:
  contents: write

jobs:
  apk:
    name: apk
    runs-on: ubuntu-latest
    environment: play-store
    env:
      VERSION: ${{ inputs.version }}
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7
        with:
          ref: v${{ inputs.version }}
          fetch-depth: 0
          persist-credentials: false

      # The environment's branch policy only checks the ref the run was started from, not the tag.
      - name: Refuse a tag that is not on main
        run: git merge-base --is-ancestor "v$VERSION" origin/main

      # F-Droid builds the version written in the tagged release commit; an APK of another version
      # would never match its rebuild.
      - name: Refuse a tag whose version.properties does not match
        run: grep -qx "versionName=$VERSION" version.properties

      - uses: ./.github/actions/setup-android-build

      - name: Decode distribution keystore
        env:
          DISTRIBUTION_KEYSTORE_BASE64: ${{ secrets.DISTRIBUTION_KEYSTORE_BASE64 }}
        run: |
          echo "$DISTRIBUTION_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/signing.jks"
          echo "SIGNING_KEYSTORE_FILE=$RUNNER_TEMP/signing.jks" >> "$GITHUB_ENV"

      # Without the configuration cache: it would store the key passwords on disk.
      - name: Build signed APK
        env:
          SIGNING_KEYSTORE_PASSWORD: ${{ secrets.DISTRIBUTION_KEYSTORE_PASSWORD }}
          SIGNING_KEY_ALIAS: ${{ secrets.DISTRIBUTION_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.DISTRIBUTION_KEY_PASSWORD }}
        run: ./gradlew assembleRelease --no-configuration-cache

      - name: Remove distribution keystore
        if: ${{ always() }}
        run: rm -f "$RUNNER_TEMP/signing.jks"

      - name: Check signing blocks for F-Droid
        run: python3 -I scripts/check-apk-signing-blocks.py app/build/outputs/apk/release/app-release.apk

      - name: Attach APK to the release
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          cp app/build/outputs/apk/release/app-release.apk "days-counter-$VERSION.apk"
          gh release upload "v$VERSION" "days-counter-$VERSION.apk" --clobber
```

- [ ] **Step 2: Job in `ci.yml`**

Nach dem Job `play` ergänzen:

```yaml
  # Signed with the distribution key that GitHub, IzzyOnDroid and F-Droid share
  # (docs/fdroid-setup.md); skipped until that key exists.
  apk:
    name: apk
    needs: release
    if: needs.release.outputs.version != '' && vars.DISTRIBUTION_KEY_CONFIGURED == 'true'
    permissions:
      contents: write
    uses: ./.github/workflows/apk-release.yml
    with:
      version: ${{ needs.release.outputs.version }}
    secrets: inherit
```

(Repository-Variable `DISTRIBUTION_KEY_CONFIGURED=true` setzt der Maintainer nach Einrichtungsschritt 2; Secrets sind in `if` nicht lesbar.)

- [ ] **Step 3: Übergang `-PappVersion` beenden**

`app/build.gradle.kts`: den `appVersion`-Block ersetzen durch

```kotlin
val appVersion: AppVersion =
    AppVersion.parseProperties(
        providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get(),
    )
```

`.github/workflows/play-upload.yml`: `run: ./gradlew bundleRelease -PappVersion="$VERSION" --no-configuration-cache` → `run: ./gradlew bundleRelease --no-configuration-cache`, und nach „Refuse a tag that is not on main“ denselben Schritt „Refuse a tag whose version.properties does not match“ wie in Step 1 einfügen.

`docs/play-store-setup.md`, Abschnitt 4: `-PappVersion=$VERSION` aus dem Befehl streichen und den Absatz zu `v1.0.2` ersetzen durch: „Tags vor dem ersten Release-Commit enthalten keine passende `version.properties`; die erste Version von Hand nur ab einem Tag mit Release-Commit bauen.“

- [ ] **Step 4: Prüfen**

Run: `./gradlew check && actionlint && ./gradlew assembleDebug -PappVersion=9.9.9 -q && ~/Library/Android/sdk/build-tools/37.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -1`
Expected: `BUILD SUCCESSFUL`, keine actionlint-Ausgabe, `versionName='1.0.6'` (die Property wirkt nicht mehr).

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/apk-release.yml .github/workflows/ci.yml .github/workflows/play-upload.yml app/build.gradle.kts docs/play-store-setup.md
git commit -m "ci: attach the distribution-signed APK to every GitHub release"
```

### Task 8: Nebenläufigkeit auf `main`

**Files:**
- Modify: `.github/workflows/ci.yml` (Workflow-`concurrency`, Jobs `check`, `emulator`, `connected`, `release`)

**Interfaces:**
- Produces: Concurrency-Gruppen `checks-<ref>[-<api>]` (mit Abbruch) und `release-main` (ohne Abbruch).

- [ ] **Step 1: Workflow-Gruppe nur noch für PRs**

```yaml
concurrency:
  # Pull requests cancel their older runs. On main each run is its own group, so a newer merge can
  # cancel the older run's checks (job groups below) without ever touching a release in progress.
  group: ${{ github.event_name == 'pull_request' && format('{0}-{1}', github.workflow, github.ref) || github.run_id }}
  cancel-in-progress: true
```

- [ ] **Step 2: Job-Gruppen**

Job `check`:

```yaml
    concurrency:
      group: checks-${{ github.ref }}-check
      cancel-in-progress: true
```

Job `emulator`:

```yaml
    concurrency:
      group: checks-${{ github.ref }}-emulator-${{ matrix.api-level }}
      cancel-in-progress: true
```

Job `connected` – Bedingung ersetzen, damit ein abgebrochener Emulatorlauf nicht als Fehler zählt und kein Release auslöst:

```yaml
    if: ${{ !cancelled() && needs.emulator.result != 'cancelled' }}
```

Job `release`:

```yaml
    # Never cancelled: a release that has started must finish its commit, tag and GitHub release.
    concurrency:
      group: release-main
      cancel-in-progress: false
```

Run: `actionlint`
Expected: keine Ausgabe.

- [ ] **Step 3: Commit und PR C**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: cancel superseded checks on main but never a running release"
```

PR `ci: commit the version in a release commit and attach a signed APK` erst öffnen bzw. mergen, wenn die Einrichtungsschritte 1–4 erledigt sind.

- [ ] **Step 4: Verifikation nach dem Merge (auf GitHub)**

1. Lauf auf `main` nach dem Merge von PR C: Job `release` erzeugt `chore(release): X.Y.Z [skip ci]` auf `main`; `git fetch --tags && git describe --tags origin/main` liefert `vX.Y.Z`; `play` grün; `apk` grün; Release trägt `days-counter-X.Y.Z.apk`.
2. `apksigner verify --print-certs days-counter-X.Y.Z.apk` zeigt das Verteilungszertifikat.
3. Zwei kleine PRs kurz nacheinander mergen: Der ältere Lauf zeigt `check`/`emulator` abgebrochen und `release` übersprungen, der neuere releast beide Commits in einem Release-Commit.

---

## PR D: Doku und Rezept (Branch `docs/fdroid-setup`)

### Task 9: Einrichtungsanleitung, Rezeptentwurf, README

**Files:**
- Create: `docs/fdroid-setup.md`
- Create: `docs/fdroid/com.pimorazelvanto.dayscounter.yml`
- Modify: `README.md` (Abschnitt „Releases“), `CLAUDE.md` (Hinweis auf Release-Commit in „Commits“)

- [ ] **Step 1: Rezeptentwurf**

`docs/fdroid/com.pimorazelvanto.dayscounter.yml` (Fingerabdruck trägt der Maintainer ein):

```yaml
Categories:
  - Time
License: MIT
AuthorName: Pimora Zelvanto Apps
SourceCode: https://github.com/pimorazelvantoapps/days-counter
IssueTracker: https://github.com/pimorazelvantoapps/days-counter/issues
Changelog: https://github.com/pimorazelvantoapps/days-counter/releases

AutoName: Days Counter

RepoType: git
Repo: https://github.com/pimorazelvantoapps/days-counter.git
Binaries: https://github.com/pimorazelvantoapps/days-counter/releases/download/v%v/days-counter-%v.apk

Builds:
  - versionName: 1.0.7
    versionCode: 1000007
    commit: v1.0.7
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: <SHA-256 des Verteilungszertifikats, Kleinbuchstaben ohne Doppelpunkte>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
UpdateCheckData: version.properties|versionCode=(\d+)|.|versionName=(.+)
CurrentVersion: 1.0.7
CurrentVersionCode: 1000007
```

(`versionName`/`versionCode`/`commit` beim Einreichen auf das dann aktuelle Release mit Release-Commit setzen.)

- [ ] **Step 2: Anleitung `docs/fdroid-setup.md`**

Inhalt in dieser Reihenfolge, mit den Befehlen aus „Einrichtung durch den Maintainer“ oben:

1. Verteilungsschlüssel erzeugen und sichern (Befehl, `chmod 600`, Passwortmanager).
2. Secrets `DISTRIBUTION_*` im Environment `play-store` und Variable `gh variable set DISTRIBUTION_KEY_CONFIGURED --body true`.
3. Deploy-Key erzeugen, eintragen, Secret `RELEASE_DEPLOY_KEY`.
4. Ruleset-Ausnahme für Deploy-Keys (`gh api`-Befehle).
5. Bestätigung für Android-Entwickler: Verteilungsschlüssel in der Play Console zur App hinzufügen, vor dem ersten APK-Release. Begründung: ohne Registrierung verweigern zertifizierte Geräte in den betroffenen Ländern die Installation; registrierbar nur, solange F-Droid unsere Signatur übernimmt.
6. Fingerabdruck: `keytool -list -v -keystore ~/.keys/days-counter-distribution.jks -alias distribution | grep SHA256` → Doppelpunkte entfernen, klein schreiben, in `AllowedAPKSigningKeys` eintragen.
7. IzzyOnDroid: Issue unter `https://codeberg.org/IzzyOnDroid/repo/issues` mit Vorlage:

```
App: Days Counter (com.pimorazelvanto.dayscounter)
Source: https://github.com/pimorazelvantoapps/days-counter (MIT)
APKs: attached to every GitHub release as days-counter-X.Y.Z.apk, signed with the same key F-Droid will use (reproducible builds)
Metadata: fastlane/metadata/android (en-US, de-DE)
No trackers, no network permission.
```

8. F-Droid: Fork von `https://gitlab.com/fdroid/fdroiddata`, Rezept nach `metadata/com.pimorazelvanto.dayscounter.yml`, lokal `fdroid readmeta`, `fdroid lint com.pimorazelvanto.dayscounter`, `fdroid build -v -l com.pimorazelvanto.dayscounter` (optional), Merge Request.
9. Hinweis: Play-Store-Installationen und F-Droid/IzzyOnDroid/GitHub-Installationen tragen verschiedene Signaturen; ein Wechsel erfordert eine Neuinstallation.

- [ ] **Step 3: README und CLAUDE.md**

`README.md`, Abschnitt „Releases“, ergänzen:

```
Außerhalb des Play Stores gibt es die App bei IzzyOnDroid und F-Droid sowie als APK an jedem
GitHub-Release. Diese drei teilen sich eine eigene Signatur und lassen sich untereinander
aktualisieren; Installationen aus dem Play Store tragen Googles Signatur, ein Wechsel erfordert
eine Neuinstallation. Einrichtung: [`docs/fdroid-setup.md`](docs/fdroid-setup.md).
```

`CLAUDE.md`, Abschnitt „Commits“, ergänzen:

```
- Nach jedem Release liegt ein Commit `chore(release): X.Y.Z [skip ci]` des Bots auf `main`;
  Branches vor dem Merge darauf rebasen.
```

- [ ] **Step 4: Prüfen, Commit, PR D**

Run: `./gradlew check`
Expected: `BUILD SUCCESSFUL`.

```bash
git add docs/fdroid-setup.md docs/fdroid README.md CLAUDE.md
git commit -m "docs: describe the F-Droid and IzzyOnDroid setup and draft the F-Droid recipe"
```

PR `docs: describe the F-Droid and IzzyOnDroid setup and draft the F-Droid recipe` (kein Release).
