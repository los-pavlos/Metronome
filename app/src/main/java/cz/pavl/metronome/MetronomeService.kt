package cz.pavl.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MetronomeService : Service() {
    private lateinit var engine: MetronomeEngine
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "metronome_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_PARAMS = "ACTION_UPDATE_PARAMS"
        const val BROADCAST_BEAT = "cz.pavl.metronome.BEAT"
        const val BROADCAST_STATE = "cz.pavl.metronome.STATE"
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        engine = MetronomeEngine(this)
        createNotificationChannel()
        engine.currentBeat.onEach { beat ->
            val intent = Intent(BROADCAST_BEAT)
            intent.putExtra("beat", beat)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val bpm = intent.getIntExtra("bpm", 120)
                val beats = intent.getIntExtra("beats", 4)
                val subs = intent.getIntExtra("subs", 1)
                startMetronome(bpm, beats, subs)
            }
            ACTION_PAUSE -> pauseMetronome()
            ACTION_UPDATE_PARAMS -> {
                val bpm = intent.getIntExtra("bpm", 120)
                val beats = intent.getIntExtra("beats", 4)
                val subs = intent.getIntExtra("subs", 1)
                if (isServiceRunning) {
                    engine.start(bpm, beats, subs)
                    updateNotification(true, bpm)
                }
            }
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startMetronome(bpm: Int, beats: Int, subs: Int) {
        isServiceRunning = true
        engine.start(bpm, beats, subs)
        startForeground(NOTIFICATION_ID, buildNotification(true, bpm))
        broadcastState(true)
    }

    private fun pauseMetronome() {
        isServiceRunning = false
        engine.stop()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(false, 0))
        stopForeground(false)
        broadcastState(false)
    }

    private fun updateNotification(isPlaying: Boolean, bpm: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(isPlaying, bpm))
    }

    private fun broadcastState(isPlaying: Boolean) {
        val intent = Intent(BROADCAST_STATE)
        intent.putExtra("isPlaying", isPlaying)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun buildNotification(isPlaying: Boolean, bpm: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
        val playPauseIntent = Intent(this, MetronomeService::class.java).apply { action = if (isPlaying) ACTION_PAUSE else ACTION_START }
        val pendingPlayPause = PendingIntent.getService(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, MetronomeService::class.java).apply { action = ACTION_STOP_SERVICE }
        val pendingStop = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Metronome")
            .setContentText(if (isPlaying) "Playing at $bpm BPM" else "Paused")
            .setSmallIcon(R.drawable.note_1)
            .setContentIntent(pendingOpen)
            .setOngoing(isPlaying)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Resume", pendingPlayPause)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", pendingStop)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Metronome Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        engine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}