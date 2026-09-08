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
}
