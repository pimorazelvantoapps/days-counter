package com.pimorazelvanto.dayscounter.testsupport

import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.Locale

/**
 * Forces [locale] on the target app's configuration for one test's duration, restoring the
 * previous locale and JVM default afterwards even if the test fails. Needed wherever a test
 * addresses a control through a string it cannot reference directly, such as a Material 3
 * label that is a library-internal resource: referencing a private resource trips Android
 * Lint's check for one, which this project treats as an error, so the label is matched as
 * literal text instead, and that text depends on the device's locale unless pinned.
 */
class PinnedLocaleRule(
    private val locale: Locale = Locale.US,
) : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
                val originalConfiguration = Configuration(resources.configuration)
                val originalDefaultLocale = Locale.getDefault()
                val pinnedConfiguration = Configuration(resources.configuration)
                pinnedConfiguration.setLocale(locale)

                Locale.setDefault(locale)
                applyConfiguration(resources, pinnedConfiguration)
                try {
                    base.evaluate()
                } finally {
                    Locale.setDefault(originalDefaultLocale)
                    applyConfiguration(resources, originalConfiguration)
                }
            }
        }

    @Suppress("DEPRECATION") // createConfigurationContext returns a derived Context, not this one.
    private fun applyConfiguration(
        resources: Resources,
        configuration: Configuration,
    ) {
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}
