package de.westnordost.streetcomplete.ktx

import android.os.Build
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.core.view.WindowInsetsCompat

@Suppress("NOTHING_TO_INLINE")
@RequiresApi(Build.VERSION_CODES.KITKAT_WATCH)
inline fun WindowInsets.toWindowInsetsCompat() = WindowInsetsCompat.toWindowInsetsCompat(this)
