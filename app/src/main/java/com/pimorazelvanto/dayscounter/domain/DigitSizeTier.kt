package com.pimorazelvanto.dayscounter.domain

enum class DigitSizeTier {
    LARGE,
    MEDIUM,
    SMALL,
}

private const val MAX_LENGTH_LARGE = 2
private const val MAX_LENGTH_MEDIUM = 3

fun DisplayValue.sizeTier(): DigitSizeTier =
    when {
        text.length <= MAX_LENGTH_LARGE -> DigitSizeTier.LARGE
        text.length <= MAX_LENGTH_MEDIUM -> DigitSizeTier.MEDIUM
        else -> DigitSizeTier.SMALL
    }
