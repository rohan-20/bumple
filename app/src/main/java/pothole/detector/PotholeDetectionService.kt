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
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

import android.os.Binder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import android.location.Location
import android.os.Looper
import android.util.Log
import pothole.detector.models.Pothole
import java.util.Locale

class PotholeDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var vibrator: Vibrator
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null
    private lateinit var geocoder: Geocoder

    private val binder = LocalBinder()
    private val _potholeCount = MutableStateFlow(0)
    val potholeCount = _potholeCount.asStateFlow()
    private val _totalDistance = MutableStateFlow(0.0)
    val totalDistance = _totalDistance.asStateFlow()
    private val _smoothnessScore = MutableStateFlow(100.0) // Initial score
    val smoothnessScore = _smoothnessScore.asStateFlow()
    private val _detectedPotholes = MutableStateFlow<List<Pothole>>(emptyList())
    val detectedPotholes = _detectedPotholes.asStateFlow()


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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { newLocation ->
                    if (lastLocation == null) {
                        lastLocation = newLocation
                    } else {
                        val distance = lastLocation!!.distanceTo(newLocation) / 1000.0 // meters to kilometers
                        _totalDistance.value += distance
                        Log.d("PotholeDetectionService", "Distance: $distance km, Total Distance: ${_totalDistance.value} km")
                        calculateSmoothnessScore()
                        updateNotification()
                        lastLocation = newLocation
                    }
                }
            }
        }

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
        startLocationUpdates()
    }

    private fun stopDetection() {
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        stopForeground(true)
        stopSelf()
    }

    fun resetCount() {
        _potholeCount.value = 0
        _totalDistance.value = 0.0
        _smoothnessScore.value = 100.0
        _detectedPotholes.value = emptyList()
        lastLocation = null
        updateNotification()
        sendCountUpdate()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000 // Update interval in milliseconds
        )
            .setMinUpdateIntervalMillis(2000) // Minimum update interval
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("PotholeDetectionService", "Location permission not granted: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun calculateSmoothnessScore() {
        val maxScore = 100.0
        val k = 10.0 // Sensitivity constant
        val dMin = 1.0 // Minimum distance to avoid division by zero

        val potholes = _potholeCount.value.toDouble()
        val distance = _totalDistance.value

        if (distance <= 0.0 && potholes == 0.0) {
            _smoothnessScore.value = maxScore
            return
        }

        val potholeDensity = potholes / (distance + dMin)
        _smoothnessScore.value = maxScore * kotlin.math.exp(-k * potholeDensity)
        Log.d("PotholeDetectionService", "Smoothness Score: ${_smoothnessScore.value}")
    }

    private fun getAddressFromLocation(location: Location): String {
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0)
            } else {
                "Address not found"
            }
        } catch (e: Exception) {
            Log.e("PotholeDetectionService", "Error getting address: ${e.message}")
            "Error getting address"
        }
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
                handlePotholeDetection()
            }
        } else if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gyroMagnitude = sqrt(x * x + y * y + z * z)

            if (gyroMagnitude > GYRO_THRESHOLD) {
                handlePotholeDetection()
            }
        }
    }

    private fun handlePotholeDetection() {
        val currentTime = System.currentTimeMillis()
        lastLocation?.let {
            val address = getAddressFromLocation(it)
            val pothole = Pothole(it.latitude, it.longitude, address)
            _detectedPotholes.value += pothole
            _potholeCount.value++
            calculateSmoothnessScore()
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
        .setContentText("Potholes: ${_potholeCount.value}, Distance: %.2f km, Smoothness: %.2f".format(_totalDistance.value, _smoothnessScore.value))
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
        stopLocationUpdates()
    }
}