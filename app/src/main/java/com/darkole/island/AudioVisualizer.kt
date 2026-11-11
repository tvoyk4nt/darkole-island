
package com.darkole.island

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt
import kotlin.random.Random

object AudioVisualizer {

    private const val TAG = "DarkoleIsland"

    private val _levels = MutableStateFlow(List(4) { 0f })
    val levels: StateFlow<List<Float>> = _levels

    private var visualizer: Visualizer? = null
    private var randomJob: Job? = null
    private var permissionMonitorJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Synchronized
    fun start(context: Context) {
        if (visualizer != null) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Нет разрешения RECORD_AUDIO. Запускаем случайный эквалайзер")
            startRandomMode()
            return
        }

        randomJob?.cancel()
        randomJob = null

        var candidate: Visualizer? = null

        try {
            candidate = Visualizer(0)

            val captureRange = Visualizer.getCaptureSizeRange()
            candidate.captureSize = captureRange[1]

            candidate.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {

                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Используем FFT.
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null) return

                        val ranges = listOf(
                            2..5,
                            6..12,
                            13..28,
                            29..60
                        )

                        val newLevels = ranges.map { range ->
                            var sum = 0.0
                            var count = 0

                            for (bin in range) {
                                val realIndex = bin * 2
                                val imaginaryIndex = realIndex + 1

                                if (imaginaryIndex < fft.size) {
                                    val real = fft[realIndex].toInt()
                                    val imaginary = fft[imaginaryIndex].toInt()

                                    val magnitude = sqrt(
                                        (real * real + imaginary * imaginary).toDouble()
                                    )

                                    sum += magnitude
                                    count++
                                }
                            }

                            if (count == 0) {
                                0f
                            } else {
                                (sum / count / 45.0)
                                    .toFloat()
                                    .coerceIn(0f, 1f)
                            }
                        }

                        val previous = _levels.value

                        _levels.value = newLevels.mapIndexed { index, value ->
                            previous[index] * 0.55f + value * 0.45f
                        }
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            )

            candidate.enabled = true
            visualizer = candidate

            Log.d(TAG, "AudioVisualizer запущен")

            startPermissionMonitor(context.applicationContext)

        } catch (e: Exception) {
            Log.e(TAG, "Не удалось запустить AudioVisualizer", e)

            try {
                candidate?.release()
            } catch (_: Exception) {
            }

            visualizer = null
            startRandomMode()
        }
    }

    private fun startRandomMode() {
        if (randomJob?.isActive == true) return

        Log.d(TAG, "Запущен случайный режим эквалайзера")

        randomJob = scope.launch {
            var current = List(4) { Random.nextFloat() * 0.5f }

            while (isActive) {
                val targets = List(4) {
                    Random.nextFloat() * 0.85f + 0.05f
                }

                repeat(5) {
                    if (!isActive) return@launch

                    current = current.mapIndexed { index, value ->
                        value * 0.55f + targets[index] * 0.45f
                    }

                    _levels.value = current

                    delay(40)
                }
            }
        }
    }

    private fun startPermissionMonitor(context: Context) {
        permissionMonitorJob?.cancel()

        permissionMonitorJob = scope.launch {
            while (isActive) {
                delay(1000)

                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    Log.w(TAG, "Разрешение RECORD_AUDIO отозвано")

                    synchronized(this@AudioVisualizer) {
                        if (visualizer != null) {
                            stopVisualizerInternal()
                            startRandomMode()
                        }
                    }

                    return@launch
                }
            }
        }
    }

    private fun stopVisualizerInternal() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки Visualizer", e)
        }

        visualizer = null
    }

    @Synchronized
    fun stop() {
        permissionMonitorJob?.cancel()
        permissionMonitorJob = null

        randomJob?.cancel()
        randomJob = null

        stopVisualizerInternal()

        _levels.value = List(4) { 0f }

        Log.d(TAG, "AudioVisualizer остановлен")
    }
}
