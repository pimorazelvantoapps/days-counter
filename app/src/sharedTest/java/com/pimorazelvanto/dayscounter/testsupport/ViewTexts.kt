package com.pimorazelvanto.dayscounter.testsupport

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** The texts of every TextView in this view and its descendants, in layout order. */
fun View.texts(): List<String> =
    buildList {
        if (this@texts is TextView) add(text.toString())
        if (this@texts is ViewGroup) for (index in 0 until childCount) addAll(getChildAt(index).texts())
    }
