package de.westnordost.streetcomplete.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.ktx.isLocationEnabledCompat

/** Manages the process to ensure that the app can access the user's location. Two steps:
 *
 *  1. ask for permission
 *  2. ask for location to be turned on
 *
 * This fragment reports back via a local broadcast with the intent LocationRequestFragment.ACTION_FINISHED
 * The process is started via [.startRequest]  */
class LocationRequestFragment : Fragment() {

    var state: LocationState? = null
    private set

    private var inProgress = false
    private var locationProviderChangedReceiver: BroadcastReceiver? = null

    /* Lifecycle */

    override fun onStop() {
        super.onStop()
        unregisterForLocationProviderChanges()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (state != null) outState.putString("locationState", state!!.name)
        outState.putBoolean("inProgress", inProgress)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (savedInstanceState != null) {
            val stateName = savedInstanceState.getString("locationState")
            if (stateName != null) state = LocationState.valueOf(stateName)
            inProgress = savedInstanceState.getBoolean("inProgress")
        }
    }

    /** Start location request process. When already started, will not be started again.  */
    fun startRequest() {
        if (!inProgress) {
            inProgress = true
            state = null
            nextStep()
        }
    }

    private fun nextStep() {
        if (state == null || state == LocationState.DENIED) {
            requestLocationPermissions()
        } else if (state == LocationState.ALLOWED) {
            requestLocationSettingsToBeOn()
        } else if (state == LocationState.ENABLED) {
            finish()
        }
    }

    private fun finish() {
        inProgress = false
        val intent = Intent(ACTION_FINISHED)
        intent.putExtra(STATE, state!!.name)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    /* Step 1: Ask for permission */

    private fun requestLocationPermissions() {
        if (LocationUtil.hasLocationPermission(context)) {
            state = LocationState.ALLOWED
            nextStep()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // must be for someone else...
        if (requestCode != LOCATION_PERMISSION_REQUEST) return
        if (permissions.firstOrNull() != Manifest.permission.ACCESS_FINE_LOCATION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions() // retry then...
        } else {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.no_location_permission_warning)
                .setPositiveButton(R.string.retry) { _, _ -> requestLocationPermissions() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> deniedLocationPermissions() }
                .setOnCancelListener { deniedLocationPermissions() }
                .show()
        }
    }

    private fun deniedLocationPermissions() {
        state = LocationState.DENIED
        finish()
    }

    /* Step 1: Ask for location to be turned on */

    private fun requestLocationSettingsToBeOn() {
        if (requireContext().getSystemService<LocationManager>()!!.isLocationEnabledCompat) {
            state = LocationState.ENABLED
            nextStep()
        } else {
            val dlg = AlertDialog.Builder(requireContext())
                .setMessage(R.string.turn_on_location_request)
                .setPositiveButton(android.R.string.yes) { dialog, _ ->
                    dialog.dismiss()
                    startActivityForResult(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        LOCATION_TURN_ON_REQUEST
                    )
                }
                .setNegativeButton(android.R.string.no) { _, _ -> cancelTurnLocationOnDialog() }
                .setOnCancelListener { cancelTurnLocationOnDialog() }
                .create()

            // the user may turn on location in the pull-down-overlay, without actually going into
            // settings dialog
            registerForLocationProviderChanges(dlg)
            dlg.show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // must be for someone else...
        if (requestCode != LOCATION_TURN_ON_REQUEST) return
        // we ignore the resultCode, because we always get Activity.RESULT_CANCELED. Instead, we
        // check if the conditions are fulfilled now
        requestLocationSettingsToBeOn()
    }

    private fun registerForLocationProviderChanges(dlg: AlertDialog) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                dlg.dismiss()
                unregisterForLocationProviderChanges()
                requestLocationSettingsToBeOn()
            }
        }
        requireContext().registerReceiver(receiver, LocationUtil.createLocationAvailabilityIntentFilter())
        locationProviderChangedReceiver = receiver
    }

    private fun unregisterForLocationProviderChanges() {
        locationProviderChangedReceiver?.let { receiver ->
            requireContext().unregisterReceiver(receiver)
        }
        locationProviderChangedReceiver = null
    }

    private fun cancelTurnLocationOnDialog() {
        unregisterForLocationProviderChanges()
        finish()
    }

    companion object {
        const val ACTION_FINISHED = "de.westnordost.LocationRequestFragment.FINISHED"
        const val STATE = "state"
        private const val LOCATION_PERMISSION_REQUEST = 1
        private const val LOCATION_TURN_ON_REQUEST = 2
    }
}
