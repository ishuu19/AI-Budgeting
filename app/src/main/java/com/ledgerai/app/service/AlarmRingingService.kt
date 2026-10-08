package com.ledgerai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ledgerai.app.data.repository.CalendarRepository
import com.ledgerai.app.domain.model.CalendarEventKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground media-playback service that rings until dismiss or snooze.
 */
@AndroidEntryPoint
class AlarmRingingService : Service() {

    @Inject lateinit var calendarRepository: CalendarRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var volumeRampJob: Job? = null
    private var activeAlarmId: Long = -1L
    private var activeLabel: String = "Alarm"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                if (id < 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startRinging(id)
            }
            ACTION_DISMISS -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, activeAlarmId)
                dismiss(id)
            }
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, activeAlarmId)
                val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)
                snooze(id, minutes)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(alarmId: Long) {
        activeAlarmId = alarmId
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(activeLabel, alarmId))

        scope.launch {
            val alarm = calendarRepository.getById(alarmId)?.takeIf { it.kind == CalendarEventKind.ALARM }
            if (alarm == null || !alarm.isEnabled) {
                stopEverything()
                return@launch
            }
            activeLabel = alarm.title.ifBlank { "Alarm" }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, buildNotification(activeLabel, alarmId))
            playTone(alarm.alarmToneUri)
            vibrate()
            launchFullScreen(alarmId, activeLabel)
        }
    }

    private fun playTone(toneUri: String?) {
        stopTone()
        try {
            val uri = AlarmToneHelper.resolvePlayableUri(this, toneUri)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, uri)
                isLooping = true
                prepare()
                setVolume(0f, 0f)
                start()
            }.also { startVolumeRamp(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Tone play failed; trying system alarm URI", e)
            try {
                val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmRingingService, fallback)
                    isLooping = true
                    prepare()
                    setVolume(0f, 0f)
                    start()
                }.also { startVolumeRamp(it) }
            } catch (e2: Exception) {
                Log.e(TAG, "Default ringtone failed", e2)
            }
        }
    }

    /** Gradually raise volume from silent to full over ~10s. */
    private fun startVolumeRamp(mediaPlayer: MediaPlayer) {
        volumeRampJob?.cancel()
        volumeRampJob = scope.launch {
            val steps = VOLUME_RAMP_STEPS
            for (i in 1..steps) {
                delay(VOLUME_RAMP_STEP_MS)
                val vol = i / steps.toFloat()
                try {
                    mediaPlayer.setVolume(vol, vol)
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 500, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopVibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
        }
    }

    private fun launchFullScreen(alarmId: Long, label: String) {
        val ui = Intent(this, AlarmRingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, label)
        }
        try {
            startActivity(ui)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start ring UI (lock screen may use FSI)", e)
        }
    }

    private fun dismiss(alarmId: Long) {
        scope.launch {
            try {
                val alarm = calendarRepository.getById(alarmId)
                if (alarm != null && alarm.kind == CalendarEventKind.ALARM) {
                    if (alarm.alarmRepeatDays == 0) {
                        calendarRepository.setEnabled(alarmId, false)
                    } else {
                        calendarRepository.setEnabled(alarmId, true)
                    }
                }
            } finally {
                stopEverything()
            }
        }
    }

    private fun snooze(alarmId: Long, minutes: Int) {
        alarmScheduler.scheduleSnooze(alarmId, minutes.coerceIn(1, 60))
        stopEverything()
    }

    private fun stopTone() {
        volumeRampJob?.cancel()
        volumeRampJob = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
    }

    private fun stopEverything() {
        stopTone()
        stopVibrate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(label: String, alarmId: Long): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            Intent(this, AlarmRingingActivity::class.java).apply {
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_ALARM_LABEL, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPi = PendingIntent.getService(
            this,
            (alarmId * 10 + 1).toInt(),
            Intent(this, AlarmRingingService::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_ALARM_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePi = PendingIntent.getService(
            this,
            (alarmId * 10 + 2).toInt(),
            Intent(this, AlarmRingingService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_SNOOZE_MINUTES, 5)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label)
            .setContentText("Alarm is ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Dismiss", dismissPi)
            .addAction(0, "Snooze 5m", snoozePi)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm ringing"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopTone()
        stopVibrate()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRingingService"
        private const val VOLUME_RAMP_STEPS = 20
        private const val VOLUME_RAMP_STEP_MS = 500L
        const val CHANNEL_ID = "alarm_ringing"
        const val NOTIFICATION_ID = 2101
        const val ACTION_START = "com.ledgerai.app.action.ALARM_RING_START"
        const val ACTION_DISMISS = "com.ledgerai.app.action.ALARM_RING_DISMISS"
        const val ACTION_SNOOZE = "com.ledgerai.app.action.ALARM_RING_SNOOZE"
        const val EXTRA_ALARM_ID = AlarmScheduler.EXTRA_ALARM_ID
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmRingingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ALARM_ID, alarmId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
