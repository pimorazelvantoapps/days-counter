package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisplayValueTest {
    @Test
    fun `remaining days render as plain number`() {
        assertEquals("42", DisplayValue.Remaining(42).text)
    }

    @Test
    fun `reached renders as zero`() {
        assertEquals("0", DisplayValue.Reached.text)
    }

    @Test
    fun `elapsed days render as plain number`() {
        assertEquals("42", DisplayValue.Elapsed(42).text)
    }
}
