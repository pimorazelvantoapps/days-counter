package com.pimorazelvanto.dayscounter.domain

sealed interface DisplayValue {
    val text: String

    data class Days(
        val count: Int,
    ) : DisplayValue {
        override val text: String get() = count.toString()
    }

    data object Reached : DisplayValue {
        override val text: String = "0"
    }

    data object Passed : DisplayValue {
        override val text: String = "-"
    }
}
