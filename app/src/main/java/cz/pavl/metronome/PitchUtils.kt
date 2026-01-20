package cz.pavl.metronome

import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PitchUtils {
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun frequencyToNote(frequency: Float, baseFrequency: Int): TunerResult {
        if (frequency == 0f) return TunerResult()
        val noteNum = 12 * log2(frequency / baseFrequency.toDouble()) + 69
        val roundedNote = noteNum.roundToInt()
        val noteIndex = (roundedNote % 12)
        val safeIndex = if (noteIndex < 0) noteIndex + 12 else noteIndex
        val name = noteNames[safeIndex]
        val deviation = (noteNum - roundedNote) * 100
        return TunerResult(frequency, name, deviation.toFloat())
    }

    fun detectPitch(buffer: ShortArray, sampleRate: Int): Float {
        val size = buffer.size
        var sumSquares = 0.0
        for (sample in buffer) sumSquares += sample * sample
        val rms = sqrt(sumSquares / size)
        if (rms < 50) return 0f

        val minLag = sampleRate / 1000
        val maxLag = sampleRate / 40
        var bestCorrelation = -1.0
        var bestLag = -1

        for (lag in minLag until maxLag) {
            var correlation = 0.0
            val readLimit = size - lag
            for (i in 0 until readLimit) correlation += buffer[i] * buffer[i + lag]
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        if (bestLag == -1) return 0f

        var adjustedLag = bestLag.toFloat()
        if (bestLag > 0 && bestLag < maxLag - 1) {
            val prev = calculateCorrelation(buffer, bestLag - 1)
            val curr = bestCorrelation
            val next = calculateCorrelation(buffer, bestLag + 1)
            val delta = (prev - next) / (2 * (prev - 2 * curr + next))
            adjustedLag += delta.toFloat()
        }
        return sampleRate.toFloat() / adjustedLag
    }

    private fun calculateCorrelation(buffer: ShortArray, lag: Int): Double {
        var correlation = 0.0
        val readLimit = buffer.size - lag
        for (i in 0 until readLimit) correlation += buffer[i] * buffer[i + lag]
        return correlation
    }
}