package pothole.detector

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.util.Log

class PotholeViewModel : ViewModel() {

    private val _potholeCount = MutableStateFlow(0)
    val potholeCount = _potholeCount.asStateFlow()

    private val _totalDistance = MutableStateFlow(0.0)
    val totalDistance = _totalDistance.asStateFlow()

    private val _smoothnessScore = MutableStateFlow(100.0)
    val smoothnessScore = _smoothnessScore.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting = _isDetecting.asStateFlow()

    fun updatePotholeCount(count: Int) {
        _potholeCount.value = count
        Log.d("PotholeViewModel", "Pothole count updated to: $count")
    }

    fun updateTotalDistance(distance: Double) {
        _totalDistance.value = distance
        Log.d("PotholeViewModel", "Total distance updated to: $distance km")
    }

    fun updateSmoothnessScore(score: Double) {
        _smoothnessScore.value = score
        Log.d("PotholeViewModel", "Smoothness score updated to: $score")
    }

    fun updateIsDetecting(detecting: Boolean) {
        _isDetecting.value = detecting
        Log.d("PotholeViewModel", "isDetecting updated to: $detecting")
    }

    fun resetCount() {
        _potholeCount.value = 0
        _totalDistance.value = 0.0
        _smoothnessScore.value = 100.0
        Log.d("PotholeViewModel", "Count, distance, and score reset.")
    }
}