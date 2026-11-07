package sa.factory.ilmibootcampsmarte.engine

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class StylusToolType {
    BALLPOINT_PEN,
    CALLIGRAPHY_BRUSH,
    HIGHLIGHTER,
    PRECISION_ERASER,
    LASER_POINTER
}

data class StylusPoint(
    val x: Float,
    val y: Float,
    val pressureLevel: Int, // 0 to 4095
    val tiltAngleDegrees: Float, // 0 to 90 degrees
    val azimuthDegrees: Float, // 0 to 360 degrees
    val isHovering: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
)

data class StylusStroke(
    val strokeId: String,
    val toolType: StylusToolType,
    val points: List<StylusPoint>,
    val averagePressure: Double,
    val totalDistancePx: Float,
    val smoothedStrokeWidth: Float
)

class StylusDigitizerProtocolEngine {
    private var activeTool: StylusToolType = StylusToolType.BALLPOINT_PEN
    private var stylusBatteryPct: Int = 100
    private var isPalmRejectionActive: Boolean = true
    private val completedStrokes = mutableListOf<StylusStroke>()
    private val currentPoints = mutableListOf<StylusPoint>()

    val maxPressureLevels: Int = 4096
    val samplingRateHz: Int = 240

    fun getActiveTool(): StylusToolType = activeTool
    fun getBatteryLevel(): Int = stylusBatteryPct
    fun getCompletedStrokes(): List<StylusStroke> = completedStrokes.toList()

    fun selectTool(tool: StylusToolType) {
        activeTool = tool
    }

    fun setBatteryLevel(pct: Int) {
        stylusBatteryPct = max(0, min(100, pct))
    }

    fun isContactPalmRejected(touchX: Float, touchY: Float, lastStylusX: Float, lastStylusY: Float, radiusThresholdPx: Float = 60f): Boolean {
        if (!isPalmRejectionActive) return false
        val distance = hypot((touchX - lastStylusX).toDouble(), (touchY - lastStylusY).toDouble()).toFloat()
        return distance <= radiusThresholdPx
    }

    fun recordSample(
        x: Float,
        y: Float,
        pressureLevel: Int,
        tiltAngle: Float = 45f,
        azimuth: Float = 90f,
        isHovering: Boolean = false
    ): StylusPoint {
        val clampedPressure = max(0, min(maxPressureLevels - 1, pressureLevel))
        val clampedTilt = max(0f, min(90f, tiltAngle))
        val point = StylusPoint(
            x = x,
            y = y,
            pressureLevel = clampedPressure,
            tiltAngleDegrees = clampedTilt,
            azimuthDegrees = azimuth % 360f,
            isHovering = isHovering
        )
        if (!isHovering) {
            currentPoints.add(point)
        }
        return point
    }

    fun finishStroke(): StylusStroke? {
        if (currentPoints.isEmpty()) return null

        val pointsCopy = currentPoints.toList()
        currentPoints.clear()

        val avgPressure = pointsCopy.map { it.pressureLevel }.average()
        var totalDist = 0f
        for (i in 1 until pointsCopy.size) {
            val p1 = pointsCopy[i - 1]
            val p2 = pointsCopy[i]
            totalDist += hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
        }

        // Quadratic pressure width mapping (1.0 to 12.0 dp)
        val normalizedPressure = (avgPressure / maxPressureLevels.toDouble()).toFloat()
        val baseWidth = when (activeTool) {
            StylusToolType.BALLPOINT_PEN -> 2.5f
            StylusToolType.CALLIGRAPHY_BRUSH -> 6.0f
            StylusToolType.HIGHLIGHTER -> 18.0f
            StylusToolType.PRECISION_ERASER -> 12.0f
            StylusToolType.LASER_POINTER -> 4.0f
        }
        val computedWidth = baseWidth * (0.5f + (normalizedPressure * 1.5f))

        val stroke = StylusStroke(
            strokeId = "STRK-${System.currentTimeMillis() % 100000}",
            toolType = activeTool,
            points = pointsCopy,
            averagePressure = avgPressure,
            totalDistancePx = totalDist,
            smoothedStrokeWidth = computedWidth
        )
        completedStrokes.add(stroke)
        return stroke
    }

    fun computeStrokeVelocityPxPerMs(stroke: StylusStroke): Float {
        if (stroke.points.size < 2) return 0f
        val durationMs = stroke.points.last().timestampMs - stroke.points.first().timestampMs
        if (durationMs <= 0) return 0f
        return stroke.totalDistancePx / durationMs.toFloat()
    }
}
