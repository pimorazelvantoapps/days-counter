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
        val expected =
            listOf(
                "RED",
                "ORANGE",
                "YELLOW",
                "GREEN",
                "TEAL",
                "BLUE",
                "INDIGO",
                "PURPLE",
                "PINK",
                "BROWN",
                "GREY",
                "BLACK",
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

    @ParameterizedTest
    @EnumSource(HeaderColor::class)
    fun `white title text is legible against every header color`(color: HeaderColor) {
        val ratio = whiteTextContrastRatio(color.argb)
        assertTrue(
            ratio >= WCAG_AA_NORMAL_TEXT_CONTRAST_RATIO,
            "contrast ratio of ${color.name} is $ratio, must be at least $WCAG_AA_NORMAL_TEXT_CONTRAST_RATIO",
        )
    }
}

private const val WCAG_AA_NORMAL_TEXT_CONTRAST_RATIO = 4.5

private fun whiteTextContrastRatio(argb: Long): Double {
    val red = (argb shr 16 and 0xFF) / 255.0
    val green = (argb shr 8 and 0xFF) / 255.0
    val blue = (argb and 0xFF) / 255.0
    val luminance =
        0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
    return 1.05 / (luminance + 0.05)
}

private fun linearize(channel: Double): Double =
    if (channel <= 0.03928) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
