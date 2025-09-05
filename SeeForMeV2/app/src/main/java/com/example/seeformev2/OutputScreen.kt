package com.example.seeformev2

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OutputScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_output_screen) // Create this layout

        // Get the status from the intent
        val status = intent.getStringExtra("STATUS") ?: "No status available"

        // Display status on a TextView
        findViewById<TextView>(R.id.statusTextView).text = status
    }
}
