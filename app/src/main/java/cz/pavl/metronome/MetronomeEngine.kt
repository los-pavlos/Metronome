package cz.pavl.metronome

import android.content.Context
import android.media.SoundPool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MetronomeEngine(context: Context) {
    private val soundPool = SoundPool.Builder().setMaxStreams(4).build()
    private val highSoundId = soundPool.load(context, R.raw.tick_high, 1)
    private val lowSoundId = soundPool.load(context, R.raw.tick_low, 1)
    private val thirdSoundId = soundPool.load(context, R.raw.tick_third, 1)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private val _currentBeat = MutableStateFlow(1)
    val currentBeat = _currentBeat.asStateFlow()

    fun start(bpm: Int, beatsPerBar: Int, subdivisions: Int) {
        stop()
        job = scope.launch {
            val totalTicks = beatsPerBar * subdivisions
            var globalTickIndex = 0
            val beatDuration = 60000L / bpm.toLong()
            val subInterval = beatDuration / subdivisions

            while (isActive) {
                val startTime = System.currentTimeMillis()
                val isMainBeat = (globalTickIndex % subdivisions) == 0
                val mainBeatNumber = (globalTickIndex / subdivisions) + 1

                if (isMainBeat) {
                    _currentBeat.emit(mainBeatNumber)
                    if (globalTickIndex == 0) soundPool.play(highSoundId, 1f, 1f, 1, 0, 1f)
                    else soundPool.play(lowSoundId, 1f, 1f, 1, 0, 1f)
                } else {
                    soundPool.play(thirdSoundId, 0.7f, 0.7f, 1, 0, 1f)
                }

                globalTickIndex++
                if (globalTickIndex >= totalTicks) globalTickIndex = 0
                val processingTime = System.currentTimeMillis() - startTime
                val delayTime = subInterval - processingTime
                if (delayTime > 0) delay(delayTime)
            }
        }
    }

    fun stop() {
        job?.cancel()
        _currentBeat.value = 1
    }
}