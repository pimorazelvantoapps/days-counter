package com.pimorazelvanto.dayscounter.domain

import java.text.BreakIterator

/**
 * Counts what a reader sees as one character: an emoji is two UTF-16 units, a family emoji
 * joined by zero-width joiners up to eleven, yet each shows as one. Relies on
 * [BreakIterator.getCharacterInstance] finding grapheme clusters, which it does on Android (ICU)
 * and on the JVM since Java 20.
 */
fun String.visibleLength(): Int = graphemeEnds().count()

/** Keeps the first [count] visible characters, never cutting one in half. */
fun String.takeVisible(count: Int): String = substring(0, graphemeEnds().take(count).lastOrNull() ?: 0)

private fun String.graphemeEnds(): Sequence<Int> {
    val iterator = BreakIterator.getCharacterInstance().also { it.setText(this) }
    return generateSequence { iterator.next().takeIf { it != BreakIterator.DONE } }
}
