package se.araisan.stalk.app

import android.content.Context
import android.content.SharedPreferences

const val APP_PREFS_NAME = "app_prefs"

fun Context.appPrefs(): SharedPreferences = getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
