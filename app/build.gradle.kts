plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.ktlint)
}

val appVersion: AppVersion =
    AppVersion.parseProperties(
        providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get(),
    )

/**
 * Keystore that signs the release build: the Play upload key for the bundle, the distribution key
 * for the APK on GitHub, IzzyOnDroid and F-Droid. Without it the build stays unsigned, as F-Droid
 * and pull requests build it.
 */
val signingKeystoreFile: String? = providers.environmentVariable("SIGNING_KEYSTORE_FILE").orNull

android {
    namespace = "com.pimorazelvanto.dayscounter"
    compileSdk = 37

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

    defaultConfig {
        applicationId = "com.pimorazelvanto.dayscounter"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersion.code
        versionName = appVersion.name
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // The release build signed with the debug key, so CI can install the exact minified app
        // for a smoke test.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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

    // AGP would store the dependency list, encrypted for Google, in the APK signing block; F-Droid's
    // scanner rejects that block, and the app does not need it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// baseline.prof is not reliably reproducible across builds (F-Droid's Reproducible Builds guide);
// for a widget the startup gain is negligible, a reproducible APK is not.
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
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
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    constraints {
        // Glance pulls WorkManager 2.7.1, whose Room 2.2.5 creates WorkDatabase reflectively.
        // R8 removes that constructor and the minified app crashes on start; WorkManager 2.8+
        // brings a Room with matching keep rules.
        implementation(libs.androidx.work.runtime)
    }

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
    // Compose's test rule pulls Espresso 3.5, which reflects on InputManager.getInstance, a method
    // Android 16 removed: every Compose test would fail there before reaching the app.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
