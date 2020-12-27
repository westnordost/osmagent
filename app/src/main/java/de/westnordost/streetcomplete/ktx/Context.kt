package de.westnordost.streetcomplete.ktx

import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources

fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, text, duration).show()
}

fun Context.toast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

@Suppress("NOTHING_TO_INLINE")
inline fun Context.getAppCompatDrawable(@DrawableRes resId: Int) = AppCompatResources.getDrawable(this, resId)
