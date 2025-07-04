package pothole.detector

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

import android.os.Binder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PotholeDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var vibrator: Vibrator

    private val binder = LocalBinder()
    private val _potholeCount = MutableStateFlow(0)
    val potholeCount = _potholeCount.asStateFlow()


    private val ACCEL_THRESHOLD = 15.0f // Adjust this threshold as needed
    private val GYRO_THRESHOLD = 5.0f // Adjust this threshold as needed
    private val DETECTION_COOLDOWN_MS = 1000L // Cooldown period to avoid multiple detections for one pothole
    private var lastDetectionTime = 0L

    companion object {
        const val ACTION_START_DETECTION = "ACTION_START_DETECTION"
        const val ACTION_STOP_DETECTION = "ACTION_STOP_DETECTION"
        const val ACTION_RESET_COUNT = "ACTION_RESET_COUNT"
        const val ACTION_UPDATE_COUNT = "ACTION_UPDATE_COUNT"
        const val EXTRA_POTHOLE_COUNT = "EXTRA_POTHOLE_COUNT"
        const val ACTION_REQUEST_COUNT = "ACTION_REQUEST_COUNT"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "PotholeDetectorChannel"
    }

    inner class LocalBinder : Binder() {
        fun getService(): PotholeDetectionService = this@PotholeDetectionService
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DETECTION -> startDetection()
            ACTION_STOP_DETECTION -> stopDetection()
            ACTION_RESET_COUNT -> resetCount()
            ACTION_REQUEST_COUNT -> sendCountUpdate()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private fun startDetection() {
        updateNotification()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun stopDetection() {
        sensorManager.unregisterListener(this)
        stopForeground(true)
        stopSelf()
    }

    fun resetCount() {
        _potholeCount.value = 0
        updateNotification()
        sendCountUpdate()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_COOLDOWN_MS) {
            return
        }

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val accelerationMagnitude = sqrt(x * x + y * y + z * z)

            if (accelerationMagnitude > ACCEL_THRESHOLD) {
                _potholeCount.value++
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(200)
                }
                lastDetectionTime = currentTime
                updateNotification()
                sendCountUpdate()
            }
        } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gyroMagnitude = sqrt(x * x + y * y + z * z)

            if (gyroMagnitude > GYRO_THRESHOLD) {
                _potholeCount.value++
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(200)
                }
                lastDetectionTime = currentTime
                updateNotification()
                sendCountUpdate()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Pothole Detector Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Pothole Detector")
        .setContentText("Potholes detected: ${_potholeCount.value}")
        .setSmallIcon(android.R.drawable.ic_dialog_info) // Use a default Android icon for now
        .setContentIntent(getMainActivityPendingIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun sendCountUpdate() {
        val intent = Intent(ACTION_UPDATE_COUNT).apply {
            putExtra(EXTRA_POTHOLE_COUNT, _potholeCount.value)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}