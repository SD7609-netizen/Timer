package com.intervaltimer.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
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
    }

    private lateinit var binding: ActivityEditPresetBinding
    private val vm: TimerViewModel by viewModels()
    private lateinit var intervalAdapter: IntervalEditAdapter
    private val intervals = mutableListOf<Interval>()
    private var presetId: Long = -1L
    private var existingPreset: Preset? = null
    private var selectedFinalSound = SoundType.FANFARE

    // File picker для кастомного звука (используется из диалога интервала)
    private var pendingCustomSoundCallback: ((String) -> Unit)? = null
    private val soundFilePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val path = copyAudioToInternal(uri)
        if (path != null) {
            pendingCustomSoundCallback?.invoke(path)
        } else {
            Toast.makeText(this, "Не удалось скопировать файл", Toast.LENGTH_SHORT).show()
        }
        pendingCustomSoundCallback = null
    }

    private fun copyAudioToInternal(uri: Uri): String? {
        return try {
            val dir = File(filesDir, "sounds").also { it.mkdirs() }
            val name = "sound_${System.currentTimeMillis()}.mp3"
            val dest = File(dir, name)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (_: Exception) { null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditPresetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presetId = intent.getLongExtra(EXTRA_PRESET_ID, -1L)

        setupIntervalList()
        setupFinalSoundSpinner()
        setupSaveButton()

        if (presetId != -1L) {
            loadPreset()
        } else {
            binding.toolbar.title = "Новый пресет"
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupIntervalList() {
        intervalAdapter = IntervalEditAdapter(intervals,
            onEdit = { pos -> showIntervalDialog(pos) },
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

        // Drag-to-reorder
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val f = from.adapterPosition
                val t = to.adapterPosition
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
        val sounds = SoundType.values().map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sounds)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFinalSound.adapter = adapter
        binding.spinnerFinalSound.setSelection(SoundType.FANFARE.ordinal)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val name = binding.etPresetName.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                binding.etPresetName.error = "Введите название"
                return@setOnClickListener
            }
            if (intervals.isEmpty()) {
                Toast.makeText(this, "Добавьте хотя бы один интервал", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedFinalSound = SoundType.values()[binding.spinnerFinalSound.selectedItemPosition]
            val vibration = binding.switchVibration.isChecked

            if (existingPreset != null) {
                val updated = existingPreset!!.copy(name = name, finalSoundType = selectedFinalSound, vibrationEnabled = vibration)
                vm.updatePreset(updated, intervals)
            } else {
                vm.savePreset(name, selectedFinalSound, vibration, intervals) {}
            }
            finish()
        }
    }

    private fun loadPreset() {
        binding.toolbar.title = "Редактировать"
        // We load preset data via ViewModel
        vm.loadIntervals(presetId) { loaded ->
            intervals.clear()
            intervals.addAll(loaded)
            intervalAdapter.notifyDataSetChanged()
        }
        // Load preset name from presets flow
        lifecycleScope.launch {
            vm.presets.collect { list ->
                val p = list.find { it.id == presetId }
                if (p != null && existingPreset == null) {
                    existingPreset = p
                    binding.etPresetName.setText(p.name)
                    binding.spinnerFinalSound.setSelection(p.finalSoundType.ordinal)
                    binding.switchVibration.isChecked = p.vibrationEnabled
                }
            }
        }
    }

    private fun showIntervalDialog(editPosition: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_interval, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etIntervalName)
        val etMinutes = view.findViewById<TextInputEditText>(R.id.etMinutes)
        val etSeconds = view.findViewById<TextInputEditText>(R.id.etSeconds)
        val spinnerSound = view.findViewById<android.widget.Spinner>(R.id.spinnerSound)
        val tvCustomLabel = view.findViewById<TextView>(R.id.tvCustomSoundLabel)
        val btnPickSound = view.findViewById<Button>(R.id.btnPickSound)

        var customSoundPath = ""

        val soundLabels = SoundType.values().filter { it != SoundType.FANFARE }.map { it.label }
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, soundLabels)
        soundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSound.adapter = soundAdapter

        if (editPosition >= 0) {
            val existing = intervals[editPosition]
            etName.setText(existing.name)
            etMinutes.setText((existing.durationSeconds / 60).toString())
            etSeconds.setText((existing.durationSeconds % 60).toString())
            val soundIndex = SoundType.values().filter { it != SoundType.FANFARE }.indexOf(existing.soundType)
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
                val mins = etMinutes.text?.toString()?.toIntOrNull() ?: 0
                val secs = etSeconds.text?.toString()?.toIntOrNull() ?: 0
                val total = mins * 60 + secs
                if (total <= 0) {
                    Toast.makeText(this, "Длительность должна быть > 0", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val availableSounds = SoundType.values().filter { it != SoundType.FANFARE }
                val sound = availableSounds[spinnerSound.selectedItemPosition]
                val interval = Interval(
                    presetId = presetId,
                    position = editPosition.coerceAtLeast(0),
                    name = name,
                    durationSeconds = total,
                    soundType = sound,
                    customSoundPath = customSoundPath
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

    private fun showAutoSeriesDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_auto_series, null)
        val etTotal = view.findViewById<TextInputEditText>(R.id.etTotalMinutes)
        val etIntMin = view.findViewById<TextInputEditText>(R.id.etIntervalMinutes)
        val etIntSec = view.findViewById<TextInputEditText>(R.id.etIntervalSeconds)
        val etName = view.findViewById<TextInputEditText>(R.id.etSeriesName)
        val spinner = view.findViewById<android.widget.Spinner>(R.id.spinnerSeriesSound)
        val tvPreview = view.findViewById<android.widget.TextView>(R.id.tvSeriesPreview)

        val availableSounds = SoundType.values().filter { it != SoundType.FANFARE }
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableSounds.map { it.label })
        soundAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = soundAdapter

        fun updatePreview() {
            val totalSec = (etTotal.text?.toString()?.toIntOrNull() ?: 0) * 60
            val intSec = (etIntMin.text?.toString()?.toIntOrNull() ?: 0) * 60 +
                    (etIntSec.text?.toString()?.toIntOrNull() ?: 0)
            if (intSec > 0 && totalSec > 0) {
                val count = totalSec / intSec
                val rem = totalSec % intSec
                val intLabel = if (intSec >= 60) "${intSec/60} мин" else "${intSec} сек"
                tvPreview.text = "Будет создано: $count интервалов по $intLabel" +
                        (if (rem > 0) " + остаток ${rem}с" else "")
            } else {
                tvPreview.text = "Укажите корректные значения"
            }
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
                val intSec = (etIntMin.text?.toString()?.toIntOrNull() ?: 0) * 60 +
                        (etIntSec.text?.toString()?.toIntOrNull() ?: 0)
                val name = etName.text?.toString()?.trim()?.ifEmpty { "Интервал" } ?: "Интервал"
                val sound = availableSounds[spinner.selectedItemPosition]

                if (intSec <= 0 || totalSec <= 0) {
                    Toast.makeText(this, "Некорректные значения", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val count = totalSec / intSec
                if (count <= 0) {
                    Toast.makeText(this, "Интервал больше общего времени", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val startPos = intervals.size
                repeat(count) { i ->
                    intervals.add(Interval(
                        presetId = presetId,
                        position = startPos + i,
                        name = if (count > 1) "$name ${startPos + i + 1}" else name,
                        durationSeconds = intSec,
                        soundType = sound
                    ))
                }
                intervalAdapter.notifyItemRangeInserted(startPos, count)
                Toast.makeText(this, "Добавлено $count интервалов", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
