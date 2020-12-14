package de.westnordost.streetcomplete.location;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import static android.location.LocationManager.PROVIDERS_CHANGED_ACTION;

public class LocationUtil
{
	public static boolean hasLocationPermission(Context context)
	{
		return ContextCompat.checkSelfPermission(context,
				Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	public static IntentFilter createLocationAvailabilityIntentFilter()
	{
		String action = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? MODE_CHANGED : PROVIDERS_CHANGED_ACTION;
		return new IntentFilter(action);
	}

	// because LocationManager.MODE_CHANGED is not defined before KitKat
	private static final String MODE_CHANGED = "android.location.MODE_CHANGED";
}
