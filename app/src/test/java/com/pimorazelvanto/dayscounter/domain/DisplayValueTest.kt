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
    fun `passed renders as an ascii hyphen`() {
        assertEquals("\u002D", DisplayValue.Passed.text)
    }
}
