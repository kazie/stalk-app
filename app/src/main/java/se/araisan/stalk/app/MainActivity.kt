package se.araisan.stalk.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import android.widget.Toast
import android.os.Handler
import android.os.Looper

const val APP_PREF_STALK_FREQ = "stalk_frequency"
const val APP_PREF_USER_NAME = "user_name"
const val APP_PREF_SERVICE_RUNNING = "service_running"
const val APP_PREF_DATA_EXISTS = "data_exists"
const val APP_PREF_LAST_CHECKED_NAME = "last_checked_name"

class MainActivity : AppCompatActivity() {
    private var isServiceRunning = false // service state.

    private lateinit var nameEditText: EditText // Reference to the input field
    private lateinit var toggleButton: Button // Reference to the button
    private lateinit var intervalSpinner: Spinner // Reference to the frequency spinner
    private lateinit var deleteButton: Button // Delete my data button

    private val disabledColor =
        ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled), // Disabled state
                intArrayOf(android.R.attr.state_pressed), // Pressed state
                intArrayOf(), // Default state
            ),
            intArrayOf(
                "#CCCCCC".toColorInt(), // Light gray for disabled
                "#008800".toColorInt(), // Dark green for pressed
                "#009900".toColorInt(), // Normal green
            ),
        )

    private val enabledColor =
        ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled), // Disabled state
                intArrayOf(android.R.attr.state_pressed), // Pressed state
                intArrayOf(), // Default state
            ),
            intArrayOf(
                "#CCCCCC".toColorInt(), // Light gray for disabled
                "#880000".toColorInt(), // Dark red for pressed
                "#990000".toColorInt(), // Normal red
            ),
        )

    private val neutralDisabledColor = ColorStateList.valueOf("#666666".toColorInt())

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to the layout XML file
        setContentView(R.layout.activity_main)

        intervalSpinner = findViewById(R.id.intervalSpinner)
        // Create an ArrayAdapter using the string array and a simple spinner layout
        ArrayAdapter
            .createFromResource(
                this,
                R.array.time_intervals,
                android.R.layout.simple_spinner_item,
            ).also { adapter ->
                // Specify the layout to use when the list of choices appears
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                intervalSpinner.adapter = adapter
            }
        // Set a listener to capture selected values and save them in SharedPreferences
        createSpinnerListener(intervalSpinner)
        val savedInterval = getFrequency()
        val items = resources.getStringArray(R.array.time_intervals)
        val savedPosition = items.indexOf(savedInterval)
        if (savedPosition >= 0) {
            intervalSpinner.setSelection(savedPosition)
        }

        nameEditText = findViewById(R.id.nameEditText)
        toggleButton = findViewById(R.id.start_button)
        deleteButton = findViewById(R.id.delete_button)

        enableEdgeToEdge()

        // Load the saved name from SharedPreferences
        val savedName = getSavedName()
        nameEditText.setText(savedName)

        // Enable the button if there's already a valid input
        toggleButton.isEnabled = savedName.isNotEmpty()
        toggleButton.backgroundTintList = this.disabledColor

        // Restore UI state based on whether service is running
        isServiceRunning = getServiceRunning()

        // Initialize delete button state
        updateUiForServiceState()

        // Add a TextWatcher to the EditText to detect real-time changes
        nameEditText.addTextChangedListener(createTextWatcher())

        toggleButton.setOnClickListener {
            buttonClickListener(toggleButton)
        }

        deleteButton.setOnClickListener {
            onDeleteClicked()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh UI if service state changed while we were paused
        val running = getServiceRunning()
        if (running != isServiceRunning) {
            isServiceRunning = running
            updateUiForServiceState()
        }
    }

    private fun createSpinnerListener(intervalSpinner: Spinner) {
        intervalSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    // Get the selected value
                    val selectedInterval = parent.getItemAtPosition(position).toString()

                    // Save the selected interval into SharedPreferences
                    saveFrequency(selectedInterval)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // Handle the case where no selection is made (optional)
                }
            }
    }

    private fun createTextWatcher() =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
                toggleButton.isEnabled = !s.isNullOrEmpty() // Enable button only when input isn't empty
                // Debounce existence check on name change when not running
                scheduleExistenceCheckDebounced()
            }

            override fun afterTextChanged(s: Editable?) {
                saveName(s?.toString()) // Save the name in SharedPreferences
            }
        }

    private fun buttonClickListener(button: Button) {
        if (!isServiceRunning) {
            Log.d("MainActivity", "Start button clicked") // Verify this log appears
            if (checkAndRequestPermissions()) {
                val intent = Intent(this, LocationService::class.java)
                startService(intent)
                isServiceRunning = true
                saveServiceRunning(true)
                updateUiForServiceState()
            } else {
                Log.d("MainActivity", "Permissions not granted")
            }
        } else {
            Log.d("MainActivity", "Stop button clicked") // Verify this log appears
            val intent = Intent(this, LocationService::class.java)
            stopService(intent)
            isServiceRunning = false
            saveServiceRunning(false)
            updateUiForServiceState()
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.FOREGROUND_SERVICE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(android.Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        }

        return if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            false
        } else {
            true
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            permissions.forEach { (permission, isGranted) ->
                Log.d("MainActivity", "$permission granted: $isGranted")
            }
        }

    // Helper method to save the name input into SharedPreferences
    private fun saveName(name: String?) {
        if (name == null) return
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPreferences.edit {
            putString(APP_PREF_USER_NAME, name)
        }
    }

    // Helper method to retrieve the saved name
    private fun getSavedName(): String {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPreferences.getString(APP_PREF_USER_NAME, "") ?: ""
    }

    // Helper method to save the frequency input into SharedPreferences
    private fun saveFrequency(name: String?) {
        if (name == null) return
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPreferences.edit {
            putString(APP_PREF_STALK_FREQ, name)
        }
    }

    // Helper method to retrieve the saved frequency
    private fun getFrequency(): String {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPreferences.getString(APP_PREF_STALK_FREQ, "10s") ?: "10s"
    }

    private fun saveServiceRunning(running: Boolean) {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPreferences.edit {
            putBoolean(APP_PREF_SERVICE_RUNNING, running)
        }
    }

    private fun getServiceRunning(): Boolean {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return sharedPreferences.getBoolean(APP_PREF_SERVICE_RUNNING, false)
    }

    private fun updateUiForServiceState() {
        // Toggle button appearance and text
        if (isServiceRunning) {
            toggleButton.text = "Stop stalking"
            toggleButton.backgroundTintList = this.enabledColor
        } else {
            toggleButton.text = "Start stalking"
            toggleButton.backgroundTintList = this.disabledColor
        }

        // Disable/enable inputs per requirement
        nameEditText.isEnabled = !isServiceRunning
        intervalSpinner.isEnabled = !isServiceRunning

        // Update delete button enablement and tint
        updateDeleteButtonEnabled()
    }

    private fun updateDeleteButtonEnabled() {
        val currentName = nameEditText.text?.toString() ?: ""
        val enabled = computeDeleteEnabled(currentName)
        deleteButton.isEnabled = enabled
        deleteButton.backgroundTintList = if (enabled) enabledColor else neutralDisabledColor
    }

    private fun computeDeleteEnabled(currentName: String): Boolean {
        if (currentName.isEmpty()) return false
        if (isServiceRunning) return false
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val last = prefs.getString(APP_PREF_LAST_CHECKED_NAME, null)
        val exists = prefs.getBoolean(APP_PREF_DATA_EXISTS, false)
        return last == currentName && exists
    }

    private fun scheduleExistenceCheckDebounced() {
        pendingCheck?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val name = nameEditText.text?.toString() ?: ""
            if (name.isEmpty() || isServiceRunning) {
                runOnUiThread { updateDeleteButtonEnabled() }
                return@Runnable
            }
            Thread {
                val exists = ApiClient.checkUserHasData(name)
                saveDataExistence(name, exists)
                runOnUiThread { updateDeleteButtonEnabled() }
            }.start()
        }
        pendingCheck = runnable
        mainHandler.postDelayed(runnable, 500)
    }

    private fun saveDataExistence(name: String, exists: Boolean) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit {
            putString(APP_PREF_LAST_CHECKED_NAME, name)
            putBoolean(APP_PREF_DATA_EXISTS, exists)
        }
    }

    private fun onDeleteClicked() {
        val name = nameEditText.text?.toString()?.trim().orEmpty()
        if (name.isEmpty() || !deleteButton.isEnabled) return
        deleteButton.isEnabled = false
        Thread {
            val ok = ApiClient.deleteUserData(name)
            if (ok) {
                saveDataExistence(name, false)
            }
            runOnUiThread {
                if (ok) {
                    Toast.makeText(this, "Deleted data", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
                updateDeleteButtonEnabled()
            }
        }.start()
    }
}
