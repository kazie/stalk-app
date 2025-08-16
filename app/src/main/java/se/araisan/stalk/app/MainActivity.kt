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

const val APP_PREF_STALK_FREQ = "stalk_frequency"
const val APP_PREF_USER_NAME = "user_name"

class MainActivity : AppCompatActivity() {
    private var isServiceRunning = false // service state.

    private lateinit var nameEditText: EditText // Reference to the input field
    private lateinit var toggleButton: Button // Reference to the button

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to the layout XML file
        setContentView(R.layout.activity_main)

        val intervalSpinner: Spinner = findViewById(R.id.intervalSpinner)
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

        enableEdgeToEdge()

        // Load the saved name from SharedPreferences
        val savedName = getSavedName()
        nameEditText.setText(savedName)

        // Enable the button if there's already a valid input
        toggleButton.isEnabled = savedName.isNotEmpty()
        toggleButton.backgroundTintList = this.disabledColor

        // Add a TextWatcher to the EditText to detect real-time changes
        nameEditText.addTextChangedListener(createTextWatcher())

        toggleButton.setOnClickListener {
            buttonClickListener(toggleButton)
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
                button.text = "Stop stalking"
                button.setBackgroundColor(0x990000)
                button.backgroundTintList = this.enabledColor
            } else {
                Log.d("MainActivity", "Permissions not granted")
            }
        } else {
            Log.d("MainActivity", "Stop button clicked") // Verify this log appears
            val intent = Intent(this, LocationService::class.java)
            stopService(intent)
            isServiceRunning = false
            button.text = "Start stalking"
            button.backgroundTintList = this.disabledColor
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
}
