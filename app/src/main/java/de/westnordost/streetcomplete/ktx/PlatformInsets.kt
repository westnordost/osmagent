package de.westnordost.streetcomplete.ktx

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.Insets

@Suppress("NOTHING_TO_INLINE")
@RequiresApi(Build.VERSION_CODES.Q)
inline fun android.graphics.Insets.toCompatInsets() = Insets.toCompatInsets(this)
