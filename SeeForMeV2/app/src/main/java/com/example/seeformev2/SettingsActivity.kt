package com.example.seeformev2

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {

    private lateinit var enableTTS: Switch
    private lateinit var volumeControl: SeekBar
    private lateinit var saveSettingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        enableTTS = findViewById(R.id.enableTTS)
        volumeControl = findViewById(R.id.volumeControl)
        saveSettingsButton = findViewById(R.id.saveSettingsButton)

        // ✅ Load saved settings
        loadSettings()

        saveSettingsButton.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish() // ✅ Close settings page after saving
        }
    }

    private fun loadSettings() {
        val sharedPref = getSharedPreferences("SeeForMePrefs", Context.MODE_PRIVATE)
        enableTTS.isChecked = sharedPref.getBoolean("ENABLE_TTS", true)
        volumeControl.progress = sharedPref.getInt("VOLUME", 50)
    }

    private fun saveSettings() {
        val sharedPref = getSharedPreferences("SeeForMePrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("ENABLE_TTS", enableTTS.isChecked)
            putInt("VOLUME", volumeControl.progress)
            apply()
        }
    }
}
