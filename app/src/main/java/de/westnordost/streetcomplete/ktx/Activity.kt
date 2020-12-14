package de.westnordost.streetcomplete.ktx

import android.app.Activity
import androidx.core.app.ActivityCompat

@Suppress("NOTHING_TO_INLINE")
inline fun Activity.recreateCompat() = ActivityCompat.recreate(this)
