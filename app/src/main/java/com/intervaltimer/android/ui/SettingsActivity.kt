package com.intervaltimer.android.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.intervaltimer.android.databinding.ActivitySettingsBinding
import com.intervaltimer.android.service.TimerService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences(TimerService.PREFS_NAME, MODE_PRIVATE)

        binding.switchKeepScreen.isChecked = prefs.getBoolean(TimerService.PREF_KEEP_SCREEN, false)
        binding.switchKeepScreen.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(TimerService.PREF_KEEP_SCREEN, checked).apply()
        }

        val sizeKeys   = listOf("small", "medium", "large")
        val sizeLabels = listOf("Маленький (100dp)", "Средний (130dp)", "Большой (160dp)")
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sizeLabels)
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOverlaySize.adapter = sizeAdapter
        val currentSize = prefs.getString(TimerService.PREF_OVERLAY_SIZE, "medium")
        binding.spinnerOverlaySize.setSelection(sizeKeys.indexOf(currentSize).coerceAtLeast(0))

        binding.btnSaveSettings.setOnClickListener {
            prefs.edit()
                .putString(TimerService.PREF_OVERLAY_SIZE, sizeKeys[binding.spinnerOverlaySize.selectedItemPosition])
                .apply()
            finish()
        }
    }
}
