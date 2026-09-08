# Days Counter Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein 1x1-Android-Homescreen-Widget, das pro Instanz die Tage bis zu einem Zieldatum zählt, mit Konfigurationsdialog, exakter Mitternachtsaktualisierung und vollständiger Test- und Analyse-Pipeline.

**Architecture:** Ein Gradle-Modul `app` mit vier Packages und fester Abhängigkeitsrichtung: `domain` (reine Kotlin-Logik, kennt nichts) ← `data` (DataStore-Repository) ← `widget` (Glance) und `config` (Compose), die sich gegenseitig nicht kennen. Konfiguration schreibt in den Speicher und stößt ein Widget-Update an; das Widget liest. Abhängigkeiten werden ohne DI-Framework in `AppContainer` manuell konstruiert; Tests tauschen den Container gegen Fakes aus.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.0 (built-in Kotlin), Gradle 9.6.0, Jetpack Glance 1.2.0, Compose BOM 2026.08.00 (Material 3 1.4.0), Preferences DataStore 1.2.1, JUnit 5.14.1 + android-junit5 2.0.1, Robolectric 4.16.1, Compose UI Test, ktlint 1.8.0 (ktlint-gradle 14.2.0), detekt 1.23.8 + compose-rules 0.4.23, Android Lint.

**Spec:** `docs/superpowers/specs/2026-09-08-days-counter-widget-design.md`

## Global Constraints

- `minSdk = 33`, `compileSdk = 36`, `targetSdk = 36`. Package `com.pimorazelvanto.dayscounter`.
- Kotlin Official Code Style (`kotlin.code.style=official`, ktlint `ktlint_official`). `allWarningsAsErrors = true` im Kotlin-Compiler. Lint: `warningsAsErrors = true`, `abortOnError = true`. detekt: Standardkonfiguration plus Compose-Regeln, leere Baseline, keine aufgeweichten Schwellwerte.
- `domain` hat keine Android-Abhängigkeit. Zeit ausschließlich über `Clock`, niemals `LocalDate.now()` außerhalb von `SystemClock`.
- Kein DI-Framework, kein Multi-Modul, kein WorkManager, kein `updatePeriodMillis`, kein Launcher-Eintrag.
- Anzeige: Zahl bei `diff > 0`, `"0"` bei `diff == 0`, `"-"` bei `diff < 0`; Platzhalter ohne Konfiguration zeigt `"–"` (Gedankenstrich U+2013).
- Zieldatum bei Eingabe strikt `> today`. Titel max. 20 Zeichen. Standardtitel „Tage“ (de) / „days“ (en). Standardfarbe `HeaderColor.RED`.
- 12 Farben in fester Reihenfolge: RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, INDIGO, PURPLE, PINK, BROWN, GREY, BLACK.
- Exakter Alarm über `setExactAndAllowWhileIdle(RTC_WAKEUP, …)`, Berechtigung `USE_EXACT_ALARM`. Keine Fallbacks.
- Alle Strings in `values/strings.xml` (en) und `values-de/strings.xml`; kein hartkodierter UI-Text.
- Dokumentationsregeln aus `CLAUDE.md`: sprechende Namen statt Kommentare, nichts doppelt dokumentieren, Testkommentare nur zur Testlogik.
- Commit-Format: `<type>: <Beschreibung>` (feat, test, build, docs, refactor), englisch, Imperativ. Git-Identität vor dem ersten Commit prüfen (`git config user.name`); falls leer, mit `git config user.name "Phillip Look"` und `git config user.email "pimora.zelvanto.apps@gmail.com"` setzen.
- Jeder Task endet mit grünem `./gradlew check` (Emulator-Tasks zusätzlich `connectedCheck`), dann Commit.

## Umgebung

- JDK 21 (Temurin) unter `~/.jenv/versions/21`; `JAVA_HOME` ist gesetzt.
- Android SDK unter `~/Library/Android/sdk` (Platforms 34–36.1, Build-Tools 36.0.0). `ANDROID_HOME` ist **nicht** gesetzt, deshalb `local.properties` mit `sdk.dir` anlegen (Task 1).
- Ein AVD existiert: `Pixel_3a_API_34_extension_level_7_arm64-v8a`. Start für Emulator-Tasks:
  `~/Library/Android/sdk/emulator/emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a -no-snapshot-load &` und warten bis `adb shell getprop sys.boot_completed` `1` liefert.
- Robolectric lädt beim ersten Testlauf `android-all`-Jars aus dem Netz (einige hundert MB); Netzwerk nötig.

## Dateistruktur

```
days_counter/
├── settings.gradle.kts
├── build.gradle.kts                      Root: Plugins apply false, detekt/ktlint für alle Module
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/…                      Gradle 9.6.0
├── local.properties                      sdk.dir (gitignored)
├── .gitignore  .editorconfig
├── config/detekt/detekt.yml              nur Compose-Regeln aktiviert, Rest Default
├── README.md
└── app/
    ├── build.gradle.kts
    ├── lint.xml
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/pimorazelvanto/dayscounter/
        │   │   ├── DaysCounterApplication.kt
        │   │   ├── AppContainer.kt          interface AppContainer, DefaultAppContainer, Context.appContainer
        │   │   ├── domain/ Clock.kt SystemClock.kt DisplayValue.kt DaysCalculator.kt
        │   │   │           DigitSizeTier.kt TargetDateValidator.kt HeaderColor.kt
│   │   │           WidgetConfig.kt WidgetUiState.kt
        │   │   ├── data/   WidgetConfigRepository.kt DataStoreWidgetConfigRepository.kt
        │   │   ├── widget/ WidgetUpdater.kt GlanceWidgetUpdater.kt
        │   │   │           MidnightUpdateScheduler.kt AlarmManagerMidnightUpdateScheduler.kt
        │   │   │           DateChangeReceiver.kt DaysCounterWidgetContent.kt
        │   │   │           DaysCounterWidget.kt DaysCounterWidgetReceiver.kt
        │   │   └── config/ ConfigUiState.kt ConfigViewModel.kt ConfigViewModelFactory.kt
        │   │               FutureOnlySelectableDates.kt WidgetPreview.kt ColorPickerDialog.kt
        │   │               ConfigScreen.kt ConfigActivity.kt
        │   └── res/
        │       ├── values/strings.xml colors.xml dimens.xml themes.xml
        │       ├── values-de/strings.xml
        │       ├── values-night/colors.xml
        │       ├── xml/days_counter_widget_info.xml locales_config.xml
        │       ├── layout/widget_preview.xml
        │       └── drawable/ic_launcher_foreground.xml  mipmap-anydpi/ic_launcher.xml
        ├── test/java/com/pimorazelvanto/dayscounter/        JUnit 5 (domain, config) + Robolectric/JUnit 4 (data, widget)
        │   ├── domain/…Test.kt
        │   ├── data/DataStoreWidgetConfigRepositoryTest.kt
        │   ├── widget/AlarmManagerMidnightUpdateSchedulerTest.kt DateChangeReceiverTest.kt
        │   │          DaysCounterWidgetContentTest.kt
        │   ├── config/ConfigViewModelTest.kt
        │   └── testsupport/ FixedClock.kt FakeWidgetConfigRepository.kt FakeWidgetUpdater.kt
        │                    FakeMidnightUpdateScheduler.kt FakeAppContainer.kt
        └── androidTest/java/com/pimorazelvanto/dayscounter/
            ├── config/ConfigActivityTest.kt
            └── widget/DaysCounterWidgetRenderTest.kt
```

Testhilfen liegen in `src/test/java/com/pimorazelvanto/dayscounter/testsupport/`. Die `androidTest`-Quellen brauchen `FixedClock` ebenfalls; damit nichts doppelt gepflegt wird, legt Task 7 den Ordner `src/sharedTest/java` an und bindet ihn in beide Test-Source-Sets ein.

---

### Task 1: Projektgerüst mit Qualitätspipeline

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `local.properties`, `.gitignore`, `.editorconfig`, `config/detekt/detekt.yml`, `app/build.gradle.kts`, `app/lint.xml`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`, `app/src/main/res/xml/locales_config.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/values/colors.xml`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/BuildSmokeTest.kt` (wird in Task 2 wieder gelöscht)

**Interfaces:**
- Produces: lauffähiges `./gradlew check` mit ktlint, detekt, Lint, JUnit 5 und Vintage-Engine; Version-Catalog-Aliase, die alle späteren Tasks verwenden (`libs.androidx.glance.appwidget`, `libs.junit.jupiter`, …).

- [ ] **Step 1: Git-Identität und .gitignore**

```bash
git config user.name || git config user.name "Phillip Look"
git config user.email || git config user.email "pimora.zelvanto.apps@gmail.com"
```

`.gitignore`:
```
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build
/app/build
/captures
.externalNativeBuild
.cxx
*.apk
*.ap_
*.aab
/app/release
```

- [ ] **Step 2: settings.gradle.kts, gradle.properties, local.properties**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DaysCounter"
include(":app")
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```

`local.properties`:
```properties
sdk.dir=/Users/<user>/Library/Android/sdk
```

- [ ] **Step 3: Gradle-Wrapper 9.6.0 erzeugen**

```bash
/opt/homebrew/bin/gradle wrapper --gradle-version 9.6.0 --distribution-type bin
./gradlew --version
```
Erwartet: Ausgabe zeigt `Gradle 9.6.0`.

- [ ] **Step 4: Version Catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "9.4.0"
kotlin = "2.4.20"
composeBom = "2026.08.00"
glance = "1.2.0"
activityCompose = "1.13.0"
lifecycleViewmodel = "2.11.0"
datastore = "1.2.1"
coreKtx = "1.19.0"
coroutines = "1.11.0"
junitBom = "5.14.1"
androidJunit5 = "2.0.1"
junit4 = "4.13.2"
robolectric = "4.16.1"
androidxTestCore = "1.7.0"
androidxTestRunner = "1.7.0"
androidxTestRules = "1.7.0"
androidxTestExtJunit = "1.3.0"
detekt = "1.23.8"
detektComposeRules = "0.4.23"
ktlintGradle = "14.2.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycleViewmodel" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
androidx-glance-material3 = { group = "androidx.glance", name = "glance-material3", version.ref = "glance" }
androidx-glance-testing = { group = "androidx.glance", name = "glance-testing", version.ref = "glance" }
androidx-glance-appwidget-testing = { group = "androidx.glance", name = "glance-appwidget-testing", version.ref = "glance" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit-bom = { group = "org.junit", name = "junit-bom", version.ref = "junitBom" }
junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api" }
junit-jupiter-params = { group = "org.junit.jupiter", name = "junit-jupiter-params" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine" }
junit-vintage-engine = { group = "org.junit.vintage", name = "junit-vintage-engine" }
junit-platform-launcher = { group = "org.junit.platform", name = "junit-platform-launcher" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core-ktx = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidxTestRunner" }
androidx-test-rules = { group = "androidx.test", name = "rules", version.ref = "androidxTestRules" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExtJunit" }
detekt-compose-rules = { group = "io.nlopez.compose.rules", name = "detekt", version.ref = "detektComposeRules" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-junit5 = { id = "de.mannodermaus.android-junit", version.ref = "androidJunit5" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintGradle" }
```

Hinweis: AGP 9 bringt Kotlin eingebaut mit (KGP 2.2.10). Das Compose-Compiler-Plugin 2.4.20 zieht `kotlin-gradle-plugin:2.4.20` transitiv nach, Gradle löst den Konflikt zur höheren Version auf. `org.jetbrains.kotlin.android` wird **nicht** angewendet.

- [ ] **Step 5: Root build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.junit5) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint) apply false
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom(files("app/src/main/java", "app/src/test/java", "app/src/androidTest/java", "app/src/sharedTest/java"))
    parallel = true
}

dependencies {
    detektPlugins(libs.detekt.compose.rules)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}
```

`config/detekt/detekt.yml` (nur gezielte Ergänzungen zur Default-Konfiguration; Compose-Funktionen haben naturgemäß viele Parameter, und `sharedTest` ist ein Testordner, den detekt nicht kennt):
```yaml
Compose:
  active: true

complexity:
  LongParameterList:
    ignoreAnnotated:
      - Composable

style:
  MagicNumber:
    excludes:
      - "**/test/**"
      - "**/androidTest/**"
      - "**/sharedTest/**"
      - "**/commonTest/**"
      - "**/jvmTest/**"
      - "**/androidUnitTest/**"
      - "**/androidInstrumentedTest/**"
```

`.editorconfig`:
```
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 4
trim_trailing_whitespace = true

[*.{kt,kts}]
ktlint_code_style = ktlint_official
ktlint_function_naming_ignore_when_annotated_with = Composable
max_line_length = 120

[*.{yml,yaml,toml}]
indent_size = 2
```

- [ ] **Step 6: app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.pimorazelvanto.dayscounter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pimorazelvanto.dayscounter"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("test").kotlin.directories += "src/sharedTest/java"
        getByName("androidTest").kotlin.directories += "src/sharedTest/java"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
        lintConfig = file("lint.xml")
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

ktlint {
    version.set("1.8.0")
    outputToConsole.set(true)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.glance.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Die Zeile `packaging.resources.excludes` verhindert doppelte `META-INF/LICENSE.md` aus JUnit-Jars im Instrumentation-APK.

`app/lint.xml` (einzelne Checks werden gezielt und begründet abgeschaltet, nicht pauschal):
```xml
<?xml version="1.0" encoding="utf-8"?>
<lint>
    <!-- Versionsupdates sind bewusste Entscheidungen; ein neues Release darf den Build nicht brechen -->
    <issue id="GradleDependency" severity="ignore" />
    <issue id="NewerVersionAvailable" severity="ignore" />
    <issue id="AndroidGradlePluginVersion" severity="ignore" />
</lint>
```

- [ ] **Step 7: Manifest, Strings, Locale-Config, Icon**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".DaysCounterApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:localeConfig="@xml/locales_config"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
    </application>

</manifest>
```
Die Klasse `DaysCounterApplication` entsteht in Task 7; bis dahin legt dieser Task eine leere Klasse an:
```kotlin
package com.pimorazelvanto.dayscounter

import android.app.Application

class DaysCounterApplication : Application()
```

`app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Days Counter</string>
</resources>
```

`app/src/main/res/values-de/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Tage-Zähler</string>
</resources>
```
Weitere Strings kommen erst mit dem Code, der sie benutzt; sonst schlägt Lint `UnusedResources` als Fehler an.

`app/src/main/res/xml/locales_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
    <locale android:name="en" />
    <locale android:name="de" />
</locale-config>
```

`app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launcher_background">#D32F2F</color>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (weißes Kalenderblatt als Vektor):
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M30,30 h48 a6,6 0 0 1 6,6 v42 a6,6 0 0 1 -6,6 h-48 a6,6 0 0 1 -6,-6 v-42 a6,6 0 0 1 6,-6 z" />
    <path
        android:fillColor="#B71C1C"
        android:pathData="M30,30 h48 a6,6 0 0 1 6,6 v12 h-60 v-12 a6,6 0 0 1 6,-6 z" />
</vector>
```

`app/src/main/res/mipmap-anydpi/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 8: Smoke-Test schreiben, der die Test-Pipeline beweist**

`app/src/test/java/com/pimorazelvanto/dayscounter/BuildSmokeTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildSmokeTest {
    @Test
    fun `junit platform runs jupiter tests`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 9: check ausführen**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`, im Log erscheinen `ktlintMainSourceSetCheck`, `detekt`, `lintDebug`, `testDebugUnitTest`. Bei ktlint-Fehlern zuerst `./gradlew ktlintFormat`, dann erneut.

Falls das detekt-Plugin 1.23.8 unter Gradle 9.6 mit einem Konfigurationsfehler abbricht (nicht mit Regelverstößen), wechsle auf detekt 2: im Catalog `detekt = "2.0.0-alpha.6"`, `detektComposeRules = "0.6.6"`, Plugin-ID `dev.detekt`, Task-Typ `dev.detekt.gradle.Detekt`, und `jvmTarget`-Zeile entfernen. Halte diese Änderung im Commit-Text fest.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "build: scaffold Android project with lint, ktlint, detekt and JUnit 5"
```

---

### Task 2: Domäne – Clock, DisplayValue, DaysCalculator

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/domain/Clock.kt`, `SystemClock.kt`, `DisplayValue.kt`, `DaysCalculator.kt`
- Create: `app/src/sharedTest/java/com/pimorazelvanto/dayscounter/testsupport/FixedClock.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/domain/DaysCalculatorTest.kt`, `DisplayValueTest.kt`
- Delete: `app/src/test/java/com/pimorazelvanto/dayscounter/BuildSmokeTest.kt`

**Interfaces:**
- Produces:
  - `interface Clock { fun today(): LocalDate; fun zone(): ZoneId }`
  - `class SystemClock : Clock`
  - `sealed interface DisplayValue { val text: String }` mit `data class Days(val count: Int)`, `data object Reached`, `data object Passed`
  - `object DaysCalculator { fun calculate(today: LocalDate, target: LocalDate): DisplayValue }`
  - `class FixedClock(private val fixedToday: LocalDate, private val fixedZone: ZoneId = ZoneId.of("Europe/Berlin")) : Clock`

- [ ] **Step 1: Fehlschlagende Tests für DaysCalculator und DisplayValue schreiben**

`app/src/test/java/com/pimorazelvanto/dayscounter/domain/DaysCalculatorTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DaysCalculatorTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `target tomorrow counts as one full day`() {
        assertEquals(DisplayValue.Days(1), DaysCalculator.calculate(today, today.plusDays(1)))
    }

    @Test
    fun `target today is reached`() {
        assertEquals(DisplayValue.Reached, DaysCalculator.calculate(today, today))
    }

    @Test
    fun `target yesterday is passed`() {
        assertEquals(DisplayValue.Passed, DaysCalculator.calculate(today, today.minusDays(1)))
    }

    @Test
    fun `counts across year boundary`() {
        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals(DisplayValue.Days(115), DaysCalculator.calculate(today, newYear))
    }

    @Test
    fun `counts leap day when crossing february 29`() {
        val beforeLeapDay = LocalDate.of(2028, 2, 28)
        val afterLeapDay = LocalDate.of(2028, 3, 1)
        assertEquals(DisplayValue.Days(2), DaysCalculator.calculate(beforeLeapDay, afterLeapDay))
    }

    @Test
    fun `counts distances of several years`() {
        assertEquals(DisplayValue.Days(3653), DaysCalculator.calculate(today, today.plusYears(10)))
    }
}
```

`app/src/test/java/com/pimorazelvanto/dayscounter/domain/DisplayValueTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisplayValueTest {
    @Test
    fun `days render as plain number`() {
        assertEquals("42", DisplayValue.Days(42).text)
    }

    @Test
    fun `reached renders as zero`() {
        assertEquals("0", DisplayValue.Reached.text)
    }

    @Test
    fun `passed renders as hyphen`() {
        assertEquals("-", DisplayValue.Passed.text)
    }
}
```

- [ ] **Step 2: Tests ausführen, Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: Kompilierfehler `Unresolved reference 'DisplayValue'`.

- [ ] **Step 3: Implementierung**

`Clock.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun today(): LocalDate

    fun zone(): ZoneId
}
```

`SystemClock.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate
import java.time.ZoneId

class SystemClock : Clock {
    override fun today(): LocalDate = LocalDate.now(zone())

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
```

`DisplayValue.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.domain

sealed interface DisplayValue {
    val text: String

    data class Days(val count: Int) : DisplayValue {
        override val text: String get() = count.toString()
    }

    data object Reached : DisplayValue {
        override val text: String = "0"
    }

    data object Passed : DisplayValue {
        override val text: String = "-"
    }
}
```

`DaysCalculator.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DaysCalculator {
    fun calculate(today: LocalDate, target: LocalDate): DisplayValue {
        val remainingDays = ChronoUnit.DAYS.between(today, target).toInt()
        return when {
            remainingDays > 0 -> DisplayValue.Days(remainingDays)
            remainingDays == 0 -> DisplayValue.Reached
            else -> DisplayValue.Passed
        }
    }
}
```

`app/src/sharedTest/java/com/pimorazelvanto/dayscounter/testsupport/FixedClock.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.domain.Clock
import java.time.LocalDate
import java.time.ZoneId

class FixedClock(
    private val fixedToday: LocalDate,
    private val fixedZone: ZoneId = ZoneId.of("Europe/Berlin"),
) : Clock {
    override fun today(): LocalDate = fixedToday

    override fun zone(): ZoneId = fixedZone
}
```

`BuildSmokeTest.kt` löschen.

- [ ] **Step 4: Tests und check ausführen**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`, 9 Tests bestanden. Ein detekt-Hinweis auf `FixedClock` als ungenutzt ist nicht zu erwarten, da detekt nur `UnusedPrivateMember` prüft.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add day counting domain logic with injectable clock"
```

---

### Task 3: Domäne – DigitSizeTier

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/domain/DigitSizeTier.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/domain/DigitSizeTierTest.kt`

**Interfaces:**
- Consumes: `DisplayValue.text`
- Produces: `enum class DigitSizeTier { LARGE, MEDIUM, SMALL }` und `fun DisplayValue.sizeTier(): DigitSizeTier`

- [ ] **Step 1: Fehlschlagender Test**

```kotlin
package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DigitSizeTierTest {
    @ParameterizedTest(name = "{0} days -> {1}")
    @CsvSource(
        "1, LARGE",
        "99, LARGE",
        "100, MEDIUM",
        "999, MEDIUM",
        "1000, SMALL",
        "36500, SMALL",
    )
    fun `tier depends on digit count`(days: Int, expected: DigitSizeTier) {
        assertEquals(expected, DisplayValue.Days(days).sizeTier())
    }

    @ParameterizedTest(name = "{0} uses LARGE")
    @CsvSource("Reached", "Passed")
    fun `reached and passed use large tier`(name: String) {
        val value = if (name == "Reached") DisplayValue.Reached else DisplayValue.Passed
        assertEquals(DigitSizeTier.LARGE, value.sizeTier())
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'sizeTier'`.

- [ ] **Step 3: Implementierung**

```kotlin
package com.pimorazelvanto.dayscounter.domain

enum class DigitSizeTier {
    LARGE,
    MEDIUM,
    SMALL,
}

private const val MAX_LENGTH_LARGE = 2
private const val MAX_LENGTH_MEDIUM = 3

fun DisplayValue.sizeTier(): DigitSizeTier =
    when {
        text.length <= MAX_LENGTH_LARGE -> DigitSizeTier.LARGE
        text.length <= MAX_LENGTH_MEDIUM -> DigitSizeTier.MEDIUM
        else -> DigitSizeTier.SMALL
    }
```

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: derive widget digit size tier from display value"
```

---

### Task 4: Domäne – TargetDateValidator

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/domain/TargetDateValidator.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/domain/TargetDateValidatorTest.kt`

**Interfaces:**
- Produces: `sealed interface ValidationResult { data object Valid; data object NotInFuture }` und `object TargetDateValidator { fun validate(today: LocalDate, candidate: LocalDate): ValidationResult }`

- [ ] **Step 1: Fehlschlagender Test**

```kotlin
package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TargetDateValidatorTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `tomorrow is valid`() {
        assertEquals(ValidationResult.Valid, TargetDateValidator.validate(today, today.plusDays(1)))
    }

    @Test
    fun `today is not in future`() {
        assertEquals(ValidationResult.NotInFuture, TargetDateValidator.validate(today, today))
    }

    @Test
    fun `yesterday is not in future`() {
        assertEquals(ValidationResult.NotInFuture, TargetDateValidator.validate(today, today.minusDays(1)))
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'ValidationResult'`.

- [ ] **Step 3: Implementierung**

```kotlin
package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data object NotInFuture : ValidationResult
}

object TargetDateValidator {
    fun validate(today: LocalDate, candidate: LocalDate): ValidationResult =
        if (candidate.isAfter(today)) ValidationResult.Valid else ValidationResult.NotInFuture
}
```

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: validate that target dates lie strictly in the future"
```

---

### Task 5: Domäne – HeaderColor

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/domain/HeaderColor.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/domain/HeaderColorTest.kt`

**Interfaces:**
- Produces: `enum class HeaderColor(val argb: Long)` mit 12 Einträgen in Rasterreihenfolge, `HeaderColor.DEFAULT == RED`, `HeaderColor.fromName(name: String): HeaderColor?`

- [ ] **Step 1: Fehlschlagender Test**

```kotlin
package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class HeaderColorTest {
    @Test
    fun `palette has twelve colors in grid order`() {
        val expected = listOf(
            "RED", "ORANGE", "YELLOW",
            "GREEN", "TEAL", "BLUE",
            "INDIGO", "PURPLE", "PINK",
            "BROWN", "GREY", "BLACK",
        )
        assertEquals(expected, HeaderColor.entries.map { it.name })
    }

    @Test
    fun `default color is red`() {
        assertEquals(HeaderColor.RED, HeaderColor.DEFAULT)
    }

    @ParameterizedTest
    @EnumSource(HeaderColor::class)
    fun `name round trips through fromName`(color: HeaderColor) {
        assertEquals(color, HeaderColor.fromName(color.name))
    }

    @ParameterizedTest
    @EnumSource(HeaderColor::class)
    fun `every color is fully opaque`(color: HeaderColor) {
        assertTrue(color.argb ushr 24 == 0xFFL, "alpha byte of ${color.name} must be FF")
    }

    @Test
    fun `unknown name yields null instead of throwing`() {
        assertNull(HeaderColor.fromName("MAUVE"))
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'HeaderColor'`.

- [ ] **Step 3: Implementierung**

```kotlin
package com.pimorazelvanto.dayscounter.domain

enum class HeaderColor(val argb: Long) {
    RED(0xFFD32F2F),
    ORANGE(0xFFF57C00),
    YELLOW(0xFFFFA000),
    GREEN(0xFF388E3C),
    TEAL(0xFF00897B),
    BLUE(0xFF1976D2),
    INDIGO(0xFF3949AB),
    PURPLE(0xFF7B1FA2),
    PINK(0xFFC2185B),
    BROWN(0xFF6D4C41),
    GREY(0xFF616161),
    BLACK(0xFF212121),
    ;

    companion object {
        val DEFAULT: HeaderColor = RED

        fun fromName(name: String): HeaderColor? = entries.firstOrNull { it.name == name }
    }
}
```

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`. Falls detekt `MagicNumber` für die Farbwerte meldet, ist das ein echter Regelkonflikt mit Enum-Konstanten; unterdrücke gezielt mit `@Suppress("MagicNumber")` an der Enum-Klasse, nicht global.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add twelve-color header palette with stable persistence names"
```

---

### Task 6: Daten – WidgetConfig und DataStore-Repository

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/domain/WidgetConfig.kt`, `app/src/main/java/com/pimorazelvanto/dayscounter/data/WidgetConfigRepository.kt`, `DataStoreWidgetConfigRepository.kt`
- Create: `app/src/sharedTest/java/com/pimorazelvanto/dayscounter/testsupport/FakeWidgetConfigRepository.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/data/DataStoreWidgetConfigRepositoryTest.kt`

**Interfaces:**
- Consumes: `HeaderColor`, `HeaderColor.fromName`
- Produces:
  - `data class WidgetConfig(val title: String, val targetDate: LocalDate, val color: HeaderColor)`
  - `interface WidgetConfigRepository { suspend fun load(appWidgetId: Int): WidgetConfig?; suspend fun save(appWidgetId: Int, config: WidgetConfig); suspend fun delete(appWidgetId: Int) }`
  - `class DataStoreWidgetConfigRepository(private val dataStore: DataStore<Preferences>) : WidgetConfigRepository`
  - `class FakeWidgetConfigRepository : WidgetConfigRepository` mit `val saved: MutableMap<Int, WidgetConfig>` und `var failOnSave: Boolean`

- [ ] **Step 1: Fehlschlagender Robolectric-Test (JUnit 4, läuft über Vintage)**

Der Test schreibt in eine echte DataStore-Datei im Temp-Ordner; Robolectric wird nur für den `Context`-freien Dateizugriff nicht benötigt, aber DataStore braucht die Android-Log-Klasse, daher `RobolectricTestRunner`.

```kotlin
package com.pimorazelvanto.dayscounter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DataStoreWidgetConfigRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreWidgetConfigRepository

    private val config = WidgetConfig("Urlaub", LocalDate.of(2027, 3, 15), HeaderColor.BLUE)

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            temporaryFolder.newFile("configs.preferences_pb")
        }
        repository = DataStoreWidgetConfigRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `save then load returns same config`() = runBlocking {
        repository.save(7, config)

        assertEquals(config, repository.load(7))
    }

    @Test
    fun `load of unknown id returns null`() = runBlocking {
        assertNull(repository.load(99))
    }

    @Test
    fun `configs of different ids are isolated`() = runBlocking {
        val other = config.copy(title = "Geburtstag", color = HeaderColor.PINK)
        repository.save(1, config)
        repository.save(2, other)

        assertEquals(config, repository.load(1))
        assertEquals(other, repository.load(2))
    }

    @Test
    fun `delete removes only the given id`() = runBlocking {
        repository.save(1, config)
        repository.save(2, config)

        repository.delete(1)

        assertNull(repository.load(1))
        assertEquals(config, repository.load(2))
    }

    @Test
    fun `corrupt date yields null`() = runBlocking {
        repository.save(5, config)
        dataStore.edit { it[stringPreferencesKey("target_date_5")] = "not-a-date" }

        assertNull(repository.load(5))
    }

    @Test
    fun `unknown color name yields null`() = runBlocking {
        repository.save(5, config)
        dataStore.edit { it[stringPreferencesKey("color_5")] = "MAUVE" }

        assertNull(repository.load(5))
    }

    @Test
    fun `missing title yields null`() = runBlocking {
        repository.save(5, config)
        dataStore.edit { it.remove(stringPreferencesKey("title_5")) }

        assertNull(repository.load(5))
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'WidgetConfig'`.

- [ ] **Step 3: Implementierung**

`WidgetConfig.kt` (Package `domain`, weil die Klasse ein reines Wertobjekt ohne Persistenzbezug ist):
```kotlin
package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate

data class WidgetConfig(
    val title: String,
    val targetDate: LocalDate,
    val color: HeaderColor,
)
```

`WidgetConfigRepository.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.data

import com.pimorazelvanto.dayscounter.domain.WidgetConfig

interface WidgetConfigRepository {
    suspend fun load(appWidgetId: Int): WidgetConfig?

    suspend fun save(appWidgetId: Int, config: WidgetConfig)

    suspend fun delete(appWidgetId: Int)
}
```

`DataStoreWidgetConfigRepository.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeParseException

class DataStoreWidgetConfigRepository(
    private val dataStore: DataStore<Preferences>,
) : WidgetConfigRepository {
    override suspend fun load(appWidgetId: Int): WidgetConfig? {
        val preferences = dataStore.data.first()
        val keys = Keys(appWidgetId)
        val title = preferences[keys.title] ?: return null
        val targetDate = preferences[keys.targetDate]?.let(::parseDateOrNull) ?: return null
        val color = preferences[keys.color]?.let(HeaderColor::fromName) ?: return null
        return WidgetConfig(title, targetDate, color)
    }

    override suspend fun save(appWidgetId: Int, config: WidgetConfig) {
        val keys = Keys(appWidgetId)
        dataStore.edit { preferences ->
            preferences[keys.title] = config.title
            preferences[keys.targetDate] = config.targetDate.toString()
            preferences[keys.color] = config.color.name
        }
    }

    override suspend fun delete(appWidgetId: Int) {
        val keys = Keys(appWidgetId)
        dataStore.edit { preferences ->
            preferences.remove(keys.title)
            preferences.remove(keys.targetDate)
            preferences.remove(keys.color)
        }
    }

    private fun parseDateOrNull(isoDate: String): LocalDate? =
        try {
            LocalDate.parse(isoDate)
        } catch (_: DateTimeParseException) {
            null
        }

    private class Keys(appWidgetId: Int) {
        val title = stringPreferencesKey("title_$appWidgetId")
        val targetDate = stringPreferencesKey("target_date_$appWidgetId")
        val color = stringPreferencesKey("color_$appWidgetId")
    }
}
```

`FakeWidgetConfigRepository.kt` (sharedTest):
```kotlin
package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import java.io.IOException

class FakeWidgetConfigRepository : WidgetConfigRepository {
    val saved = mutableMapOf<Int, WidgetConfig>()
    var failOnSave = false

    override suspend fun load(appWidgetId: Int): WidgetConfig? = saved[appWidgetId]

    override suspend fun save(appWidgetId: Int, config: WidgetConfig) {
        if (failOnSave) throw IOException("simulated write failure")
        saved[appWidgetId] = config
    }

    override suspend fun delete(appWidgetId: Int) {
        saved.remove(appWidgetId)
    }
}
```

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`, 7 neue Tests grün. Der erste Robolectric-Lauf lädt `android-all-instrumented` für SDK 36 herunter und dauert einige Minuten.

Falls Robolectric SDK 36 nicht unterstützt (Fehlermeldung `Robolectric does not support API level 36`), annotiere die Robolectric-Testklassen mit `@Config(sdk = [35])` und dokumentiere das in `CLAUDE.md` unter „Code“.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: persist widget configuration per widget id in Preferences DataStore"
```

---

### Task 7: Application, AppContainer und Test-Fakes

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/AppContainer.kt`
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/widget/WidgetUpdater.kt`, `MidnightUpdateScheduler.kt` (nur Interfaces; Implementierungen folgen in Task 8 und 10)
- Modify: `app/src/main/java/com/pimorazelvanto/dayscounter/DaysCounterApplication.kt`
- Create: `app/src/sharedTest/java/com/pimorazelvanto/dayscounter/testsupport/FakeWidgetUpdater.kt`, `FakeMidnightUpdateScheduler.kt`, `FakeAppContainer.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/DaysCounterApplicationTest.kt`

**Interfaces:**
- Consumes: `Clock`, `SystemClock`, `WidgetConfigRepository`, `DataStoreWidgetConfigRepository`
- Produces:
  - `fun interface WidgetUpdater { suspend fun updateAll() }`
  - `interface MidnightUpdateScheduler { fun schedule(); fun cancel() }`
  - `interface AppContainer { val clock: Clock; val repository: WidgetConfigRepository; val widgetUpdater: WidgetUpdater; val midnightUpdateScheduler: MidnightUpdateScheduler; val backgroundScope: CoroutineScope }`
  - `class DefaultAppContainer(context: Context) : AppContainer` (die beiden Widget-Implementierungen werden in Task 8/10 eingesetzt; bis dahin stehen dort Platzhalter-Objekte, die dieser Task ausdrücklich definiert, siehe Step 3)
  - `class DaysCounterApplication : Application() { var container: AppContainer }`
  - `val Context.appContainer: AppContainer`
  - `class FakeWidgetUpdater : WidgetUpdater { var updateCount: Int }`
  - `class FakeMidnightUpdateScheduler : MidnightUpdateScheduler { var scheduleCount: Int; var cancelCount: Int }`
  - `class FakeAppContainer(clock, repository, widgetUpdater, scheduler, backgroundScope = CoroutineScope(Dispatchers.Unconfined)) : AppContainer`

- [ ] **Step 1: Fehlschlagender Test**

```kotlin
package com.pimorazelvanto.dayscounter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.data.DataStoreWidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.SystemClock
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaysCounterApplicationTest {
    @Test
    fun `application exposes a default container`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val container = context.appContainer

        assertTrue(container.clock is SystemClock)
        assertTrue(container.repository is DataStoreWidgetConfigRepository)
    }

    @Test
    fun `container can be replaced for tests`() {
        val application = ApplicationProvider.getApplicationContext<DaysCounterApplication>()
        val fake = com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer()

        application.container = fake

        assertSame(fake, application.appContainer)
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'appContainer'`.

- [ ] **Step 3: Implementierung**

`widget/WidgetUpdater.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

fun interface WidgetUpdater {
    suspend fun updateAll()
}
```

`widget/MidnightUpdateScheduler.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

interface MidnightUpdateScheduler {
    fun schedule()

    fun cancel()
}
```

`AppContainer.kt`:
```kotlin
package com.pimorazelvanto.dayscounter

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.pimorazelvanto.dayscounter.data.DataStoreWidgetConfigRepository
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.SystemClock
import com.pimorazelvanto.dayscounter.widget.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val clock: Clock
    val repository: WidgetConfigRepository
    val widgetUpdater: WidgetUpdater
    val midnightUpdateScheduler: MidnightUpdateScheduler
    val backgroundScope: CoroutineScope
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val applicationContext = context.applicationContext

    override val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val clock: Clock = SystemClock()

    override val repository: WidgetConfigRepository =
        DataStoreWidgetConfigRepository(
            PreferenceDataStoreFactory.create {
                applicationContext.preferencesDataStoreFile("widget_configs")
            },
        )

    override val widgetUpdater: WidgetUpdater = NoOpWidgetUpdater

    override val midnightUpdateScheduler: MidnightUpdateScheduler = NoOpMidnightUpdateScheduler
}

private object NoOpWidgetUpdater : WidgetUpdater {
    override suspend fun updateAll() = Unit
}

private object NoOpMidnightUpdateScheduler : MidnightUpdateScheduler {
    override fun schedule() = Unit

    override fun cancel() = Unit
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DaysCounterApplication).container
```
Die beiden `NoOp*`-Objekte sind Übergangs-Implementierungen; Task 8 ersetzt `NoOpMidnightUpdateScheduler` durch `AlarmManagerMidnightUpdateScheduler`, Task 10 ersetzt `NoOpWidgetUpdater` durch `GlanceWidgetUpdater`, und beide Objekte werden dann gelöscht.

`DaysCounterApplication.kt`:
```kotlin
package com.pimorazelvanto.dayscounter

import android.app.Application

class DaysCounterApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
```

`testsupport/FakeWidgetUpdater.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.widget.WidgetUpdater

class FakeWidgetUpdater : WidgetUpdater {
    var updateCount = 0

    override suspend fun updateAll() {
        updateCount++
    }
}
```

`testsupport/FakeMidnightUpdateScheduler.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.widget.MidnightUpdateScheduler

class FakeMidnightUpdateScheduler : MidnightUpdateScheduler {
    var scheduleCount = 0
    var cancelCount = 0

    override fun schedule() {
        scheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }
}
```

`testsupport/FakeAppContainer.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.AppContainer
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.widget.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

class FakeAppContainer(
    override val clock: Clock = FixedClock(LocalDate.of(2026, 9, 8)),
    override val repository: WidgetConfigRepository = FakeWidgetConfigRepository(),
    override val widgetUpdater: FakeWidgetUpdater = FakeWidgetUpdater(),
    override val midnightUpdateScheduler: FakeMidnightUpdateScheduler = FakeMidnightUpdateScheduler(),
    override val backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) : AppContainer
```
`Dispatchers.Unconfined` lässt `backgroundScope.launch { … }` in Tests synchron durchlaufen, solange die Fakes nicht suspendieren. Damit brauchen Receiver-Tests keine Latches.

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add manual dependency container with test fakes"
```

---

### Task 8: Exakter Mitternachtsalarm

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/widget/AlarmManagerMidnightUpdateScheduler.kt`
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/widget/DateChangeReceiver.kt` (nur Klassenrumpf mit Action-Konstante; Verhalten in Task 9)
- Modify: `app/src/main/java/com/pimorazelvanto/dayscounter/AppContainer.kt` (NoOpMidnightUpdateScheduler ersetzen)
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/widget/AlarmManagerMidnightUpdateSchedulerTest.kt`

**Interfaces:**
- Consumes: `Clock.today()`, `Clock.zone()`, `MidnightUpdateScheduler`
- Produces:
  - `class AlarmManagerMidnightUpdateScheduler(context: Context, alarmManager: AlarmManager, clock: Clock) : MidnightUpdateScheduler`
  - `DateChangeReceiver.ACTION_MIDNIGHT = "com.pimorazelvanto.dayscounter.action.MIDNIGHT"`

- [ ] **Step 1: Fehlschlagender Robolectric-Test**

```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.testsupport.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AlarmManagerMidnightUpdateSchedulerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 9, 8)
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AlarmManagerMidnightUpdateScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = AlarmManagerMidnightUpdateScheduler(context, alarmManager, FixedClock(today, zone))
    }

    @Test
    fun `schedules exact wakeup alarm at next local midnight`() {
        scheduler.schedule()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        val expectedMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expectedMidnight, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(0L, alarm.windowLengthMs)
    }

    @Test
    fun `rescheduling replaces instead of duplicating`() {
        scheduler.schedule()
        scheduler.schedule()

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `cancel removes the alarm`() {
        scheduler.schedule()

        scheduler.cancel()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `alarm targets DateChangeReceiver with midnight action`() {
        scheduler.schedule()

        val operation = shadowOf(alarmManager).scheduledAlarms.single().operation!!
        val intent = shadowOf(operation).savedIntent
        assertEquals(DateChangeReceiver.ACTION_MIDNIGHT, intent.action)
        assertEquals(DateChangeReceiver::class.java.name, intent.component?.className)
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'AlarmManagerMidnightUpdateScheduler'`.

- [ ] **Step 3: Implementierung**

`DateChangeReceiver.kt` (Rumpf; Task 9 füllt `onReceive`):
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit

    companion object {
        const val ACTION_MIDNIGHT = "com.pimorazelvanto.dayscounter.action.MIDNIGHT"
    }
}
```

`AlarmManagerMidnightUpdateScheduler.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pimorazelvanto.dayscounter.domain.Clock

class AlarmManagerMidnightUpdateScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val clock: Clock,
) : MidnightUpdateScheduler {
    override fun schedule() {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnightEpochMillis(),
            midnightPendingIntent(),
        )
    }

    override fun cancel() {
        alarmManager.cancel(midnightPendingIntent())
    }

    private fun nextMidnightEpochMillis(): Long =
        clock.today().plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli()

    private fun midnightPendingIntent(): PendingIntent {
        val intent = Intent(context, DateChangeReceiver::class.java).setAction(DateChangeReceiver.ACTION_MIDNIGHT)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE_MIDNIGHT = 1
    }
}
```

In `AppContainer.kt`: `NoOpMidnightUpdateScheduler` samt Objekt entfernen und ersetzen durch
```kotlin
    override val midnightUpdateScheduler: MidnightUpdateScheduler =
        AlarmManagerMidnightUpdateScheduler(
            applicationContext,
            applicationContext.getSystemService(AlarmManager::class.java),
            clock,
        )
```
mit Import `android.app.AlarmManager`. Achtung Reihenfolge: `clock` muss vor `midnightUpdateScheduler` deklariert sein, sonst ist es beim Zugriff noch `null`.

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`. Lint kennt `USE_EXACT_ALARM` aus dem Manifest (Task 1) und meldet daher kein `ScheduleExactAlarm`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: schedule exact wakeup alarm for the next local midnight"
```

---

### Task 9: DateChangeReceiver – Update und Neuplanung bei Zeitereignissen

**Files:**
- Modify: `app/src/main/java/com/pimorazelvanto/dayscounter/widget/DateChangeReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/widget/DateChangeReceiverTest.kt`

**Interfaces:**
- Consumes: `Context.appContainer`, `AppContainer.widgetUpdater`, `AppContainer.midnightUpdateScheduler`, `AppContainer.backgroundScope`, `FakeAppContainer`
- Produces: Manifest-registrierter Receiver für `ACTION_MIDNIGHT`, `ACTION_TIMEZONE_CHANGED`, `ACTION_TIME_CHANGED`, `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`

- [ ] **Step 1: Fehlschlagender Test**

```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DateChangeReceiverTest {
    private lateinit var application: DaysCounterApplication
    private lateinit var container: FakeAppContainer
    private val receiver = DateChangeReceiver()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        container = FakeAppContainer()
        application.container = container
    }

    @Test
    fun `handled actions update all widgets and reschedule`() {
        val actions = listOf(
            DateChangeReceiver.ACTION_MIDNIGHT,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        actions.forEach { receiver.onReceive(application, Intent(it)) }

        assertEquals(actions.size, container.widgetUpdater.updateCount)
        assertEquals(actions.size, container.midnightUpdateScheduler.scheduleCount)
    }

    @Test
    fun `unrelated action is ignored`() {
        receiver.onReceive(application, Intent(Intent.ACTION_BATTERY_LOW))

        assertEquals(0, container.widgetUpdater.updateCount)
        assertEquals(0, container.midnightUpdateScheduler.scheduleCount)
    }

    @Test
    fun `receiver is registered in manifest for all system actions`() {
        val packageManager = application.packageManager
        val systemActions = listOf(
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        systemActions.forEach { action ->
            val receivers = packageManager.queryBroadcastReceivers(Intent(action), 0)
            val names = receivers.map { it.activityInfo.name }
            assertEquals("receiver for $action", listOf(DateChangeReceiver::class.java.name), names)
        }
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --tests '*DateChangeReceiverTest*' --console=plain
```
Erwartet: FAIL, `expected:<5> but was:<0>` und der Manifest-Test mit leerer Liste.

- [ ] **Step 3: Implementierung**

`DateChangeReceiver.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pimorazelvanto.dayscounter.appContainer
import kotlinx.coroutines.launch

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = context.appContainer
        val pendingResult: PendingResult? = goAsync()
        container.backgroundScope.launch {
            try {
                container.widgetUpdater.updateAll()
                container.midnightUpdateScheduler.schedule()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.pimorazelvanto.dayscounter.action.MIDNIGHT"

        private val HANDLED_ACTIONS = setOf(
            ACTION_MIDNIGHT,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
```
`goAsync()` liefert `null`, wenn `onReceive` direkt aufgerufen wird (Tests); im System liefert es ein `PendingResult`, das den Prozess bis `finish()` am Leben hält.

Manifest, innerhalb `<application>`:
```xml
        <receiver
            android:name=".widget.DateChangeReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```
`Intent.ACTION_TIME_CHANGED` hat den String-Wert `android.intent.action.TIME_SET`. `ACTION_MIDNIGHT` kommt über einen expliziten Intent und braucht keinen Filter.

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: refresh widgets and reschedule alarm on midnight and time changes"
```

---

### Task 10: Glance-Widget – Darstellung, Receiver, Widget-Info

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/domain/WidgetUiState.kt`, `app/src/main/java/com/pimorazelvanto/dayscounter/widget/DaysCounterWidgetContent.kt`, `DaysCounterWidget.kt`, `DaysCounterWidgetReceiver.kt`, `GlanceWidgetUpdater.kt`
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/config/ConfigActivity.kt` (Stub mit `createIntent`; Task 13 füllt ihn)
- Create: `app/src/main/res/xml/days_counter_widget_info.xml`, `app/src/main/res/layout/widget_preview.xml`, `app/src/main/res/values/dimens.xml`, `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/res/values/strings.xml`, `values-de/strings.xml`, `app/src/main/AndroidManifest.xml`, `AppContainer.kt` (NoOpWidgetUpdater ersetzen)
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/domain/WidgetUiStateTest.kt`, `app/src/test/java/com/pimorazelvanto/dayscounter/widget/DaysCounterWidgetContentTest.kt`

**Interfaces:**
- Consumes: `WidgetConfig`, `WidgetConfigRepository.load/delete`, `DaysCalculator`, `DisplayValue`, `sizeTier()`, `HeaderColor`, `Clock`, `Context.appContainer`, `MidnightUpdateScheduler`
- Produces:
  - `data class WidgetUiState(val title: String, val valueText: String, val sizeTier: DigitSizeTier, val color: HeaderColor, val isPlaceholder: Boolean)` mit `companion fun from(config: WidgetConfig?, today: LocalDate, defaultTitle: String): WidgetUiState`
  - `@Composable fun DaysCounterWidgetContent(state: WidgetUiState, onClick: Action)` (Glance) mit Test-Tags `"title"` und `"value"`
  - `class DaysCounterWidget : GlanceAppWidget()`
  - `class DaysCounterWidgetReceiver : GlanceAppWidgetReceiver()`
  - `class GlanceWidgetUpdater(context: Context) : WidgetUpdater`
  - `ConfigActivity.createIntent(context: Context, appWidgetId: Int): Intent`
  - Konstante `WidgetUiState.PLACEHOLDER_TEXT = "–"`

Abweichung von der Spec-Regel „`widget` und `config` kennen einander nicht“: `widget` braucht `ConfigActivity.createIntent` für den Tipp auf das Widget. Das ist die einzige erlaubte Kante; sie wird in Task 15 in Spec und `CLAUDE.md` nachgetragen.

- [ ] **Step 1: Fehlschlagende Tests**

`WidgetUiStateTest.kt` (JUnit 5):
```kotlin
package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WidgetUiStateTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `configured widget shows remaining days`() {
        val config = WidgetConfig("Urlaub", today.plusDays(42), HeaderColor.BLUE)

        val state = WidgetUiState.from(config, today, defaultTitle = "Tage")

        assertEquals(WidgetUiState("Urlaub", "42", DigitSizeTier.LARGE, HeaderColor.BLUE, isPlaceholder = false), state)
    }

    @Test
    fun `passed target shows hyphen`() {
        val config = WidgetConfig("Urlaub", today.minusDays(1), HeaderColor.BLUE)

        assertEquals("-", WidgetUiState.from(config, today, "Tage").valueText)
    }

    @Test
    fun `four digit values use small tier`() {
        val config = WidgetConfig("Rente", today.plusDays(4000), HeaderColor.GREY)

        assertEquals(DigitSizeTier.SMALL, WidgetUiState.from(config, today, "Tage").sizeTier)
    }

    @Test
    fun `missing config yields placeholder with default title and color`() {
        val state = WidgetUiState.from(null, today, defaultTitle = "Tage")

        assertTrue(state.isPlaceholder)
        assertEquals("Tage", state.title)
        assertEquals(WidgetUiState.PLACEHOLDER_TEXT, state.valueText)
        assertEquals(HeaderColor.DEFAULT, state.color)
        assertEquals(DigitSizeTier.LARGE, state.sizeTier)
        assertFalse(WidgetUiState.from(WidgetConfig("x", today, HeaderColor.RED), today, "Tage").isPlaceholder)
    }
}
```

`DaysCounterWidgetContentTest.kt` (JUnit 4 + Robolectric, Glance-Testing):
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaysCounterWidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val anyClick = actionStartActivity(Intent(Intent.ACTION_VIEW))

    @Test
    fun `renders title and value`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            DaysCounterWidgetContent(
                WidgetUiState("Urlaub", "42", DigitSizeTier.LARGE, HeaderColor.BLUE, isPlaceholder = false),
                onClick = anyClick,
            )
        }

        onNode(hasTestTag("title")).assertHasText("Urlaub")
        onNode(hasTestTag("value")).assertHasText("42")
    }

    @Test
    fun `renders placeholder text`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            DaysCounterWidgetContent(
                WidgetUiState("Tage", WidgetUiState.PLACEHOLDER_TEXT, DigitSizeTier.LARGE, HeaderColor.RED, isPlaceholder = true),
                onClick = anyClick,
            )
        }

        onNode(hasTestTag("value")).assertHasText(WidgetUiState.PLACEHOLDER_TEXT)
    }

    @Test
    fun `whole widget is clickable`() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable {
            DaysCounterWidgetContent(
                WidgetUiState("Tage", "3", DigitSizeTier.LARGE, HeaderColor.RED, isPlaceholder = false),
                onClick = anyClick,
            )
        }

        onNode(hasTestTag("root")).assert(hasClickAction())
    }
}
```
Falls `hasClickAction` in `androidx.glance.testing.unit` nicht existiert, verwende `androidx.glance.appwidget.testing.unit.hasStartActivityClickAction(Intent(Intent.ACTION_VIEW))` statt `hasClickAction()`; das ist der Glance-eigene Matcher für Start-Activity-Klicks.

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'WidgetUiState'`.

- [ ] **Step 3: Ressourcen**

`res/values/colors.xml` ergänzen:
```xml
    <color name="widget_sheet_background">#FAFAFA</color>
    <color name="widget_sheet_text">#212121</color>
    <color name="widget_placeholder_text">#9E9E9E</color>
    <color name="widget_header_text">#FFFFFF</color>
    <color name="widget_preview_header">#D32F2F</color>
```

`res/values-night/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="widget_sheet_background">#2B2B2B</color>
    <color name="widget_sheet_text">#F5F5F5</color>
    <color name="widget_placeholder_text">#757575</color>
</resources>
```

`res/values/dimens.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <dimen name="widget_title_text_size">10sp</dimen>
    <dimen name="widget_value_text_size_large">22sp</dimen>
    <dimen name="widget_value_text_size_medium">17sp</dimen>
    <dimen name="widget_value_text_size_small">13sp</dimen>
    <dimen name="widget_padding">4dp</dimen>
</resources>
```

`res/values/strings.xml` ergänzen:
```xml
    <string name="widget_description">Counts the days until a date</string>
    <string name="default_title">days</string>
    <string name="widget_preview_value">42</string>
```
`res/values-de/strings.xml` ergänzen:
```xml
    <string name="widget_description">Zählt die Tage bis zu einem Datum</string>
    <string name="default_title">Tage</string>
    <string name="widget_preview_value">42</string>
```

`res/xml/days_counter_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:configure="com.pimorazelvanto.dayscounter.config.ConfigActivity"
    android:description="@string/widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:minHeight="40dp"
    android:minWidth="40dp"
    android:previewLayout="@layout/widget_preview"
    android:resizeMode="none"
    android:targetCellHeight="1"
    android:targetCellWidth="1"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable" />
```

`res/layout/widget_preview.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/widget_sheet_background"
    android:orientation="vertical">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="@color/widget_preview_header"
        android:gravity="center"
        android:text="@string/default_title"
        android:textColor="@color/widget_header_text"
        android:textSize="@dimen/widget_title_text_size" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="2"
        android:gravity="center"
        android:text="@string/widget_preview_value"
        android:textColor="@color/widget_sheet_text"
        android:textSize="@dimen/widget_value_text_size_large"
        android:textStyle="bold" />
</LinearLayout>
```

- [ ] **Step 4: Kotlin-Implementierung**

`WidgetUiState.kt` (Package `domain`, damit `config` und `widget` denselben Anzeigezustand nutzen, ohne einander zu kennen):
```kotlin
package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate

data class WidgetUiState(
    val title: String,
    val valueText: String,
    val sizeTier: DigitSizeTier,
    val color: HeaderColor,
    val isPlaceholder: Boolean,
) {
    companion object {
        const val PLACEHOLDER_TEXT = "–"

        fun from(config: WidgetConfig?, today: LocalDate, defaultTitle: String): WidgetUiState {
            if (config == null) {
                return WidgetUiState(defaultTitle, PLACEHOLDER_TEXT, DigitSizeTier.LARGE, HeaderColor.DEFAULT, isPlaceholder = true)
            }
            val value = DaysCalculator.calculate(today, config.targetDate)
            return WidgetUiState(config.title, value.text, value.sizeTier(), config.color, isPlaceholder = false)
        }
    }
}
```

`DaysCounterWidgetContent.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

private val TITLE_TEXT_SIZE = 10.sp
private val VALUE_TEXT_SIZE_LARGE = 22.sp
private val VALUE_TEXT_SIZE_MEDIUM = 17.sp
private val VALUE_TEXT_SIZE_SMALL = 13.sp
private val CONTENT_PADDING = 4.dp
private val HEADER_HEIGHT = 18.dp

@Composable
fun DaysCounterWidgetContent(state: WidgetUiState, onClick: Action) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_sheet_background))
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(onClick)
            .semantics { testTag = "root" },
    ) {
        Header(state)
        Sheet(state)
    }
}

@Composable
private fun Header(state: WidgetUiState) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(ColorProvider(Color(state.color.argb)))
            .padding(horizontal = CONTENT_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.title,
            maxLines = 1,
            modifier = GlanceModifier.semantics { testTag = "title" },
            style = TextStyle(
                color = ColorProvider(R.color.widget_header_text),
                fontSize = TITLE_TEXT_SIZE,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun Sheet(state: WidgetUiState) {
    val textColor = if (state.isPlaceholder) R.color.widget_placeholder_text else R.color.widget_sheet_text
    Box(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.valueText,
            maxLines = 1,
            modifier = GlanceModifier.semantics { testTag = "value" },
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = state.sizeTier.textSize(),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun DigitSizeTier.textSize(): TextUnit =
    when (this) {
        DigitSizeTier.LARGE -> VALUE_TEXT_SIZE_LARGE
        DigitSizeTier.MEDIUM -> VALUE_TEXT_SIZE_MEDIUM
        DigitSizeTier.SMALL -> VALUE_TEXT_SIZE_SMALL
    }
```
Glance kennt nur `defaultWeight()` ohne Gewichtsfaktor; deshalb bekommt der Header eine feste Höhe und das Blatt den Rest. Bei einer typischen 1x1-Kachel von etwa 57 dp entspricht das dem Verhältnis ein Drittel zu zwei Drittel.

`ConfigActivity.kt` (Stub):
```kotlin
package com.pimorazelvanto.dayscounter.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity

class ConfigActivity : ComponentActivity() {
    companion object {
        fun createIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
```

`DaysCounterWidget.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.appContainer
import com.pimorazelvanto.dayscounter.config.ConfigActivity
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

class DaysCounterWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = container.repository.load(appWidgetId)
        val state = WidgetUiState.from(config, container.clock.today(), context.getString(R.string.default_title))
        val openConfig = actionStartActivity(ConfigActivity.createIntent(context, appWidgetId))

        provideContent {
            DaysCounterWidgetContent(state, onClick = openConfig)
        }
    }
}
```

`GlanceWidgetUpdater.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

class GlanceWidgetUpdater(private val context: Context) : WidgetUpdater {
    override suspend fun updateAll() = DaysCounterWidget().updateAll(context)
}
```

`DaysCounterWidgetReceiver.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.pimorazelvanto.dayscounter.appContainer
import kotlinx.coroutines.launch

class DaysCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaysCounterWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.appContainer.midnightUpdateScheduler.schedule()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val container = context.appContainer
        val pendingResult: PendingResult? = goAsync()
        container.backgroundScope.launch {
            try {
                appWidgetIds.forEach { container.repository.delete(it) }
                if (remainingWidgetIds(context).isEmpty()) container.midnightUpdateScheduler.cancel()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private fun remainingWidgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DaysCounterWidgetReceiver::class.java))
}
```

`AppContainer.kt`: `NoOpWidgetUpdater` entfernen, ersetzen durch `override val widgetUpdater: WidgetUpdater = GlanceWidgetUpdater(applicationContext)`.

Manifest, innerhalb `<application>` ergänzen:
```xml
        <receiver
            android:name=".widget.DaysCounterWidgetReceiver"
            android:exported="true"
            android:label="@string/app_name">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/days_counter_widget_info" />
        </receiver>

        <activity
            android:name=".config.ConfigActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_CONFIGURE" />
            </intent-filter>
        </activity>
```

- [ ] **Step 5: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`. Typische Stolpersteine: Lint `UnusedResources` für `widget_preview_value` (wird im Layout benutzt, also kein Fehler); detekt `LongParameterList` tritt nicht auf, da `WidgetUiState` fünf Parameter hat und der Default-Schwellwert bei sechs liegt.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: render calendar-sheet widget with Glance and wire receiver lifecycle"
```

---

### Task 11: ConfigUiState und ConfigViewModel

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/config/ConfigUiState.kt`, `ConfigViewModel.kt`, `ConfigViewModelFactory.kt`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/config/ConfigUiStateTest.kt`, `ConfigViewModelTest.kt`

**Interfaces:**
- Consumes: `WidgetConfigRepository`, `Clock`, `WidgetUpdater`, `MidnightUpdateScheduler`, `TargetDateValidator`, `ValidationResult`, `WidgetUiState.from`, `HeaderColor.DEFAULT`, Fakes aus `testsupport`
- Produces:
  - `sealed interface SaveState { Idle, Saving, Saved, Failed }` (alle `data object`)
  - `data class ConfigUiState(title: String, targetDate: LocalDate?, color: HeaderColor, today: LocalDate, defaultTitle: String, saveState: SaveState = SaveState.Idle)` mit abgeleiteten `val validation: ValidationResult?`, `val isValid: Boolean`, `val preview: WidgetUiState`
  - `class ConfigViewModel(appWidgetId, repository, clock, widgetUpdater, midnightUpdateScheduler, defaultTitle) : ViewModel()` mit `val uiState: StateFlow<ConfigUiState>`, `onTitleChanged(String)`, `onTargetDateChanged(LocalDate)`, `onColorChanged(HeaderColor)`, `refreshToday()`, `save()`, `onSaveFailureShown()`, `ConfigViewModel.MAX_TITLE_LENGTH = 20`
  - `class ConfigViewModelFactory(appWidgetId: Int, container: AppContainer, defaultTitle: String) : ViewModelProvider.Factory`

- [ ] **Step 1: Fehlschlagende Tests (JUnit 5)**

`ConfigUiStateTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConfigUiStateTest {
    private val today = LocalDate.of(2026, 9, 8)
    private val base = ConfigUiState(title = "Urlaub", targetDate = null, color = HeaderColor.BLUE, today = today, defaultTitle = "Tage")

    @Test
    fun `without date state is invalid and preview is placeholder`() {
        assertNull(base.validation)
        assertFalse(base.isValid)
        assertTrue(base.preview.isPlaceholder)
    }

    @Test
    fun `future date is valid and preview shows days`() {
        val state = base.copy(targetDate = today.plusDays(3))

        assertEquals(ValidationResult.Valid, state.validation)
        assertTrue(state.isValid)
        assertEquals("3", state.preview.valueText)
        assertEquals("Urlaub", state.preview.title)
    }

    @Test
    fun `today is invalid`() {
        val state = base.copy(targetDate = today)

        assertEquals(ValidationResult.NotInFuture, state.validation)
        assertFalse(state.isValid)
    }

    @Test
    fun `blank title previews with default title`() {
        val state = base.copy(title = "   ", targetDate = today.plusDays(1))

        assertEquals("Tage", state.preview.title)
    }

    @Test
    fun `preview uses selected color`() {
        val state = base.copy(targetDate = today.plusDays(1), color = HeaderColor.PINK)

        assertEquals(WidgetUiState.from(null, today, "Tage").copy(title = "Urlaub", valueText = "1", color = HeaderColor.PINK, isPlaceholder = false), state.preview)
    }
}
```

`ConfigViewModelTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.FakeMidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetUpdater
import com.pimorazelvanto.dayscounter.testsupport.FixedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {
    private val today = LocalDate.of(2026, 9, 8)
    private val repository = FakeWidgetConfigRepository()
    private val widgetUpdater = FakeWidgetUpdater()
    private val scheduler = FakeMidnightUpdateScheduler()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(appWidgetId: Int = 7) =
        ConfigViewModel(appWidgetId, repository, FixedClock(today), widgetUpdater, scheduler, defaultTitle = "Tage")

    @Test
    fun `new widget starts with default title, no date and default color`() {
        val state = viewModel().uiState.value

        assertEquals("Tage", state.title)
        assertNull(state.targetDate)
        assertEquals(HeaderColor.DEFAULT, state.color)
        assertEquals(today, state.today)
        assertFalse(state.isValid)
    }

    @Test
    fun `existing config is loaded as start state`() {
        repository.saved[7] = WidgetConfig("Urlaub", today.plusDays(10), HeaderColor.TEAL)

        val state = viewModel().uiState.value

        assertEquals("Urlaub", state.title)
        assertEquals(today.plusDays(10), state.targetDate)
        assertEquals(HeaderColor.TEAL, state.color)
    }

    @Test
    fun `title is truncated to max length`() {
        val vm = viewModel()

        vm.onTitleChanged("a".repeat(30))

        assertEquals(ConfigViewModel.MAX_TITLE_LENGTH, vm.uiState.value.title.length)
    }

    @Test
    fun `save persists, updates widgets and schedules alarm`() {
        val vm = viewModel()
        vm.onTitleChanged("Urlaub")
        vm.onTargetDateChanged(today.plusDays(5))
        vm.onColorChanged(HeaderColor.GREEN)

        vm.save()

        assertEquals(WidgetConfig("Urlaub", today.plusDays(5), HeaderColor.GREEN), repository.saved[7])
        assertEquals(1, widgetUpdater.updateCount)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(SaveState.Saved, vm.uiState.value.saveState)
    }

    @Test
    fun `blank title is saved as default title`() {
        val vm = viewModel()
        vm.onTitleChanged("  ")
        vm.onTargetDateChanged(today.plusDays(5))

        vm.save()

        assertEquals("Tage", repository.saved[7]?.title)
    }

    @Test
    fun `save without valid date does nothing`() {
        val vm = viewModel()
        vm.onTargetDateChanged(today)

        vm.save()

        assertTrue(repository.saved.isEmpty())
        assertEquals(0, widgetUpdater.updateCount)
        assertEquals(SaveState.Idle, vm.uiState.value.saveState)
    }

    @Test
    fun `failed save reports failure and can be acknowledged`() {
        repository.failOnSave = true
        val vm = viewModel()
        vm.onTargetDateChanged(today.plusDays(1))

        vm.save()
        assertEquals(SaveState.Failed, vm.uiState.value.saveState)

        vm.onSaveFailureShown()
        assertEquals(SaveState.Idle, vm.uiState.value.saveState)
    }

    @Test
    fun `refreshToday invalidates a date that became today`() {
        val clockThatAdvances = object : com.pimorazelvanto.dayscounter.domain.Clock {
            var current = today
            override fun today() = current
            override fun zone() = java.time.ZoneId.of("Europe/Berlin")
        }
        val vm = ConfigViewModel(7, repository, clockThatAdvances, widgetUpdater, scheduler, "Tage")
        vm.onTargetDateChanged(today.plusDays(1))
        assertTrue(vm.uiState.value.isValid)

        clockThatAdvances.current = today.plusDays(1)
        vm.refreshToday()

        assertFalse(vm.uiState.value.isValid)
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'ConfigUiState'`.

- [ ] **Step 3: Implementierung**

`ConfigUiState.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.TargetDateValidator
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import java.time.LocalDate

sealed interface SaveState {
    data object Idle : SaveState

    data object Saving : SaveState

    data object Saved : SaveState

    data object Failed : SaveState
}

data class ConfigUiState(
    val title: String,
    val targetDate: LocalDate?,
    val color: HeaderColor,
    val today: LocalDate,
    val defaultTitle: String,
    val saveState: SaveState = SaveState.Idle,
) {
    val validation: ValidationResult? = targetDate?.let { TargetDateValidator.validate(today, it) }

    val isValid: Boolean = validation == ValidationResult.Valid

    val effectiveTitle: String = title.ifBlank { defaultTitle }

    val preview: WidgetUiState =
        WidgetUiState.from(
            config = targetDate?.let { WidgetConfig(effectiveTitle, it, color) },
            today = today,
            defaultTitle = defaultTitle,
        )
}
```

`ConfigViewModel.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.widget.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

class ConfigViewModel(
    private val appWidgetId: Int,
    private val repository: WidgetConfigRepository,
    private val clock: Clock,
    private val widgetUpdater: WidgetUpdater,
    private val midnightUpdateScheduler: MidnightUpdateScheduler,
    private val defaultTitle: String,
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow(
            ConfigUiState(
                title = defaultTitle,
                targetDate = null,
                color = HeaderColor.DEFAULT,
                today = clock.today(),
                defaultTitle = defaultTitle,
            ),
        )
    val uiState: StateFlow<ConfigUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch { loadExistingConfig() }
    }

    fun onTitleChanged(title: String) {
        mutableUiState.update { it.copy(title = title.take(MAX_TITLE_LENGTH)) }
    }

    fun onTargetDateChanged(targetDate: LocalDate) {
        mutableUiState.update { it.copy(targetDate = targetDate) }
    }

    fun onColorChanged(color: HeaderColor) {
        mutableUiState.update { it.copy(color = color) }
    }

    fun refreshToday() {
        mutableUiState.update { it.copy(today = clock.today()) }
    }

    fun save() {
        val state = uiState.value
        val targetDate = state.targetDate
        if (!state.isValid || targetDate == null) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(saveState = SaveState.Saving) }
            val result = persist(WidgetConfig(state.effectiveTitle, targetDate, state.color))
            mutableUiState.update { it.copy(saveState = result) }
        }
    }

    fun onSaveFailureShown() {
        mutableUiState.update { it.copy(saveState = SaveState.Idle) }
    }

    private suspend fun loadExistingConfig() {
        val existing = repository.load(appWidgetId) ?: return
        mutableUiState.update {
            it.copy(title = existing.title, targetDate = existing.targetDate, color = existing.color)
        }
    }

    private suspend fun persist(config: WidgetConfig): SaveState =
        try {
            repository.save(appWidgetId, config)
            widgetUpdater.updateAll()
            midnightUpdateScheduler.schedule()
            SaveState.Saved
        } catch (_: IOException) {
            SaveState.Failed
        }

    companion object {
        const val MAX_TITLE_LENGTH = 20
    }
}
```

`ConfigViewModelFactory.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pimorazelvanto.dayscounter.AppContainer

class ConfigViewModelFactory(
    private val appWidgetId: Int,
    private val container: AppContainer,
    private val defaultTitle: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConfigViewModel::class.java)) { "Unknown ViewModel ${modelClass.name}" }
        return ConfigViewModel(
            appWidgetId,
            container.repository,
            container.clock,
            container.widgetUpdater,
            container.midnightUpdateScheduler,
            defaultTitle,
        ) as T
    }
}
```

- [ ] **Step 4: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`. Falls detekt `SwallowedException` für `catch (_: IOException)` meldet: Die Ausnahme wird bewusst in `SaveState.Failed` überführt; ergänze `@Suppress("SwallowedException")` an `persist` mit dieser Begründung als Kommentar in derselben Zeile.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add configuration view model with validation and save flow"
```

---

### Task 12: Konfigurations-UI in Compose

**Files:**
- Create: `app/src/main/java/com/pimorazelvanto/dayscounter/config/DateConversions.kt`, `FutureOnlySelectableDates.kt`, `WidgetPreview.kt`, `ColorPickerDialog.kt`, `ConfigScreen.kt`
- Create: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/strings.xml`, `values-de/strings.xml`
- Test: `app/src/test/java/com/pimorazelvanto/dayscounter/config/DateConversionsTest.kt`, `FutureOnlySelectableDatesTest.kt`

**Interfaces:**
- Consumes: `ConfigUiState`, `SaveState`, `WidgetUiState`, `HeaderColor.entries`, `DigitSizeTier`
- Produces:
  - `fun LocalDate.toUtcStartOfDayMillis(): Long`, `fun utcMillisToLocalDate(utcMillis: Long): LocalDate`
  - `class FutureOnlySelectableDates(private val today: LocalDate) : SelectableDates`
  - `@Composable fun WidgetPreview(state: WidgetUiState, modifier: Modifier = Modifier)`
  - `@Composable fun ColorPickerDialog(selected: HeaderColor, onSelect: (HeaderColor) -> Unit, onDismiss: () -> Unit)` mit Test-Tags `color_option_<NAME>`
  - `@Composable fun ConfigScreen(state: ConfigUiState, onTitleChanged, onTargetDateChanged, onColorChanged, onSave, onCancel, onSaveFailureShown)` mit Test-Tags `title_field`, `date_field`, `color_field`, `save_button`, `cancel_button`, `preview`, `date_error`
  - Konstante `ConfigTestTags` (object) mit den Tag-Strings

- [ ] **Step 1: Fehlschlagende Tests (JUnit 5)**

`DateConversionsTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateConversionsTest {
    @Test
    fun `local date round trips through utc millis`() {
        val date = LocalDate.of(2027, 3, 15)

        assertEquals(date, utcMillisToLocalDate(date.toUtcStartOfDayMillis()))
    }

    @Test
    fun `epoch day zero maps to 1970-01-01`() {
        assertEquals(LocalDate.of(1970, 1, 1), utcMillisToLocalDate(0L))
        assertEquals(0L, LocalDate.of(1970, 1, 1).toUtcStartOfDayMillis())
    }
}
```

`FutureOnlySelectableDatesTest.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.compose.material3.ExperimentalMaterial3Api
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
class FutureOnlySelectableDatesTest {
    private val today = LocalDate.of(2026, 12, 31)
    private val selectable = FutureOnlySelectableDates(today)

    @Test
    fun `tomorrow is selectable`() {
        assertTrue(selectable.isSelectableDate(today.plusDays(1).toUtcStartOfDayMillis()))
    }

    @Test
    fun `today and yesterday are not selectable`() {
        assertFalse(selectable.isSelectableDate(today.toUtcStartOfDayMillis()))
        assertFalse(selectable.isSelectableDate(today.minusDays(1).toUtcStartOfDayMillis()))
    }

    @Test
    fun `current and later years are selectable, earlier not`() {
        assertTrue(selectable.isSelectableYear(2026))
        assertTrue(selectable.isSelectableYear(2027))
        assertFalse(selectable.isSelectableYear(2025))
    }
}
```

- [ ] **Step 2: Fehlschlag verifizieren**

```bash
./gradlew :app:testDebugUnitTest --console=plain
```
Erwartet: `Unresolved reference 'utcMillisToLocalDate'`.

- [ ] **Step 3: Strings und Theme**

`values/strings.xml` ergänzen:
```xml
    <string name="config_screen_title">Set up widget</string>
    <string name="config_label_title">Title</string>
    <string name="config_label_target_date">Target date</string>
    <string name="config_label_color">Color</string>
    <string name="config_pick_date">Pick a date</string>
    <string name="config_title_length">%1$d/%2$d</string>
    <string name="config_error_date_not_in_future">The date must be in the future</string>
    <string name="config_choose_color">Choose a color</string>
    <string name="config_action_cancel">Cancel</string>
    <string name="config_action_save">Save</string>
    <string name="config_action_ok">OK</string>
    <string name="config_save_failed">Saving failed. Please try again.</string>
    <string name="config_preview_description">Widget preview</string>
    <string name="config_color_description">Color %1$s</string>
    <string name="color_name_red">Red</string>
    <string name="color_name_orange">Orange</string>
    <string name="color_name_yellow">Yellow</string>
    <string name="color_name_green">Green</string>
    <string name="color_name_teal">Teal</string>
    <string name="color_name_blue">Blue</string>
    <string name="color_name_indigo">Indigo</string>
    <string name="color_name_purple">Purple</string>
    <string name="color_name_pink">Pink</string>
    <string name="color_name_brown">Brown</string>
    <string name="color_name_grey">Grey</string>
    <string name="color_name_black">Black</string>
```

`values-de/strings.xml` ergänzen:
```xml
    <string name="config_screen_title">Widget einrichten</string>
    <string name="config_label_title">Titel</string>
    <string name="config_label_target_date">Zieldatum</string>
    <string name="config_label_color">Farbe</string>
    <string name="config_pick_date">Datum wählen</string>
    <string name="config_title_length">%1$d/%2$d</string>
    <string name="config_error_date_not_in_future">Das Datum muss in der Zukunft liegen</string>
    <string name="config_choose_color">Farbe wählen</string>
    <string name="config_action_cancel">Abbrechen</string>
    <string name="config_action_save">Speichern</string>
    <string name="config_action_ok">OK</string>
    <string name="config_save_failed">Speichern fehlgeschlagen. Bitte erneut versuchen.</string>
    <string name="config_preview_description">Widget-Vorschau</string>
    <string name="config_color_description">Farbe %1$s</string>
    <string name="color_name_red">Rot</string>
    <string name="color_name_orange">Orange</string>
    <string name="color_name_yellow">Gelb</string>
    <string name="color_name_green">Grün</string>
    <string name="color_name_teal">Türkis</string>
    <string name="color_name_blue">Blau</string>
    <string name="color_name_indigo">Indigo</string>
    <string name="color_name_purple">Lila</string>
    <string name="color_name_pink">Pink</string>
    <string name="color_name_brown">Braun</string>
    <string name="color_name_grey">Grau</string>
    <string name="color_name_black">Schwarz</string>
```

`values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DaysCounter" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```
Im Manifest `android:theme` der Activity und der Application auf `@style/Theme.DaysCounter` setzen.

- [ ] **Step 4: Implementierung**

`DateConversions.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun LocalDate.toUtcStartOfDayMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun utcMillisToLocalDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
```

`FutureOnlySelectableDates.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import com.pimorazelvanto.dayscounter.domain.TargetDateValidator
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
class FutureOnlySelectableDates(private val today: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        TargetDateValidator.validate(today, utcMillisToLocalDate(utcTimeMillis)) == ValidationResult.Valid

    override fun isSelectableYear(year: Int): Boolean = year >= today.year
}
```

`WidgetPreview.kt` (Compose-Nachbildung des Glance-Blatts, gleiche Maße wie Task 10):
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

private val PREVIEW_SIZE = 72.dp
private val PREVIEW_CORNER = 12.dp
private val HEADER_PADDING = 4.dp
private val TITLE_TEXT_SIZE = 10.sp
private val VALUE_TEXT_SIZE_LARGE = 22.sp
private val VALUE_TEXT_SIZE_MEDIUM = 17.sp
private val VALUE_TEXT_SIZE_SMALL = 13.sp
private const val HEADER_WEIGHT = 1f
private const val SHEET_WEIGHT = 2f

@Composable
fun WidgetPreview(state: WidgetUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .size(PREVIEW_SIZE)
            .clip(RoundedCornerShape(PREVIEW_CORNER))
            .background(colorResource(R.color.widget_sheet_background)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(HEADER_WEIGHT)
                .background(Color(state.color.argb))
                .padding(horizontal = HEADER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.title,
                color = colorResource(R.color.widget_header_text),
                fontSize = TITLE_TEXT_SIZE,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(SHEET_WEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            val textColor = if (state.isPlaceholder) R.color.widget_placeholder_text else R.color.widget_sheet_text
            Text(
                text = state.valueText,
                color = colorResource(textColor),
                fontSize = when (state.sizeTier) {
                    DigitSizeTier.LARGE -> VALUE_TEXT_SIZE_LARGE
                    DigitSizeTier.MEDIUM -> VALUE_TEXT_SIZE_MEDIUM
                    DigitSizeTier.SMALL -> VALUE_TEXT_SIZE_SMALL
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
```

`ColorPickerDialog.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor

private const val GRID_COLUMNS = 3
private val SWATCH_SIZE = 48.dp
private val SWATCH_SPACING = 16.dp

@Composable
fun ColorPickerDialog(selected: HeaderColor, onSelect: (HeaderColor) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_choose_color)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(SWATCH_SPACING),
                modifier = Modifier.fillMaxWidth().testTag(ConfigTestTags.COLOR_GRID),
            ) {
                HeaderColor.entries.chunked(GRID_COLUMNS).forEach { rowColors ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SWATCH_SPACING, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowColors.forEach { color ->
                            ColorSwatch(color, isSelected = color == selected, onClick = { onSelect(color) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.config_action_cancel)) }
        },
    )
}

@Composable
private fun ColorSwatch(color: HeaderColor, isSelected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.config_color_description, stringResource(color.displayNameRes()))
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(Color(color.argb))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(ConfigTestTags.colorOption(color)),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
        }
    }
}

fun HeaderColor.displayNameRes(): Int =
    when (this) {
        HeaderColor.RED -> R.string.color_name_red
        HeaderColor.ORANGE -> R.string.color_name_orange
        HeaderColor.YELLOW -> R.string.color_name_yellow
        HeaderColor.GREEN -> R.string.color_name_green
        HeaderColor.TEAL -> R.string.color_name_teal
        HeaderColor.BLUE -> R.string.color_name_blue
        HeaderColor.INDIGO -> R.string.color_name_indigo
        HeaderColor.PURPLE -> R.string.color_name_purple
        HeaderColor.PINK -> R.string.color_name_pink
        HeaderColor.BROWN -> R.string.color_name_brown
        HeaderColor.GREY -> R.string.color_name_grey
        HeaderColor.BLACK -> R.string.color_name_black
    }
```
`Icons.Filled.Check` liegt in `androidx.compose.material:material-icons-core`, das `material3` transitiv mitbringt. Falls der Import fehlt, im Catalog `androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }` ergänzen (Version über BOM) und als `implementation` eintragen.

`ConfigScreen.kt`:
```kotlin
package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object ConfigTestTags {
    const val PREVIEW = "preview"
    const val TITLE_FIELD = "title_field"
    const val DATE_FIELD = "date_field"
    const val DATE_ERROR = "date_error"
    const val COLOR_FIELD = "color_field"
    const val COLOR_GRID = "color_grid"
    const val SAVE_BUTTON = "save_button"
    const val CANCEL_BUTTON = "cancel_button"

    fun colorOption(color: HeaderColor): String = "color_option_${color.name}"
}

private val SCREEN_PADDING = 24.dp
private val SECTION_SPACING = 20.dp
private val COLOR_DOT_SIZE = 32.dp
private const val MAX_YEARS_AHEAD = 100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onTitleChanged: (String) -> Unit,
    onTargetDateChanged: (LocalDate) -> Unit,
    onColorChanged: (HeaderColor) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSaveFailureShown: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.config_save_failed)

    LaunchedEffect(state.saveState) {
        if (state.saveState == SaveState.Failed) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            onSaveFailureShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.config_screen_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        ) {
            WidgetPreview(state.preview, modifier = Modifier.testTag(ConfigTestTags.PREVIEW))
            TitleField(state, onTitleChanged)
            DateField(state, onClick = { showDatePicker = true })
            ColorField(state.color, onClick = { showColorPicker = true })
            ActionRow(isSaveEnabled = state.isValid && state.saveState != SaveState.Saving, onSave = onSave, onCancel = onCancel)
        }
    }

    if (showDatePicker) {
        TargetDatePickerDialog(
            initialDate = state.targetDate,
            today = state.today,
            onConfirm = { showDatePicker = false; onTargetDateChanged(it) },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showColorPicker) {
        ColorPickerDialog(
            selected = state.color,
            onSelect = { showColorPicker = false; onColorChanged(it) },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Composable
private fun TitleField(state: ConfigUiState, onTitleChanged: (String) -> Unit) {
    OutlinedTextField(
        value = state.title,
        onValueChange = onTitleChanged,
        label = { Text(stringResource(R.string.config_label_title)) },
        supportingText = {
            Text(stringResource(R.string.config_title_length, state.title.length, ConfigViewModel.MAX_TITLE_LENGTH))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(ConfigTestTags.TITLE_FIELD),
    )
}

@Composable
private fun DateField(state: ConfigUiState, onClick: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val isError = state.validation == ValidationResult.NotInFuture
    OutlinedTextField(
        value = state.targetDate?.format(formatter) ?: "",
        onValueChange = {},
        readOnly = true,
        enabled = false,
        isError = isError,
        label = { Text(stringResource(R.string.config_label_target_date)) },
        placeholder = { Text(stringResource(R.string.config_pick_date)) },
        supportingText = {
            if (isError) {
                Text(
                    stringResource(R.string.config_error_date_not_in_future),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(ConfigTestTags.DATE_ERROR),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(ConfigTestTags.DATE_FIELD),
    )
}

@Composable
private fun ColorField(color: HeaderColor, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(ConfigTestTags.COLOR_FIELD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Text(stringResource(R.string.config_label_color), style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.size(COLOR_DOT_SIZE).clip(CircleShape).background(Color(color.argb)))
        Text(stringResource(color.displayNameRes()), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionRow(isSaveEnabled: Boolean, onSave: () -> Unit, onCancel: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel, modifier = Modifier.testTag(ConfigTestTags.CANCEL_BUTTON)) {
            Text(stringResource(R.string.config_action_cancel))
        }
        Button(onClick = onSave, enabled = isSaveEnabled, modifier = Modifier.testTag(ConfigTestTags.SAVE_BUTTON)) {
            Text(stringResource(R.string.config_action_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDatePickerDialog(
    initialDate: LocalDate?,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstSelectable = today.plusDays(1)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toUtcStartOfDayMillis(),
        initialDisplayedMonthMillis = (initialDate ?: firstSelectable).withDayOfMonth(1).toUtcStartOfDayMillis(),
        yearRange = today.year..today.year + MAX_YEARS_AHEAD,
        selectableDates = FutureOnlySelectableDates(today),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { datePickerState.selectedDateMillis?.let { onConfirm(utcMillisToLocalDate(it)) } },
                enabled = datePickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.config_action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.config_action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
```
Das deaktivierte `OutlinedTextField` für das Datum reagiert selbst nicht auf Klicks; der `clickable`-Modifier auf dem Feld fängt den Tipp ab. Die Farben des deaktivierten Felds wirken ausgegraut; falls das im Emulator zu blass ist, ersetze `enabled = false` durch `colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant)` zusätzlich zu `enabled = false`.

- [ ] **Step 5: check**

```bash
./gradlew check --console=plain
```
Erwartet: `BUILD SUCCESSFUL`. detekt-Compose-Regeln, die hier greifen können: `ModifierMissing` (alle öffentlichen Composables ohne `modifier`-Parameter außer Screens und Dialogen) und `ComposableParamOrder`. `ConfigScreen` und `ColorPickerDialog` sind Screens/Dialoge; falls `ModifierMissing` sie trotzdem meldet, ergänze in `config/detekt/detekt.yml`:
```yaml
Compose:
  active: true
  ModifierMissing:
    active: true
    checkModifiersForVisibility: only_public
```
und mache die betroffenen Composables `internal`, statt die Regel abzuschalten.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: build configuration screen with live preview, date picker and color grid"
```

---

### Task 13: ConfigActivity und Emulator-Tests des Konfigurationsflows

**Files:**
- Modify: `app/src/main/java/com/pimorazelvanto/dayscounter/config/ConfigActivity.kt`
- Test: `app/src/androidTest/java/com/pimorazelvanto/dayscounter/config/ConfigActivityTest.kt`

**Interfaces:**
- Consumes: `ConfigViewModel`, `ConfigViewModelFactory`, `ConfigScreen`, `ConfigTestTags`, `SaveState`, `Context.appContainer`, `DaysCounterApplication.container`, `FakeAppContainer`, `FakeWidgetConfigRepository`, `FixedClock`
- Produces: `ConfigActivity` mit `RESULT_CANCELED` als Vorgabe, `RESULT_OK` plus `EXTRA_APPWIDGET_ID` beim Speichern, sofortigem `finish()` ohne gültige ID, `refreshToday()` in `onResume`

- [ ] **Step 1: Emulator starten**

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_3a_API_34_extension_level_7_arm64-v8a -no-snapshot-load -no-audio &
~/Library/Android/sdk/platform-tools/adb wait-for-device
until [ "$(~/Library/Android/sdk/platform-tools/adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
~/Library/Android/sdk/platform-tools/adb shell settings put global window_animation_scale 0
~/Library/Android/sdk/platform-tools/adb shell settings put global transition_animation_scale 0
~/Library/Android/sdk/platform-tools/adb shell settings put global animator_duration_scale 0
```
Die drei `settings`-Zeilen schalten Animationen ab; ohne sie sind Compose-UI-Tests flaky.

- [ ] **Step 2: Fehlschlagende Instrumentation-Tests**

```kotlin
package com.pimorazelvanto.dayscounter.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import com.pimorazelvanto.dayscounter.testsupport.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ConfigActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val repository = FakeWidgetConfigRepository()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        (context as DaysCounterApplication).container =
            FakeAppContainer(clock = FixedClock(today), repository = repository)
    }

    private fun launch(appWidgetId: Int = 42): ActivityScenario<ConfigActivity> =
        ActivityScenario.launchActivityForResult(ConfigActivity.createIntent(context, appWidgetId))

    private fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun pickTomorrow() {
        composeRule.onNodeWithTag(ConfigTestTags.DATE_FIELD).performClick()
        composeRule.onAllNodes(hasText(tomorrow.dayOfMonth.toString()) and hasClickAction()).onFirst().performClick()
        composeRule.onNodeWithText(string(R.string.config_action_ok)).performClick()
    }

    @Test
    fun fullFlow_savesConfigAndReturnsOk() {
        val scenario = launch()

        composeRule.onNodeWithTag(ConfigTestTags.TITLE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(ConfigTestTags.TITLE_FIELD).performTextInput("Urlaub")
        pickTomorrow()
        composeRule.onNodeWithTag(ConfigTestTags.COLOR_FIELD).performClick()
        composeRule.onNodeWithTag(ConfigTestTags.colorOption(HeaderColor.GREEN)).performClick()
        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsEnabled().performClick()

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(42, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(WidgetConfig("Urlaub", tomorrow, HeaderColor.GREEN), repository.saved[42])
    }

    @Test
    fun saveIsDisabledWithoutDate() {
        launch()

        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(ConfigTestTags.PREVIEW).assertIsDisplayed()
    }

    @Test
    fun cancelReturnsCanceledAndWritesNothing() {
        val scenario = launch()

        composeRule.onNodeWithTag(ConfigTestTags.CANCEL_BUTTON).performClick()

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun colorDialogShowsTwelveOptionsAndSelectionUpdatesField() {
        launch()

        composeRule.onNodeWithTag(ConfigTestTags.COLOR_FIELD).performClick()
        HeaderColor.entries.forEach { composeRule.onNodeWithTag(ConfigTestTags.colorOption(it)).assertIsDisplayed() }
        composeRule.onNodeWithTag(ConfigTestTags.colorOption(HeaderColor.TEAL)).performClick()

        composeRule.onAllNodesWithTag(ConfigTestTags.COLOR_GRID).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.color_name_teal)).assertIsDisplayed()
    }

    @Test
    fun existingConfigIsPreloaded() {
        repository.saved[42] = WidgetConfig("Geburtstag", today.plusDays(30), HeaderColor.PINK)

        launch()

        composeRule.onNodeWithText("Geburtstag").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.color_name_pink)).assertIsDisplayed()
        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsEnabled()
    }

    @Test
    fun missingWidgetIdFinishesWithCanceled() {
        val scenario = ActivityScenario.launchActivityForResult<ConfigActivity>(Intent(context, ConfigActivity::class.java))

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }
}
```
Import für `assertCountEquals`: `androidx.compose.ui.test.assertCountEquals`.

- [ ] **Step 3: Fehlschlag verifizieren**

```bash
./gradlew :app:connectedDebugAndroidTest --console=plain
```
Erwartet: Tests schlagen fehl (Activity zeigt nichts an, `onNodeWithTag` findet keine Knoten), kein Kompilierfehler.

- [ ] **Step 4: Implementierung**

`ConfigActivity.kt` vollständig:
```kotlin
package com.pimorazelvanto.dayscounter.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.appContainer

class ConfigActivity : ComponentActivity() {
    private val appWidgetId: Int by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val viewModel: ConfigViewModel by viewModels {
        ConfigViewModelFactory(appWidgetId, appContainer, getString(R.string.default_title))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(state.saveState) {
                    if (state.saveState == SaveState.Saved) finishWithSuccess()
                }
                ConfigScreen(
                    state = state,
                    onTitleChanged = viewModel::onTitleChanged,
                    onTargetDateChanged = viewModel::onTargetDateChanged,
                    onColorChanged = viewModel::onColorChanged,
                    onSave = viewModel::save,
                    onCancel = ::finish,
                    onSaveFailureShown = viewModel::onSaveFailureShown,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) viewModel.refreshToday()
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    companion object {
        fun createIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
```

- [ ] **Step 5: Tests und check ausführen**

```bash
./gradlew check connectedCheck --console=plain
```
Erwartet: `BUILD SUCCESSFUL`, 6 Instrumentation-Tests grün. Der Test-Report liegt unter `app/build/reports/androidTests/connected/`. Bei Flakiness von `pickTomorrow` (Tag im nächsten Monat nicht sichtbar) prüfe, dass `initialDisplayedMonthMillis` in Task 12 auf den Monat von `firstSelectable` zeigt.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: wire configuration activity with widget result handling and emulator tests"
```

---

### Task 14: Emulator-Smoke-Test für das Widget-Rendering

**Files:**
- Test: `app/src/androidTest/java/com/pimorazelvanto/dayscounter/widget/DaysCounterWidgetRenderTest.kt`

**Interfaces:**
- Consumes: `DaysCounterWidgetContent`, `DaysCounterWidget`, `WidgetUiState`, `ConfigActivity.createIntent`, `GlanceRemoteViews.compose`, `GlanceAppWidget.compose`

- [ ] **Step 1: Test schreiben**

```kotlin
package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.config.ConfigActivity
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class DaysCounterWidgetRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val widgetSize = DpSize(60.dp, 60.dp)

    @Test
    fun configuredContentInflatesWithTitleAndValue() = runBlocking {
        val state = WidgetUiState("Urlaub", "42", DigitSizeTier.LARGE, HeaderColor.BLUE, isPlaceholder = false)
        val result = GlanceRemoteViews().compose(context, widgetSize) {
            DaysCounterWidgetContent(state, actionStartActivity(ConfigActivity.createIntent(context, 1)))
        }

        val texts = inflateTexts(result.remoteViews)

        assertTrue("texts were $texts", "Urlaub" in texts && "42" in texts)
    }

    @Test
    fun realWidgetWithoutConfigRendersPlaceholder() = runBlocking {
        val remoteViews = DaysCounterWidget().compose(context, size = widgetSize)

        val texts = inflateTexts(remoteViews)

        val defaultTitle = context.getString(R.string.default_title)
        assertTrue("texts were $texts", defaultTitle in texts && WidgetUiState.PLACEHOLDER_TEXT in texts)
    }

    private fun inflateTexts(remoteViews: android.widget.RemoteViews): List<String> {
        val texts = mutableListOf<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(context)
            val view = remoteViews.apply(context, parent)
            collectTexts(view, texts)
        }
        return texts
    }

    private fun collectTexts(view: View, into: MutableList<String>) {
        if (view is TextView) into += view.text.toString()
        if (view is ViewGroup) (0 until view.childCount).forEach { collectTexts(view.getChildAt(it), into) }
    }
}
```

- [ ] **Step 2: Test ausführen (Emulator läuft aus Task 13)**

```bash
./gradlew :app:connectedDebugAndroidTest --console=plain
```
Erwartet: beide Tests grün. Falls `realWidgetWithoutConfigRendersPlaceholder` mit `Invalid AppWidget ID` scheitert, ruft `provideGlance` `getAppWidgetId` auf einer Fake-ID auf, was erlaubt ist; dann liegt der Fehler beim `repository.load` und muss nach dem Stacktrace behandelt werden. Falls Robolectric-Unit-Tests aus Task 10 bereits das Content-Composable abdecken, bleibt dieser Test trotzdem, weil nur er die echte RemoteViews-Inflation auf einem Gerät prüft.

- [ ] **Step 3: check und Commit**

```bash
./gradlew check --console=plain
git add -A
git commit -m "test: add on-device rendering smoke test for the widget"
```

---

### Task 15: README, Dokumentationsabgleich, Endabnahme

**Files:**
- Create: `README.md`
- Modify: `CLAUDE.md`, `docs/superpowers/specs/2026-09-08-days-counter-widget-design.md`

- [ ] **Step 1: README.md für menschliche Leser**

```markdown
# Days Counter

Ein 1x1-Homescreen-Widget für Android 13 und neuer, das die Tage bis zu einem Datum zählt.
Ersatz für das nicht mehr gepflegte Widget „days left“.

## Funktionen

- Beliebig viele Widgets, jedes mit eigenem Titel, Zieldatum und Header-Farbe (12 Farben)
- Zeigt die verbleibenden Tage, „0“ am Zieltag und „-“ danach; angefangene Tage zählen voll
- Aktualisiert sich exakt um Mitternacht sowie bei Zeitzonen- oder Zeitänderung
- Folgt dem hellen oder dunklen Systemthema
- Oberfläche auf Deutsch und Englisch

## Bedienung

1. Widget „Days Counter“ auf den Homescreen ziehen. Der Konfigurationsdialog öffnet sich.
2. Titel eingeben, Zieldatum wählen (frühestens morgen), Farbe antippen und aus dem Raster wählen.
3. Speichern. Ein Tipp auf das Widget öffnet den Dialog erneut.

## Installation aus dem Quellcode

Voraussetzungen: JDK 17 oder neuer, Android SDK mit Platform 36 und Build-Tools 36.0.0,
`sdk.dir` in `local.properties` oder `ANDROID_HOME` gesetzt.

    ./gradlew assembleDebug
    adb install app/build/outputs/apk/debug/app-debug.apk

## Entwicklung

    ./gradlew check            # Lint, ktlint, detekt, Unit- und Robolectric-Tests
    ./gradlew connectedCheck   # Instrumentierte Tests auf einem laufenden Emulator
    ./gradlew ktlintFormat     # Formatierung anwenden

Architektur und Entscheidungen: `docs/superpowers/specs/2026-09-08-days-counter-widget-design.md`.
```

- [ ] **Step 2: Spec und CLAUDE.md an die umgesetzte Struktur angleichen**

In der Spec, Abschnitt 4 (Architektur):
- `WidgetConfig` und `WidgetUiState` liegen in `domain`, nicht in `data`/`widget`. Baumdarstellung entsprechend anpassen.
- Ergänzen: „Einzige Ausnahme der Abhängigkeitsregel: `widget` ruft `ConfigActivity.createIntent` auf, um den Tipp auf das Widget an die Konfiguration zu binden.“
- `WidgetConfigRepository` ist eine Schnittstelle mit Implementierung `DataStoreWidgetConfigRepository`; `MidnightUpdateScheduler` ebenso mit `AlarmManagerMidnightUpdateScheduler`; `WidgetUpdater` mit `GlanceWidgetUpdater`.
- Abschnitt 10: Repository-, Scheduler- und Receiver-Tests laufen mit Robolectric; ViewModel- und Domänentests sind reine JUnit-5-Tests mit Fakes.

In `CLAUDE.md`, Abschnitt „Code“, die Abhängigkeitsregel ersetzen durch:
```
- Abhängigkeitsrichtung der Packages einhalten: `domain` kennt nichts; `data` kennt
  `domain`; `widget` und `config` kennen `domain` und `data`. Einzige Kante zwischen den
  beiden: `widget` ruft `ConfigActivity.createIntent` auf.
- Test-Fakes und `FixedClock` liegen in `app/src/sharedTest/java` und werden von Unit- und
  Instrumentation-Tests gemeinsam genutzt. Keine zweite Kopie anlegen.
```

- [ ] **Step 3: Endabnahme**

```bash
./gradlew clean check connectedCheck assembleDebug --console=plain
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Erwartet: `BUILD SUCCESSFUL`, APK installiert. Danach manuell im Emulator: lange auf den Homescreen drücken, „Widgets“, „Days Counter“ platzieren, Dialog erscheint, Datum morgen wählen, speichern. Widget zeigt „1“. Tipp auf das Widget öffnet den Dialog mit den gespeicherten Werten. Datum ändern, speichern, Widget zeigt neuen Wert. Mit `adb shell settings put system time_12_24 24` passiert nichts Relevantes; mit
```bash
~/Library/Android/sdk/platform-tools/adb shell "cmd alarm set-time $(( ($(date +%s) + 86400) * 1000 ))"
```
springt die Gerätezeit einen Tag vor, `TIME_SET` feuert, das Widget zeigt „0“. Anschließend Zeit zurücksetzen mit `adb shell "cmd alarm set-time $(( $(date +%s) * 1000 ))"`. Diese manuelle Prüfung im Commit-Text nennen.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: add README and align spec and agent instructions with implementation"
```
