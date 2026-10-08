plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.ktlint)
}

val releaseVersion: String? = providers.gradleProperty("appVersion").orNull

/**
 * Packs a `MAJOR.MINOR.PATCH` version into `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`, so every
 * release gets a strictly greater `versionCode` than its predecessor, as Google Play requires.
 * Minor and patch must therefore stay below 1000.
 */
fun versionCodeOf(semanticVersion: String): Int {
    val (major, minor, patch) =
        requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(semanticVersion)) {
            "appVersion must look like MAJOR.MINOR.PATCH, was '$semanticVersion'"
        }.destructured.toList().map(String::toInt)
    require(minor < 1_000 && patch < 1_000) {
        "appVersion '$semanticVersion' does not fit the versionCode scheme: minor and patch must be below 1000"
    }
    return major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "com.pimorazelvanto.dayscounter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pimorazelvanto.dayscounter"
        minSdk = 33
        targetSdk = 37
        versionCode = releaseVersion?.let(::versionCodeOf) ?: 1
        versionName = releaseVersion ?: "0.0.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
