package com.intervaltimer.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.intervaltimer.android.R
import com.intervaltimer.android.data.Interval
import com.intervaltimer.android.data.IntervalType
import com.intervaltimer.android.data.Preset
import com.intervaltimer.android.data.SoundType
import com.intervaltimer.android.databinding.ActivityEditPresetBinding
import java.io.File
import java.io.FileOutputStream

class EditPresetActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PRESET_ID = "preset_id"
        fun newIntent(ctx: Context, presetId: Long) =
            Intent(ctx, EditPresetActivity::class.java).putExtra(EXTRA_PRESET_ID, presetId)

        val LOOP_VALUES  = listOf(0, 2, 3, 5, 10, -1)
        val LOOP_LABELS  = listOf("Без повтора", "2 раза", "3 раза", "5 раз", "10 раз", "Бесконечно")
        val WARN_VALUES  = listOf(0, 3, 5, 10, 15, 30)
        val WARN_LABELS  = listOf("Нет", "за 3 сек", "за 5 сек", "за 10 сек", "за 15 сек", "за 30 сек")
    }

    private lateinit var binding: ActivityEditPresetBinding
    private val vm: TimerViewModel by viewModels()
    private lateinit var intervalAdapter: IntervalEditAdapter
    private val intervals = mutableListOf<Interval>()
    private var presetId: Long = -1L
    private var existingPreset: Preset? = null
    private var selectedFinalSound = SoundType.FANFARE

    private var pendingCustomSoundCallback: ((String) -> Unit)? = null
    private val soundFilePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val path = copyAudioToInternal(uri)
        if (path != null) pendingCustomSoundCallback?.invoke(path)
        else Toast.makeText(this, "Не удалось скопировать файл", Toast.LENGTH_SHORT).show()
        pendingCustomSoundCallback = null
    }

    private fun copyAudioToInternal(uri: Uri): String? = try {
        val dir = File(filesDir, "sounds").also { it.mkdirs() }
        val dest = File(dir, "sound_${System.currentTimeMillis()}.mp3")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (_: Exception) { null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPresetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presetId = intent.getLongExtra(EXTRA_PRESET_ID, -1L)

        setupIntervalList()
        setupFinalSoundSpinner()
        setupWarningSpinner()
        setupLoopSpinner()
        setupSaveButton()

        if (presetId != -1L) loadPreset()
        else binding.toolbar.title = "Новый пресет"

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupIntervalList() {
        intervalAdapter = IntervalEditAdapter(intervals,
            onEdit   = { pos -> showIntervalDialog(pos) },
            onDelete = { pos ->
                intervals.removeAt(pos)
                intervalAdapter.notifyItemRemoved(pos)
                intervalAdapter.notifyItemRangeChanged(pos, intervals.size)
            }
        )
        binding.rvIntervals.apply {
            layoutManager = LinearLayoutManager(this@EditPresetActivity)
            adapter = intervalAdapter
        }
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val f = from.adapterPosition; val t = to.adapterPosition
                intervals.add(t, intervals.removeAt(f))
                intervalAdapter.notifyItemMoved(f, t)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvIntervals)
        binding.btnAddInterval.setOnClickListener { showIntervalDialog(-1) }
        binding.btnAutoGenerate.setOnClickListener { showAutoSeriesDialog() }
    }

    private fun setupFinalSoundSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, SoundType.values().map { it.label })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFinalSound.adapter = adapter
        binding.spinnerFinalSound.setSelection(SoundType.FANFARE.ordinal)
    }

    private fun setupWarningSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, WARN_LABELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerWarning.adapter = adapter
        binding.spinnerWarning.setSelection(WARN_VALUES.indexOf(10).coerceAtLeast(0))
    }

    private fun setupLoopSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LOOP_LABELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLoop.adapter = adapter
        binding.spinnerLoop.setSelection(0)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val name = binding.etPresetName.text?.toString()?.trim()
            if (name.isNullOrEmpty()) { binding.etPresetName.error = "Введите название"; return@setOnClickListener }
            if (intervals.isEmpty()) { Toast.makeText(this, "Добавьте хотя бы один интервал", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

            selectedFinalSound = SoundType.values()[binding.spinnerFinalSound.selectedItemPosition]
            val vibration = binding.switchVibration.isChecked
            val loopCount = LOOP_VALUES[binding.spinnerLoop.selectedItemPosition]
            val warnSecs  = WARN_VALUES[binding.spinnerWarning.selectedItemPosition]

            if (existingPreset != null) {
                val updated = existingPreset!!.copy(
                    name = name, finalSoundType = selectedFinalSound,
                    vibrationEnabled = vibration, loopCount = loopCount, warningSeconds = warnSecs
                )
                vm.updatePreset(updated, intervals)
            } else {
                vm.savePreset(name, selectedFinalSound, vibration, loopCount, warnSecs, intervals) {}
            }
            finish()
        }
    }

    private fun loadPreset() {
        binding.toolbar.title = "Редактировать"
        vm.loadIntervals(presetId) { loaded ->
            intervals.clear()
            intervals.addAll(loaded)
            intervalAdapter.notifyDataSetChanged()
        }
        lifecycleScope.launch {
            vm.presets.collect { list ->
                val p = list.find { it.id == presetId }
                if (p != null && existingPreset == null) {
                    existingPreset = p
                    binding.etPresetName.setText(p.name)
                    binding.spinnerFinalSound.setSelection(p.finalSoundType.ordinal)
                    binding.switchVibration.isChecked = p.vibrationEnabled
                    val loopIdx = LOOP_VALUES.indexOf(p.loopCount).coerceAtLeast(0)
                    binding.spinnerLoop.setSelection(loopIdx)
                    val warnIdx = WARN_VALUES.indexOf(p.warningSeconds).coerceAtLeast(0)
                    binding.spinnerWarning.setSelection(warnIdx)
                }
            }
        }
    }

    // ── Interval dialog ───────────────────────────────────────────

    private fun showIntervalDialog(editPosition: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_interval, null)
        val etName        = view.findViewById<TextInputEditText>(R.id.etIntervalName)
        val etMinutes     = view.findViewById<TextInputEditText>(R.id.etMinutes)
        val etSeconds     = view.findViewById<TextInputEditText>(R.id.etSeconds)
        val spinnerType   = view.findViewById<Spinner>(R.id.spinnerIntervalType)
        val spinnerSound  = view.findViewById<Spinner>(R.id.spinnerSound)
        val tvCustomLabel = view.findViewById<TextView>(R.id.tvCustomSoundLabel)
        val btnPickSound  = view.findViewById<Button>(R.id.btnPickSound)

        var customSoundPath = ""

        // Тип интервала
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            IntervalType.values().map { it.label })
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = typeAdapter

        // Звуки (без FANFARE)
        val availableSounds = SoundType.values().filter { it != SoundType.FANFARE }
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableSounds.map { it.label })
        soundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSound.adapter = soundAdapter

        if (editPosition >= 0) {
            val existing = intervals[editPosition]
            etName.setText(existing.name)
            etMinutes.setText((existing.durationSeconds / 60).toString())
            etSeconds.setText((existing.durationSeconds % 60).toString())
            spinnerType.setSelection(existing.intervalType.ordinal)
            val soundIndex = availableSounds.indexOf(existing.soundType)
            if (soundIndex >= 0) spinnerSound.setSelection(soundIndex)
            if (existing.customSoundPath.isNotEmpty()) {
                customSoundPath = existing.customSoundPath
                tvCustomLabel.text = "Свой: ${File(existing.customSoundPath).name}"
            }
        }

        btnPickSound.setOnClickListener {
            pendingCustomSoundCallback = { path ->
                customSoundPath = path
                tvCustomLabel.text = "Свой: ${File(path).name}"
            }
            soundFilePicker.launch("audio/*")
        }

        AlertDialog.Builder(this)
            .setTitle(if (editPosition >= 0) "Редактировать интервал" else "Добавить интервал")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val name = etName.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) return@setPositiveButton
                val mins  = etMinutes.text?.toString()?.toIntOrNull() ?: 0
                val secs  = etSeconds.text?.toString()?.toIntOrNull() ?: 0
                val total = mins * 60 + secs
                if (total <= 0) { Toast.makeText(this, "Длительность должна быть > 0", Toast.LENGTH_SHORT).show(); return@setPositiveButton }

                val intervalType = IntervalType.values()[spinnerType.selectedItemPosition]
                val sound        = availableSounds[spinnerSound.selectedItemPosition]
                val interval = Interval(
                    presetId       = presetId,
                    position       = editPosition.coerceAtLeast(0),
                    name           = name,
                    durationSeconds = total,
                    soundType      = sound,
                    customSoundPath = customSoundPath,
                    intervalType   = intervalType
                )
                if (editPosition >= 0) {
                    intervals[editPosition] = interval
                    intervalAdapter.notifyItemChanged(editPosition)
                } else {
                    intervals.add(interval)
                    intervalAdapter.notifyItemInserted(intervals.size - 1)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ── Auto-series dialog ─────────────────────────────────────────

    private fun showAutoSeriesDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auto_series, null)
        val etTotal   = view.findViewById<TextInputEditText>(R.id.etTotalMinutes)
        val etIntMin  = view.findViewById<TextInputEditText>(R.id.etIntervalMinutes)
        val etIntSec  = view.findViewById<TextInputEditText>(R.id.etIntervalSeconds)
        val etName    = view.findViewById<TextInputEditText>(R.id.etSeriesName)
        val spinner   = view.findViewById<Spinner>(R.id.spinnerSeriesSound)
        val tvPreview = view.findViewById<TextView>(R.id.tvSeriesPreview)

        val availableSounds = SoundType.values().filter { it != SoundType.FANFARE }
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableSounds.map { it.label })
        soundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = soundAdapter

        fun updatePreview() {
            val totalSec = (etTotal.text?.toString()?.toIntOrNull() ?: 0) * 60
            val intSec   = (etIntMin.text?.toString()?.toIntOrNull() ?: 0) * 60 +
                           (etIntSec.text?.toString()?.toIntOrNull() ?: 0)
            if (intSec > 0 && totalSec > 0) {
                val count = totalSec / intSec
                val rem   = totalSec % intSec
                val label = if (intSec >= 60) "${intSec/60} мин" else "${intSec} сек"
                tvPreview.text = "Будет создано: $count интервалов по $label" +
                    (if (rem > 0) " + остаток ${rem}с" else "")
            } else tvPreview.text = "Укажите корректные значения"
        }
        updatePreview()

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updatePreview()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        etTotal.addTextChangedListener(watcher)
        etIntMin.addTextChangedListener(watcher)
        etIntSec.addTextChangedListener(watcher)

        AlertDialog.Builder(this)
            .setTitle("Авто-серия интервалов")
            .setView(view)
            .setPositiveButton("Создать") { _, _ ->
                val totalSec = (etTotal.text?.toString()?.toIntOrNull() ?: 0) * 60
                val intSec   = (etIntMin.text?.toString()?.toIntOrNull() ?: 0) * 60 +
                               (etIntSec.text?.toString()?.toIntOrNull() ?: 0)
                val name     = etName.text?.toString()?.trim()?.ifEmpty { "Интервал" } ?: "Интервал"
                val sound    = availableSounds[spinner.selectedItemPosition]
                if (intSec <= 0 || totalSec <= 0) { Toast.makeText(this, "Некорректные значения", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val count = totalSec / intSec
                if (count <= 0) { Toast.makeText(this, "Интервал больше общего времени", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val startPos = intervals.size
                repeat(count) { i ->
                    intervals.add(Interval(
                        presetId = presetId, position = startPos + i,
                        name = if (count > 1) "$name ${startPos + i + 1}" else name,
                        durationSeconds = intSec, soundType = sound
                    ))
                }
                intervalAdapter.notifyItemRangeInserted(startPos, count)
                Toast.makeText(this, "Добавлено $count интервалов", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
