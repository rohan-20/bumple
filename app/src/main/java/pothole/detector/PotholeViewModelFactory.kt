package pothole.detector

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PotholeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PotholeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PotholeViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}