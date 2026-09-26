package se.araisan.stalk.app.car

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for Android Auto. The car host binds to this service and renders
 * the templates returned by [StalkScreen] on the car display.
 */
class StalkCarAppService : CarAppService() {
    // hosts_allowlist_sample is marked private but is Google's documented allowlist of trusted hosts.
    @SuppressLint("PrivateResource")
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // Allows the Desktop Head Unit and sideloaded hosts during development.
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator
                .Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = StalkSession()
}

class StalkSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = StalkScreen(carContext)
}
