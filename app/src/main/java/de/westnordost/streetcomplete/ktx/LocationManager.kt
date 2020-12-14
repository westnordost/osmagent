package de.westnordost.streetcomplete.ktx

import android.location.LocationManager
import androidx.core.location.LocationManagerCompat

inline val LocationManager.isLocationEnabledCompat: Boolean
    get() = LocationManagerCompat.isLocationEnabled(this)
