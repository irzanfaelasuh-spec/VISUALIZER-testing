
package dev.lumenwave.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class AudioCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var active = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.from(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE -> {
                VisualizerOverlay.refresh(this)
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (active) {
                    VisualizerOverlay.refresh(this)
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)

                val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(
                        EXTRA_RESULT_DATA,
                        Intent::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                startAsForeground()

                if (data == null || resultCode == 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                startCapture(resultCode, data)
            }
        }

        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stop = PendingIntent.getService(
            this,
            11,
            Intent(this, AudioCaptureService::class.java)
                .setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LumenWave is active")
            .setContentText("Listening to permitted device playback")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stop
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            // FIX: Pastikan MediaProjection tidak null sebelum digunakan.
            val mediaProjection: MediaProjection =
                manager.getMediaProjection(resultCode, data)
                    ?: throw IllegalStateException(
                        "MediaProjection could not be created"
                    )

            projection = mediaProjection

            mediaProjection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopCapture(projectionAlreadyStopped = true)
                        stopSelf()
                    }
                },
                mainHandler
            )

            val captureConfig =
                AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

            val sampleRate = 44_100

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val minimum = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minimum <= 0) {
                throw IllegalStateException(
                    "AudioRecord buffer is unavailable ($minimum)"
                )
            }

            val bufferBytes = maxOf(minimum, 8192)

            val audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferBytes)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                throw IllegalStateException(
                    "AudioRecord could not be initialized"
                )
            }

            recorder = audioRecord

            val engine = SpectrumEngine(prefs)

            audioRecord.startRecording()

            active = true
            running = true

            VisualizerOverlay.attach(this)

            worker = Thread {
                val pcm = ShortArray(1024)

                while (
                    active &&
                    !Thread.currentThread().isInterrupted
                ) {
                    val read = try {
                        audioRecord.read(
                            pcm,
                            0,
                            pcm.size,
                            AudioRecord.READ_BLOCKING
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Audio read failed", e)
                        break
                    }

                    if (read > 0) {
                        val bands = engine.process(pcm, read)
                        VisualizerOverlay.update(bands)
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord returned $read")
                        break
                    }
                }

                if (active) {
                    mainHandler.post {
                        stopSelf()
                    }
                }
            }.apply {
                name = "LumenWave-AudioCapture"
                priority = Thread.NORM_PRIORITY
                start()
            }

        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "Playback capture permission or policy rejected",
                e
            )
            stopSelf()

        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unable to start playback capture",
                e
            )
            stopSelf()
        }
    }

    private fun stopCapture(
        projectionAlreadyStopped: Boolean = false
    ) {
        active = false
        running = false

        worker?.interrupt()
        worker = null

        val currentRecorder = recorder
        recorder = null

        try {
            currentRecorder?.stop()
        } catch (_: Exception) {
        }

        try {
            currentRecorder?.release()
        } catch (_: Exception) {
        }

        val currentProjection = projection
        projection = null

        if (!projectionAlreadyStopped) {
            try {
                currentProjection?.stop()
            } catch (_: Exception) {
            }
        }

        VisualizerOverlay.detach()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Visualizer session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Shows when LumenWave is capturing allowed media playback"
                setShowBadge(false)
            }

            (
                getSystemService(NOTIFICATION_SERVICE)
                    as NotificationManager
            ).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "LumenWaveCapture"
        private const val CHANNEL_ID = "lumenwave_capture"
        private const val NOTIFICATION_ID = 4201

        const val ACTION_START =
            "dev.lumenwave.app.action.START"

        const val ACTION_STOP =
            "dev.lumenwave.app.action.STOP"

        const val ACTION_UPDATE =
            "dev.lumenwave.app.action.UPDATE"

        const val EXTRA_RESULT_CODE =
            "projection_result_code"

        const val EXTRA_RESULT_DATA =
            "projection_result_data"

        @Volatile
        var running: Boolean = false
            private set

        fun requestStop(context: Context) {
            val intent = Intent(
                context,
                AudioCaptureService::class.java
            ).setAction(ACTION_STOP)

            try {
                context.startService(intent)
            } catch (_: Exception) {
                context.stopService(intent)
            }
        }

        fun requestUpdate(context: Context) {
            if (running) {
                try {
                    context.startService(
                        Intent(
                            context,
                            AudioCaptureService::class.java
                        ).setAction(ACTION_UPDATE)
                    )
                } catch (_: Exception) {
                    VisualizerOverlay.refresh(context)
                }
            } else {
                VisualizerOverlay.refresh(context)
            }
        }
    }
}
