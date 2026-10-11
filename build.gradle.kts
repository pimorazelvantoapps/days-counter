plugins {
    // `base` gibt dem Root-Projekt ein `check`-Lifecycle-Target, an das sich detekt unten hängt;
    // ohne dieses Target würde `./gradlew check` nur :app:check ausführen und detekt nie aufrufen.
    base
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

tasks.check {
    dependsOn(tasks.named("detekt"))
}

// Called by semantic-release while it prepares a release; the release commit carries the file,
// so F-Droid builds the same version from the tag.
tasks.register("writeVersionProperties") {
    val semanticVersion = providers.gradleProperty("appVersion")
    val target = layout.projectDirectory.file("version.properties")
    doLast {
        target.asFile.writeText(AppVersion.fromSemanticVersion(semanticVersion.get()).toProperties())
    }
}
