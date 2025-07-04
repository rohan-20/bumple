package pothole.detector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import pothole.detector.ui.theme.PotholeDetectorTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build

import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: PotholeViewModel
    private var detectionService: PotholeDetectionService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PotholeDetectionService.LocalBinder
            detectionService = binder.getService()
            isBound = true
            // Start collecting the pothole count
            lifecycleScope.launch {
                detectionService?.potholeCount?.collectLatest { count ->
                    viewModel.updatePotholeCount(count)
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            detectionService = null
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. You can proceed with showing notifications.
        } else {
            // Explain to the user that the feature is unavailable because the
            // user has denied a permission. Or, you can show a dialog explaining
            // why the feature is important and direct them to app settings.
        }
    }

    private val requestActivityRecognitionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted.
        } else {
            // Explain to the user that the feature is unavailable.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[PotholeViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestActivityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        setContent {
            PotholeDetectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val potholeCountState by viewModel.potholeCount.collectAsState()
                    val isDetectingState by viewModel.isDetecting.collectAsState()

                    PotholeDetectorApp(
                        potholeCount = potholeCountState,
                        isDetecting = isDetectingState,
                        onStartDetection = { startDetectionService() },
                        onStopDetection = { stopDetectionService() },
                        onResetCount = { resetCount() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, PotholeDetectionService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun startDetectionService() {
        Log.d("MainActivity", "Starting detection service")
        val serviceIntent = Intent(this, PotholeDetectionService::class.java).apply {
            action = PotholeDetectionService.ACTION_START_DETECTION
        }
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        viewModel.updateIsDetecting(true)
    }

    private fun stopDetectionService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        val serviceIntent = Intent(this, PotholeDetectionService::class.java).apply {
            action = PotholeDetectionService.ACTION_STOP_DETECTION
        }
        stopService(serviceIntent)
        viewModel.updateIsDetecting(false)
    }

    private fun resetCount() {
        detectionService?.resetCount()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure service is stopped if activity is destroyed unexpectedly
        val serviceIntent = Intent(this, PotholeDetectionService::class.java).apply {
            action = PotholeDetectionService.ACTION_STOP_DETECTION
        }
        stopService(serviceIntent)
    }
}

@Composable
fun PotholeDetectorApp(
    potholeCount: Int,
    isDetecting: Boolean,
    onStartDetection: () -> Unit,
    onStopDetection: () -> Unit,
    onResetCount: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.gaddho_logo),
                contentDescription = "Pothole Detector Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                contentScale = ContentScale.Fit
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(120.dp),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Potholes Detected",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$potholeCount",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isDetecting) {
                Button(
                    onClick = onStopDetection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Stop Detection")
                }
            } else {
                Button(
                    onClick = onStartDetection,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Start Detection")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onResetCount,
                    enabled = potholeCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Reset Count")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PotholeDetectorPreview() {
    PotholeDetectorTheme {
        PotholeDetectorApp(potholeCount = 0, isDetecting = false, onStartDetection = {}, onStopDetection = {}, onResetCount = {})
    }
}