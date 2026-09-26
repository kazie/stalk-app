package se.araisan.stalk.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

const val APP_PREFS_NAME = "app_prefs"

fun Context.appPrefs(): SharedPreferences = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)

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
