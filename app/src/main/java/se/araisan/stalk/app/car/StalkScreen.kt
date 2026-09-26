package se.araisan.stalk.app.car

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import se.araisan.stalk.app.APP_PREF_SERVICE_RUNNING
import se.araisan.stalk.app.APP_PREF_STALK_FREQ
import se.araisan.stalk.app.APP_PREF_START_FAILED_AT
import se.araisan.stalk.app.APP_PREF_USER_NAME
import se.araisan.stalk.app.LocationService
import se.araisan.stalk.app.R
import se.araisan.stalk.app.appPrefs
import se.araisan.stalk.app.clearStaleServiceRunningFlag

/**
 * Car screen showing the tracking status with a single Start/Stop action.
 * Setup (name, interval, permissions) stays on the phone since the car
 * templates can neither take free text input while driving nor show permission dialogs.
 */
class StalkScreen(
    carContext: CarContext,
) : Screen(carContext) {
    private val prefs = carContext.appPrefs()

    // Held as a field: SharedPreferences only keeps weak references to listeners.
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                // Only report refusals of starts the driver asked for, not system (sticky) restarts.
                APP_PREF_START_FAILED_AT -> {
                    if (startRequested) {
                        startRequested = false
                        showStartOnPhoneToast()
                    }
                }

                APP_PREF_SERVICE_RUNNING -> {
                    startRequested = false
                    invalidate()
                }

                // Only what the screen shows (null = prefs cleared). Other keys, like the data-existence
                // flags written after every location report, must not trigger a template refresh.
                APP_PREF_USER_NAME, APP_PREF_STALK_FREQ, null -> {
                    invalidate()
                }
            }
        }

    private var startRequested = false

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    carContext.clearStaleServiceRunningFlag()
                    prefs.registerOnSharedPreferenceChangeListener(prefsListener)
                    // Permissions may have been granted on the phone while we were hidden.
                    invalidate()
                }

                override fun onStop(owner: LifecycleOwner) {
                    prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
                    // We won't see the outcome of a pending start; don't blame a later system restart on the driver.
                    startRequested = false
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val name = prefs.getString(APP_PREF_USER_NAME, "").orEmpty()
        if (name.isEmpty() || !hasRequiredPermissions()) {
            return MessageTemplate
                .Builder(carContext.getString(R.string.car_setup_required))
                .setHeader(header())
                .build()
        }

        val running = LocationService.isRunning
        val frequency = prefs.getString(APP_PREF_STALK_FREQ, "10s") ?: "10s"

        val toggleAction =
            Action
                .Builder()
                .setTitle(carContext.getString(if (running) R.string.stop_stalking else R.string.start_stalking))
                .setBackgroundColor(if (running) CarColor.RED else CarColor.GREEN)
                .setFlags(Action.FLAG_PRIMARY)
                .setOnClickListener { if (running) stopTracking() else startTracking() }
                .build()

        val pane =
            Pane
                .Builder()
                .addRow(row(R.string.car_row_name, name))
                .addRow(row(R.string.car_row_interval, frequency))
                .addRow(
                    row(
                        R.string.car_row_status,
                        carContext.getString(if (running) R.string.car_status_running else R.string.car_status_stopped),
                    ),
                ).addAction(toggleAction)
                .build()

        return PaneTemplate
            .Builder(pane)
            .setHeader(header())
            .build()
    }

    private fun header() =
        Header
            .Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setStartHeaderAction(Action.APP_ICON)
            .build()

    private fun row(
        titleRes: Int,
        value: String,
    ) = Row
        .Builder()
        .setTitle(carContext.getString(titleRes))
        .addText(value)
        .build()

    // LocationService works with either precise or approximate location.
    private fun hasRequiredPermissions() =
        (isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) &&
            isGranted(Manifest.permission.POST_NOTIFICATIONS)

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(carContext, permission) == PackageManager.PERMISSION_GRANTED

    // The running state is maintained by LocationService itself; the prefs
    // listener re-renders the screen once it flips.
    private fun startTracking() {
        // Plain startService (like MainActivity), not startForegroundService: the latter obliges the
        // service to reach the foreground, and a refused startForeground followed by stopSelf would
        // crash the app instead of reporting via APP_PREF_START_FAILED_AT.
        try {
            startRequested = true
            carContext.startService(Intent(carContext, LocationService::class.java))
        } catch (e: IllegalStateException) {
            // BackgroundServiceStartNotAllowedException: the app is not allowed to start from the background.
            Log.e("StalkScreen", "Could not start LocationService from the car", e)
            startRequested = false
            showStartOnPhoneToast()
        }
    }

    private fun showStartOnPhoneToast() {
        CarToast.makeText(carContext, R.string.car_start_on_phone, CarToast.LENGTH_LONG).show()
    }

    private fun stopTracking() {
        carContext.stopService(Intent(carContext, LocationService::class.java))
    }
}
