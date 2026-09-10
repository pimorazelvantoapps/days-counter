package com.pimorazelvanto.dayscounter.domain

enum class DigitSizeTier {
    ONE_DIGIT,
    TWO_DIGITS,
    THREE_DIGITS,
    FOUR_DIGITS,
    FIVE_OR_MORE_DIGITS,
}

private const val MAX_LENGTH_ONE_DIGIT = 1
private const val MAX_LENGTH_TWO_DIGITS = 2
private const val MAX_LENGTH_THREE_DIGITS = 3
private const val MAX_LENGTH_FOUR_DIGITS = 4

fun DisplayValue.sizeTier(): DigitSizeTier =
    when {
        text.length <= MAX_LENGTH_ONE_DIGIT -> DigitSizeTier.ONE_DIGIT
        text.length <= MAX_LENGTH_TWO_DIGITS -> DigitSizeTier.TWO_DIGITS
        text.length <= MAX_LENGTH_THREE_DIGITS -> DigitSizeTier.THREE_DIGITS
        text.length <= MAX_LENGTH_FOUR_DIGITS -> DigitSizeTier.FOUR_DIGITS
        else -> DigitSizeTier.FIVE_OR_MORE_DIGITS
    }
