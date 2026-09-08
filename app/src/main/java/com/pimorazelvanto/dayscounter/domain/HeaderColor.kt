package com.pimorazelvanto.dayscounter.domain

@Suppress("MagicNumber") // ARGB literals are the palette itself, not values to name.
enum class HeaderColor(
    val argb: Long,
) {
    RED(0xFFD32F2F),
    ORANGE(0xFFB35300),
    YELLOW(0xFF8D6E00),
    GREEN(0xFF2E7D32),
    TEAL(0xFF00796B),
    BLUE(0xFF1976D2),
    INDIGO(0xFF3949AB),
    PURPLE(0xFF7B1FA2),
    PINK(0xFFC2185B),
    BROWN(0xFF6D4C41),
    GREY(0xFF616161),
    BLACK(0xFF212121),
    ;

    companion object {
        val DEFAULT: HeaderColor = RED

        fun fromName(name: String): HeaderColor? = entries.firstOrNull { it.name == name }
    }
}
