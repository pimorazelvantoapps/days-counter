package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VisibleCharactersTest {
    private val palm = "🌴"
    private val family = "👨‍👩‍👧"

    @Test
    fun `plain text counts one per letter`() {
        assertEquals(6, "Urlaub".visibleLength())
    }

    @Test
    fun `emoji of two UTF-16 units counts as one`() {
        assertEquals(1, palm.visibleLength())
    }

    @Test
    fun `family emoji joined by zero-width joiners counts as one`() {
        assertEquals(1, family.visibleLength())
    }

    @Test
    fun `taking up to an emoji keeps the emoji whole`() {
        assertEquals("ab$palm", "ab${palm}cd".takeVisible(3))
    }

    @Test
    fun `taking fewer than the text has drops whole characters only`() {
        assertEquals("ab", "ab$family".takeVisible(2))
    }

    @Test
    fun `taking more than the text has returns the text`() {
        assertEquals("ab", "ab".takeVisible(20))
    }
}
