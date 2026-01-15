package cz.pavl.metronome

import android.content.Context
import android.media.SoundPool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MetronomeEngine(context: Context) {

    // SoundPool for playing tick sounds
    private val soundPool = SoundPool.Builder().setMaxStreams(2).build()

    // Load tick sounds from resources/raw
    private val highSoundId = soundPool.load(context, R.raw.tick_high, 1)
    private val lowSoundId = soundPool.load(context, R.raw.tick_low, 1)

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null

    // information about the current beat for UI visualization
    private val _currentBeat = MutableStateFlow(1)
    val currentBeat = _currentBeat.asStateFlow()

    fun start(bpm: Int, beatsPerBar: Int) {
        stop() // reset any existing job

        job = scope.launch {
            var beatIndex = 1
            // length of one beat in milliseconds
            val interval = 60000L / bpm.toLong()

            while (isActive) {
                val startTime = System.currentTimeMillis()

                // play sound for beat
                if (beatIndex == 1) {
                    soundPool.play(highSoundId, 1f, 1f, 1, 0, 1f)
                } else {
                    soundPool.play(lowSoundId, 1f, 1f, 1, 0, 1f)
                }

                // update current beat for UI
                _currentBeat.emit(beatIndex)

                // next beat
                beatIndex++
                if (beatIndex > beatsPerBar) beatIndex = 1

                // calculate processing time and delay
                val processingTime = System.currentTimeMillis() - startTime
                val delayTime = interval - processingTime

                if (delayTime > 0) {
                    delay(delayTime)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _currentBeat.value = 1 // reset visualization
    }
}