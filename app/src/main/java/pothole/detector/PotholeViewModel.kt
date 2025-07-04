package pothole.detector

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.util.Log

class PotholeViewModel : ViewModel() {

    private val _potholeCount = MutableStateFlow(0)
    val potholeCount = _potholeCount.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting = _isDetecting.asStateFlow()

    fun updatePotholeCount(count: Int) {
        _potholeCount.value = count
        Log.d("PotholeViewModel", "Pothole count updated to: $count")
    }

    fun updateIsDetecting(detecting: Boolean) {
        _isDetecting.value = detecting
        Log.d("PotholeViewModel", "isDetecting updated to: $detecting")
    }

    fun resetCount() {
        _potholeCount.value = 0
        Log.d("PotholeViewModel", "Count reset to 0")
    }
}