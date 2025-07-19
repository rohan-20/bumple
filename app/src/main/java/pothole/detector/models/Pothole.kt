package pothole.detector.models

data class Pothole(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val timestamp: Long = System.currentTimeMillis()
)
