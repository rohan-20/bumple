package pothole.detector

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import android.content.SharedPreferences

import android.util.Log

class PotholeViewModel(private val context: Context) : ViewModel() {

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
        Log.d("PotholeViewModel", "Saved $key: $score")
    }

    private fun loadScore(key: String): Double {
        val score = sharedPreferences.getFloat(key, 0.0f).toDouble()
        Log.d("PotholeViewModel", "Loaded $key: $score")
        return score
    }

    fun clearScores() {
        sharedPreferences.edit().clear().apply()
        _yourScore.value = 0.0
        _friendScore.value = 0.0
        Log.d("PotholeViewModel", "All scores cleared.")
    }
}