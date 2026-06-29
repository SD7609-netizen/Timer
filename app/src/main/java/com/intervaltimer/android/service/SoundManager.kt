package com.intervaltimer.android.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import com.intervaltimer.android.data.SoundType
import kotlin.math.PI
import kotlin.math.sin

class SoundManager(private val context: Context) {

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    fun play(soundType: SoundType, vibrate: Boolean = true) {
        Thread {
            when (soundType) {
                SoundType.BEEP_SINGLE -> playTone(880.0, 200)
                SoundType.BEEP_DOUBLE -> { playTone(880.0, 150); Thread.sleep(100); playTone(880.0, 150) }
                SoundType.BELL -> playBell()
                SoundType.WHISTLE -> playWhistle()
                SoundType.CHIME -> playChime()
                SoundType.FANFARE -> playFanfare()
            }
        }.start()

        if (vibrate) {
            val pattern = when (soundType) {
                SoundType.FANFARE -> longArrayOf(0, 200, 100, 200, 100, 400)
                SoundType.BEEP_DOUBLE -> longArrayOf(0, 100, 80, 100)
                else -> longArrayOf(0, 200)
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun playTone(frequency: Double, durationMs: Int, amplitude: Float = 0.8f) {
        val sampleRate = 44100
        val samples = sampleRate * durationMs / 1000
        val buffer = ShortArray(samples)
        val fadeLen = minOf(samples / 10, 2205)

        for (i in 0 until samples) {
            val sample = (sin(2.0 * PI * frequency * i / sampleRate) * amplitude * Short.MAX_VALUE).toInt().toShort()
            val fade = when {
                i < fadeLen -> i.toFloat() / fadeLen
                i > samples - fadeLen -> (samples - i).toFloat() / fadeLen
                else -> 1f
            }
            buffer[i] = (sample * fade).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        Thread.sleep(durationMs.toLong() + 50)
        track.stop()
        track.release()
    }

    private fun playBell() {
        // Имитация колокола: убывающий сигнал с несколькими гармониками
        val sampleRate = 44100
        val durationMs = 800
        val samples = sampleRate * durationMs / 1000
        val buffer = ShortArray(samples)

        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val decay = Math.exp(-t * 3.0)
            val sample = ((sin(2 * PI * 660 * t) * 0.5 +
                    sin(2 * PI * 990 * t) * 0.3 +
                    sin(2 * PI * 1320 * t) * 0.2) * decay * 0.8 * Short.MAX_VALUE).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeAndPlay(buffer, sampleRate, durationMs)
    }

    private fun playWhistle() {
        // Свисток: быстро нарастает, держится, убывает
        val sampleRate = 44100
        val durationMs = 400
        val samples = sampleRate * durationMs / 1000
        val buffer = ShortArray(samples)

        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val freq = 1800.0 + 200.0 * sin(2 * PI * 6 * t)
            val env = when {
                i < samples / 5 -> i.toFloat() / (samples / 5)
                i > samples * 4 / 5 -> (samples - i).toFloat() / (samples / 5)
                else -> 1f
            }
            buffer[i] = (sin(2 * PI * freq * t) * env * 0.9 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        writeAndPlay(buffer, sampleRate, durationMs)
    }

    private fun playChime() {
        // Колокольчик: три ноты
        playTone(523.25, 200, 0.7f)
        Thread.sleep(50)
        playTone(659.25, 200, 0.7f)
        Thread.sleep(50)
        playTone(783.99, 300, 0.7f)
    }

    private fun playFanfare() {
        // Финальный сигнал: восходящая последовательность
        val notes = listOf(523.25, 659.25, 783.99, 1046.5)
        notes.forEach { freq ->
            playTone(freq, 180, 0.9f)
            Thread.sleep(20)
        }
        Thread.sleep(50)
        playTone(1046.5, 500, 0.9f)
    }

    private fun writeAndPlay(buffer: ShortArray, sampleRate: Int, durationMs: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        Thread.sleep(durationMs.toLong() + 50)
        track.stop()
        track.release()
    }
}
