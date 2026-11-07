package sa.factory.ilmibootcampsmarte.hardware

import sa.factory.ilmibootcampsmarte.engine.BootcampCurriculumAttendanceEngine
import sa.factory.ilmibootcampsmarte.engine.StylusDigitizerProtocolEngine
import sa.factory.ilmibootcampsmarte.engine.StylusToolType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver(
    val stylusEngine: StylusDigitizerProtocolEngine = StylusDigitizerProtocolEngine(),
    val attendanceEngine: BootcampCurriculumAttendanceEngine = BootcampCurriculumAttendanceEngine()
) {
    private val peripheral = "Smart Stylus Digitizer 4096-Level + BLE Beacon"

    init {
        // Pre-warm sample simulation data
        stylusEngine.selectTool(StylusToolType.BALLPOINT_PEN)
        stylusEngine.recordSample(120f, 340f, 2048, 45f, 90f, false)
        stylusEngine.recordSample(125f, 350f, 3200, 50f, 95f, false)
        stylusEngine.finishStroke()

        attendanceEngine.verifyAndCheckIn(
            studentId = "STD-101",
            beaconUuid = attendanceEngine.standardBeaconUuid,
            majorId = 101,
            minorId = 4,
            rssiDbm = -62
        )
    }

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "Handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "Smart Stylus Digitizer 4096-Level + BLE Beacon: ONLINE | READY"))

        while (true) {
            delay(1500)
            val jitter = Random.nextInt(1, 8)
            val tool = stylusEngine.getActiveTool()
            val battery = stylusEngine.getBatteryLevel()
            val rate = attendanceEngine.getVerifiedAttendanceRate()
            val strokes = stylusEngine.getCompletedStrokes().size

            val payload = "Stylus: $tool | Battery: $battery% | Strokes: $strokes | Attendance Verified: ${String.format("%.0f", rate)}% (Jitter: +${jitter}ms)"
            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", payload))
        }
    }
}
