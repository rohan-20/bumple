package pothole.detector

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pothole.detector.models.Pothole

class PotholeViewModel(private val context: Context) : ViewModel() {

    private var detectionService: PotholeDetectionService? = null

    private val _potholeCount = MutableStateFlow(0)
    val potholeCount = _potholeCount.asStateFlow()

    private val _totalDistance = MutableStateFlow(0.0)
    val totalDistance = _totalDistance.asStateFlow()

    private val _smoothnessScore = MutableStateFlow(100.0)
    val smoothnessScore = _smoothnessScore.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting = _isDetecting.asStateFlow()

    private val _detectedPotholes = MutableStateFlow<List<Pothole>>(emptyList())
    val detectedPotholes = _detectedPotholes.asStateFlow()

    fun setService(service: PotholeDetectionService) {
        detectionService = service
        viewModelScope.launch {
            detectionService?.potholeCount?.collect { count ->
                _potholeCount.value = count
            }
        }
        viewModelScope.launch {
            detectionService?.totalDistance?.collect { distance ->
                _totalDistance.value = distance
            }
        }
        viewModelScope.launch {
            detectionService?.smoothnessScore?.collect { score ->
                _smoothnessScore.value = score
            }
        }
        viewModelScope.launch {
            detectionService?.detectedPotholes?.collect { potholes ->
                _detectedPotholes.value = potholes
            }
        }
    }

    fun updateIsDetecting(detecting: Boolean) {
        _isDetecting.value = detecting
    }

    fun resetCount() {
        detectionService?.resetCount()
    }

    // Face-off score logic
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("PotholeDetectorScores", Context.MODE_PRIVATE)
    }

    private val _yourScore = MutableStateFlow(loadScore("your_score"))
    val yourScore = _yourScore.asStateFlow()

    private val _friendScore = MutableStateFlow(loadScore("friend_score"))
    val friendScore = _friendScore.asStateFlow()

    fun saveYourScore(score: Double) {
        saveScore("your_score", score)
        _yourScore.value = score
    }

    fun saveFriendScore(score: Double) {
        saveScore("friend_score", score)
        _friendScore.value = score
    }

    private fun saveScore(key: String, score: Double) {
        sharedPreferences.edit().putFloat(key, score.toFloat()).apply()
    }

    private fun loadScore(key: String): Double {
        return sharedPreferences.getFloat(key, 0.0f).toDouble()
    }

    fun clearScores() {
        sharedPreferences.edit().clear().apply()
        _yourScore.value = 0.0
        _friendScore.value = 0.0
    }
}
