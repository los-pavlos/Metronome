package cz.pavl.metronome

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList

data class TunerResult(
    val frequency: Float = 0f,
    val noteName: String = "--",
    val deviation: Float = 0f
)

class TunerEngine {
    private val sampleRate = 44100
    private val bufferSize = 4096
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private val finalBufferSize = maxOf(bufferSize, minBufferSize)
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    var baseA4: Int = 440
    private val _tunerResult = MutableStateFlow(TunerResult())
    val tunerResult = _tunerResult.asStateFlow()
    private val historySize = 5
    private val freqHistory = LinkedList<Float>()

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRunning) return
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, finalBufferSize)
            audioRecord?.startRecording()
            isRunning = true
            job = scope.launch {
                val buffer = ShortArray(finalBufferSize)
                while (isActive && isRunning) {
                    val readCount = audioRecord?.read(buffer, 0, finalBufferSize) ?: 0
                    if (readCount > 0) {
                        val rawFreq = PitchUtils.detectPitch(buffer, sampleRate)
                        if (rawFreq > 20f) {
                            freqHistory.add(rawFreq)
                            if (freqHistory.size > historySize) freqHistory.removeFirst()
                            val smoothedFreq = freqHistory.average().toFloat()
                            _tunerResult.emit(PitchUtils.frequencyToNote(smoothedFreq, baseA4))
                        } else {
                            if (freqHistory.isNotEmpty()) freqHistory.removeFirst()
                        }
                    }
                    delay(20)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun stopListening() {
        isRunning = false
        job?.cancel()
        try { audioRecord?.stop(); audioRecord?.release() } catch (e: Exception) { e.printStackTrace() }
        audioRecord = null
        freqHistory.clear()
    }
}