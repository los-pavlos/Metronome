package cz.pavl.metronome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MetronomeViewModel(
    private val context: Context,
    private val tunerEngine: TunerEngine,
    private val prefs: SharedPreferences
) : ViewModel() {

    var bpm = mutableStateOf(120)
    var activeBpm = mutableStateOf(120)
    var beatsPerBar = mutableStateOf(4)
    var subdivisions = mutableStateOf(1)
    var isPlaying = mutableStateOf(false)
    var isDarkMode = mutableStateOf(prefs.getBoolean("dark_mode", false))
    var referenceFreq = mutableStateOf(prefs.getInt("ref_freq", 440))
    var currentBeat = mutableStateOf(1)

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MetronomeService.BROADCAST_BEAT -> currentBeat.value = intent.getIntExtra("beat", 1)
                MetronomeService.BROADCAST_STATE -> isPlaying.value = intent.getBooleanExtra("isPlaying", false)
            }
        }
    }

    init {
        tunerEngine.baseA4 = referenceFreq.value
        val filter = IntentFilter().apply {
            addAction(MetronomeService.BROADCAST_BEAT)
            addAction(MetronomeService.BROADCAST_STATE)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(serviceReceiver, filter)
    }

    fun setReferenceFrequency(freq: Int) {
        val clamped = freq.coerceIn(415, 466)
        referenceFreq.value = clamped
        tunerEngine.baseA4 = clamped
        prefs.edit().putInt("ref_freq", clamped).apply()
    }

    fun toggleDarkMode(enabled: Boolean) {
        isDarkMode.value = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun togglePlay() {
        val intent = Intent(context, MetronomeService::class.java)
        if (isPlaying.value) {
            intent.action = MetronomeService.ACTION_PAUSE
        } else {
            activeBpm.value = bpm.value
            intent.action = MetronomeService.ACTION_START
            intent.putExtra("bpm", activeBpm.value)
            intent.putExtra("beats", beatsPerBar.value)
            intent.putExtra("subs", subdivisions.value)
        }
        context.startService(intent)
    }

    private fun updateServiceParams() {
        if (isPlaying.value) {
            val intent = Intent(context, MetronomeService::class.java)
            intent.action = MetronomeService.ACTION_UPDATE_PARAMS
            intent.putExtra("bpm", activeBpm.value)
            intent.putExtra("beats", beatsPerBar.value)
            intent.putExtra("subs", subdivisions.value)
            context.startService(intent)
        }
    }

    fun onBpmChange(newBpm: Int, updateEngine: Boolean = true) {
        val clampedBpm = newBpm.coerceIn(40, 240)
        bpm.value = clampedBpm
        if (updateEngine) {
            activeBpm.value = clampedBpm
            updateServiceParams()
        }
    }

    fun onBpmSliderFinished() {
        activeBpm.value = bpm.value
        updateServiceParams()
    }

    fun onBeatsChange(newVal: Int) {
        beatsPerBar.value = newVal.coerceIn(2, 7)
        updateServiceParams()
    }

    fun onSubdivisionChange(newVal: Int) {
        subdivisions.value = newVal
        updateServiceParams()
    }

    private var lastTapTime: Long = 0
    private val tapIntervals = mutableListOf<Long>()
    fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 2000) tapIntervals.clear()
        else {
            val interval = now - lastTapTime
            if (interval > 200) {
                tapIntervals.add(interval)
                if (tapIntervals.size > 4) tapIntervals.removeAt(0)
                val avg = tapIntervals.average()
                if (avg > 0) onBpmChange((60000 / avg).toInt(), updateEngine = true)
            }
        }
        lastTapTime = now
    }

    override fun onCleared() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceReceiver)
        super.onCleared()
    }
}

class MetronomeViewModelFactory(
    private val context: Context,
    private val tunerEngine: TunerEngine,
    private val prefs: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MetronomeViewModel(context, tunerEngine, prefs) as T
    }
}