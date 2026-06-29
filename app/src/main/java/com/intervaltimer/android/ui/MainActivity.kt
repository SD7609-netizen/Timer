package com.intervaltimer.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.intervaltimer.android.databinding.ActivityMainBinding
import com.intervaltimer.android.service.TimerState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: TimerViewModel by viewModels()
    private lateinit var presetAdapter: PresetAdapter
    private var activePresetId: Long = -1L

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        requestOverlayPermission()
        setupPresetList()
        setupTimerControls()
        observeTimerState()
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else false
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Разрешение для виджета")
                .setMessage("Чтобы таймер отображался поверх других приложений, разреши это в настройках.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Пропустить", null)
                .show()
        }
    }

    private fun setupPresetList() {
        presetAdapter = PresetAdapter(
            onStart = { preset ->
                activePresetId = preset.id
                vm.startTimer(preset.id)
                showTimerScreen()
            },
            onEdit = { preset ->
                val intent = EditPresetActivity.newIntent(this, preset.id)
                startActivity(intent)
            },
            onDelete = { preset ->
                AlertDialog.Builder(this)
                    .setTitle("Удалить пресет?")
                    .setMessage("\"${preset.name}\" будет удалён")
                    .setPositiveButton("Удалить") { _, _ -> vm.deletePreset(preset) }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        binding.rvPresets.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = presetAdapter
        }

        lifecycleScope.launch {
            vm.presets.collectLatest { presetAdapter.submitList(it) }
        }

        binding.fabAddPreset.setOnClickListener {
            startActivity(EditPresetActivity.newIntent(this, -1L))
        }
    }

    private fun setupTimerControls() {
        binding.btnPause.setOnClickListener {
            val state = vm.timerState.value
            if (state is TimerState.Running && state.isPaused) vm.resume()
            else vm.pause()
        }
        binding.btnStop.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Остановить таймер?")
                .setPositiveButton("Стоп") { _, _ ->
                    vm.stop()
                    showPresetScreen()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun observeTimerState() {
        lifecycleScope.launch {
            vm.timerState.collectLatest { state ->
                when (state) {
                    is TimerState.Idle -> showPresetScreen()
                    is TimerState.Finished -> {
                        binding.tvIntervalName.text = "Готово!"
                        binding.tvTimer.text = "0:00"
                        binding.tvNextInterval.text = ""
                        binding.progressBar.progress = 0
                    }
                    is TimerState.Running -> updateTimerUI(state)
                }
            }
        }
    }

    private fun updateTimerUI(state: TimerState.Running) {
        showTimerScreen()
        val m = state.remainingSeconds / 60
        val s = state.remainingSeconds % 60
        val timeStr = "%d:%02d".format(m, s)

        if (binding.tvTimer.text != timeStr) {
            binding.tvTimer.text = timeStr
        }

        binding.tvIntervalName.text = state.intervalName
        binding.tvNextInterval.text = if (state.nextIntervalName == "—") {
            "Последний интервал"
        } else {
            "Следующий: ${state.nextIntervalName}"
        }

        val progress = ((state.totalSeconds - state.remainingSeconds).toFloat() / state.totalSeconds * 100).toInt()
        binding.progressBar.progress = progress

        val indexText = "${state.currentIndex + 1} / ${state.totalIntervals}"
        binding.tvIndexLabel.text = indexText

        if (state.isPaused) {
            binding.btnPause.text = "▶"
            binding.tvStatusLabel.text = "Пауза"
        } else {
            binding.btnPause.text = "⏸"
            binding.tvStatusLabel.text = "Активно"
        }
    }

    private fun showTimerScreen() {
        binding.layoutPresets.visibility = View.GONE
        binding.layoutTimer.visibility = View.VISIBLE
    }

    private fun showPresetScreen() {
        binding.layoutTimer.visibility = View.GONE
        binding.layoutPresets.visibility = View.VISIBLE
    }
}
