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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import pothole.detector.ui.faceoff.FaceOffScreen
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

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
                detectionService?.totalDistance?.collectLatest { distance ->
                    viewModel.updateTotalDistance(distance)
                }
                detectionService?.smoothnessScore?.collectLatest { score ->
                    viewModel.updateSmoothnessScore(score)
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

    private val requestLocationPermissionLauncher = registerForActivityResult(
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

        viewModel = ViewModelProvider(this, PotholeViewModelFactory(applicationContext))[PotholeViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestActivityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            PotholeDetectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val potholeCountState by viewModel.potholeCount.collectAsState()
                    val totalDistanceState by viewModel.totalDistance.collectAsState()
                    val smoothnessScoreState by viewModel.smoothnessScore.collectAsState()
                    val isDetectingState by viewModel.isDetecting.collectAsState()

                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "main_screen") {
                        composable("main_screen") {
                            PotholeDetectorApp(
                                potholeCount = potholeCountState,
                                totalDistance = totalDistanceState,
                                smoothnessScore = smoothnessScoreState,
                                isDetecting = isDetectingState,
                                onStartDetection = { startDetectionService() },
                                onStopDetection = { stopDetectionService() },
                                onResetCount = { resetCount() },
                                onNavigateToFaceOff = { navController.navigate("face_off_screen") }
                            )
                        }
                        composable("face_off_screen") {
                            FaceOffScreen(
                                yourScore = viewModel.yourScore.collectAsState().value,
                                friendScore = viewModel.friendScore.collectAsState().value,
                                currentSmoothnessScore = smoothnessScoreState,
                                onSaveYourScore = { score -> viewModel.saveYourScore(score) },
                                onSaveFriendScore = { score -> viewModel.saveFriendScore(score) },
                                onClearScores = { viewModel.clearScores() },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
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
    totalDistance: Double,
    smoothnessScore: Double,
    isDetecting: Boolean,
    onStartDetection: () -> Unit,
    onStopDetection: () -> Unit,
    onResetCount: () -> Unit,
    onNavigateToFaceOff: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Distance Traveled",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f km".format(totalDistance),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Ride Smoothness Score",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f".format(smoothnessScore),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onNavigateToFaceOff,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Face Off!")
            }

            Spacer(modifier = Modifier.height(16.dp))

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
        PotholeDetectorApp(potholeCount = 0, totalDistance = 0.0, smoothnessScore = 100.0, isDetecting = false, onStartDetection = {}, onStopDetection = {}, onResetCount = {}, onNavigateToFaceOff = {})
    }
}