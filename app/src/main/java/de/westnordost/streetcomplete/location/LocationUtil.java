package de.westnordost.streetcomplete.location;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

public class LocationUtil
{
	public static boolean isLocationOn(Context context)
	{
		if(!hasLocationPermission(context)) return false;

		try
		{
			int locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
			return locationMode != Settings.Secure.LOCATION_MODE_OFF;
		}
		catch(Settings.SettingNotFoundException e)
		{
			e.printStackTrace();
			return false;
		}
	}

	public static boolean hasLocationPermission(Context context)
	{
		return ContextCompat.checkSelfPermission(context,
				Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	public static IntentFilter createLocationAvailabilityIntentFilter()
	{
		return new IntentFilter(MODE_CHANGED);
	}

	private static final String MODE_CHANGED = "android.location.MODE_CHANGED";
}
