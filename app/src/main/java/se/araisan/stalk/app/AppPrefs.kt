package se.araisan.stalk.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

const val APP_PREFS_NAME = "app_prefs"

/** Whether speed and heading are sent with each location. */
const val APP_PREF_SEND_SPEED_HEADING = "send_speed_heading"

fun Context.appPrefs(): SharedPreferences = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)

/** Speed and heading are sent unless the user turned it off. */
fun SharedPreferences.sendSpeedHeading(): Boolean = getBoolean(APP_PREF_SEND_SPEED_HEADING, true)

/**
 * Clears APP_PREF_SERVICE_RUNNING if it was left at true by a process killed without
 * LocationService.onDestroy. Otherwise the next start would write true over true, which
 * SharedPreferences does not report to listeners, and screens would miss the change.
 */
fun Context.clearStaleServiceRunningFlag() {
    val prefs = appPrefs()
    if (!LocationService.isRunning && prefs.getBoolean(APP_PREF_SERVICE_RUNNING, false)) {
        prefs.edit { putBoolean(APP_PREF_SERVICE_RUNNING, false) }
    }
}
