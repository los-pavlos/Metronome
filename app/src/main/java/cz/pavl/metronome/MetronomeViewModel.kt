package cz.pavl.metronome

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MetronomeViewModel(
    private val engine: MetronomeEngine,
    private val prefs: SharedPreferences // SharedPreferences for user settings
) : ViewModel() {

    // --- UI STATES ---
    var bpm = mutableStateOf(120)
    var beatsPerBar = mutableStateOf(4)
    var isPlaying = mutableStateOf(false)

    // Dark Mode from SharedPreferences
    var isDarkMode = mutableStateOf(prefs.getBoolean("dark_mode", false))

    // current beat from engine
    val currentBeat = engine.currentBeat

    // tap tempo variables
    private var lastTapTime: Long = 0
    private val tapIntervals = mutableListOf<Long>()


    fun toggleDarkMode(enabled: Boolean) {
        isDarkMode.value = enabled
        // Uložíme do SharedPreferences
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun togglePlay() {
        if (isPlaying.value) {
            engine.stop()
            isPlaying.value = false
        } else {
            engine.start(bpm.value, beatsPerBar.value)
            isPlaying.value = true
        }
    }

    fun onBpmChange(newBpm: Int) {
        bpm.value = newBpm.coerceIn(40, 240)
        if (isPlaying.value) {
            engine.start(bpm.value, beatsPerBar.value)
        }
    }

    fun onBeatsChange(newVal: Int) {
        beatsPerBar.value = newVal.coerceIn(2, 7)
        if (isPlaying.value) {
            engine.start(bpm.value, beatsPerBar.value)
        }
    }

    fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 2000) {
            tapIntervals.clear()
        } else {
            val interval = now - lastTapTime
            if (interval > 200) {
                tapIntervals.add(interval)
                if (tapIntervals.size > 4) tapIntervals.removeAt(0)
                val avg = tapIntervals.average()
                if (avg > 0) {
                    val newBpm = (60000 / avg).toInt()
                    onBpmChange(newBpm)
                }
            }
        }
        lastTapTime = now
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}

// Factory for MetronomeViewModel
class MetronomeViewModelFactory(
    private val engine: MetronomeEngine,
    private val prefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MetronomeViewModel(engine, prefs) as T
    }
}