package com.pimorazelvanto.dayscounter.domain

sealed interface DisplayValue {
    val text: String

    data class Remaining(
        val count: Int,
    ) : DisplayValue {
        override val text: String get() = count.toString()
    }

    data object Reached : DisplayValue {
        override val text: String = "0"
    }

    data class Elapsed(
        val count: Int,
    ) : DisplayValue {
        override val text: String get() = count.toString()
    }
}
