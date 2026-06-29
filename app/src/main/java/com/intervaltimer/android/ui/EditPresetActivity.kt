package com.intervaltimer.android.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
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
                    soundType = sound
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
}
