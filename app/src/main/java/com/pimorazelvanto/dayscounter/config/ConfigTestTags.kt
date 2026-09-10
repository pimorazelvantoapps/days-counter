package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor

/**
 * Addressed by the instrumentation tests, which reach the configuration screen through these
 * tags alone, so the strings are a contract rather than an implementation detail.
 */
object ConfigTestTags {
    const val PREVIEW = "preview"
    const val TITLE_FIELD = "title_field"
    const val DATE_FIELD = "date_field"
    const val COLOR_FIELD = "color_field"
    const val COLOR_GRID = "color_grid"
    const val SAVE_BUTTON = "save_button"
    const val CANCEL_BUTTON = "cancel_button"

    fun colorOption(color: HeaderColor): String = "color_option_${color.name}"
}
