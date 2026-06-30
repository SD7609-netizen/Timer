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

        // Keep screen on
        binding.switchKeepScreen.isChecked = prefs.getBoolean(TimerService.PREF_KEEP_SCREEN, false)
        binding.switchKeepScreen.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(TimerService.PREF_KEEP_SCREEN, checked).apply()
        }

        // Overlay size
        val sizeKeys   = listOf("small", "medium", "large")
        val sizeLabels = listOf("Маленький (100dp)", "Средний (130dp)", "Большой (160dp)")
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sizeLabels)
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOverlaySize.adapter = sizeAdapter
        val currentSize = prefs.getString(TimerService.PREF_OVERLAY_SIZE, "medium")
        binding.spinnerOverlaySize.setSelection(sizeKeys.indexOf(currentSize).coerceAtLeast(0))

        // Overlay style
        val overlayStyleKeys   = listOf("1", "2", "3", "4")
        val overlayStyleLabels = listOf(
            "1 — Классик: дуга + метки + имя",
            "2 — Минимал: только время и дуга",
            "3 — Неон: тёмный фон, толстая дуга",
            "4 — Стекло: полупрозрачный"
        )
        val overlayStyleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, overlayStyleLabels)
        overlayStyleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOverlayStyle.adapter = overlayStyleAdapter
        val currentOverlayStyle = prefs.getString(TimerService.PREF_OVERLAY_STYLE, "1")
        binding.spinnerOverlayStyle.setSelection(overlayStyleKeys.indexOf(currentOverlayStyle).coerceAtLeast(0))

        // Widget style
        val widgetKeys = listOf("1", "2", "3", "4", "5", "6")
        val widgetLabels = listOf(
            "1 — Минимал: только время",
            "2 — Классик: имя + время + прогресс",
            "3 — Полный: имя + счётчик + время + прогресс",
            "4 — Широкий: имя слева, время справа",
            "5 — Акцент: жёлтый фон",
            "6 — Компакт: одна строка"
        )
        val widgetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, widgetLabels)
        widgetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerWidgetStyle.adapter = widgetAdapter
        val currentStyle = prefs.getString(TimerService.PREF_WIDGET_STYLE, "2")
        binding.spinnerWidgetStyle.setSelection(widgetKeys.indexOf(currentStyle).coerceAtLeast(0))

        binding.btnSaveSettings.setOnClickListener {
            prefs.edit()
                .putString(TimerService.PREF_OVERLAY_SIZE,  sizeKeys[binding.spinnerOverlaySize.selectedItemPosition])
                .putString(TimerService.PREF_OVERLAY_STYLE, overlayStyleKeys[binding.spinnerOverlayStyle.selectedItemPosition])
                .putString(TimerService.PREF_WIDGET_STYLE,  widgetKeys[binding.spinnerWidgetStyle.selectedItemPosition])
                .apply()
            finish()
        }
    }
}
