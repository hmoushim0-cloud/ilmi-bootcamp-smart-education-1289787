package sa.factory.ilmibootcampsmarte

import sa.factory.ilmibootcampsmarte.data.repository.IlmiBootcampRepositoryImpl
import sa.factory.ilmibootcampsmarte.engine.BootcampCurriculumAttendanceEngine
import sa.factory.ilmibootcampsmarte.engine.BootcampTrack
import sa.factory.ilmibootcampsmarte.engine.StylusDigitizerProtocolEngine
import sa.factory.ilmibootcampsmarte.engine.StylusToolType
import sa.factory.ilmibootcampsmarte.hardware.HardwareSensorDriver
import sa.factory.ilmibootcampsmarte.model.IlmiBootcampItem
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IlmiBootcampRegressionTest {

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = IlmiBootcampRepositoryImpl(sensorDriver)

        val item1 = IlmiBootcampItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = IlmiBootcampItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testStylusToolSelectionAndBattery() {
        val engine = StylusDigitizerProtocolEngine()
        assertEquals(StylusToolType.BALLPOINT_PEN, engine.getActiveTool())

        engine.selectTool(StylusToolType.CALLIGRAPHY_BRUSH)
        assertEquals(StylusToolType.CALLIGRAPHY_BRUSH, engine.getActiveTool())

        engine.setBatteryLevel(88)
        assertEquals(88, engine.getBatteryLevel())

        engine.setBatteryLevel(150) // clamp check
        assertEquals(100, engine.getBatteryLevel())
    }

    @Test
    fun testStylusPressureLevelClamping() {
        val engine = StylusDigitizerProtocolEngine()
        assertEquals(4096, engine.maxPressureLevels)

        val pointExceed = engine.recordSample(10f, 20f, 5000)
        assertEquals(4095, pointExceed.pressureLevel)

        val pointNegative = engine.recordSample(15f, 25f, -100)
        assertEquals(0, pointNegative.pressureLevel)
    }

    @Test
    fun testStylusStrokeDistanceAndSmoothing() {
        val engine = StylusDigitizerProtocolEngine()
        engine.selectTool(StylusToolType.HIGHLIGHTER)

        engine.recordSample(0f, 0f, 2048)
        engine.recordSample(30f, 40f, 2048) // dist = 50px

        val stroke = engine.finishStroke()
        assertNotNull(stroke)
        assertEquals(50f, stroke!!.totalDistancePx, 0.1f)
        assertTrue("Highlighter stroke width should be > 10dp", stroke.smoothedStrokeWidth > 10f)
        assertEquals(1, engine.getCompletedStrokes().size)
    }

    @Test
    fun testStylusPalmRejectionGatekeeper() {
        val engine = StylusDigitizerProtocolEngine()
        // Proximity contact within 60px should be rejected as palm rest
        val isPalm = engine.isContactPalmRejected(touchX = 120f, touchY = 125f, lastStylusX = 100f, lastStylusY = 100f, radiusThresholdPx = 60f)
        assertTrue(isPalm)

        // Far contact (> 60px) should not be rejected
        val isNotPalm = engine.isContactPalmRejected(touchX = 300f, touchY = 300f, lastStylusX = 100f, lastStylusY = 100f, radiusThresholdPx = 60f)
        assertFalse(isNotPalm)
    }

    @Test
    fun testStylusStrokeVelocityCalculation() {
        val engine = StylusDigitizerProtocolEngine()
        engine.recordSample(0f, 0f, 1024)
        engine.recordSample(100f, 0f, 2048)
        val stroke = engine.finishStroke()
        assertNotNull(stroke)

        val velocity = engine.computeStrokeVelocityPxPerMs(stroke!!)
        assertTrue(velocity >= 0f)
    }

    @Test
    fun testBootcampStudentRosterEnrollment() {
        val attendance = BootcampCurriculumAttendanceEngine()
        val students = attendance.getStudents()
        assertEquals(4, students.size)

        val student = attendance.findStudentById("STD-101")
        assertNotNull(student)
        assertEquals(BootcampTrack.AUTONOMOUS_AI_AGENTS, student?.track)
        assertTrue(student!!.gpaScore >= 4.9)
    }

    @Test
    fun testBootcampProgressCalculation() {
        val attendance = BootcampCurriculumAttendanceEngine()
        val student = attendance.findStudentById("STD-102")
        assertNotNull(student)

        // 11 of 12 modules completed = (11 / 12) * 100 ~ 91.67%
        val progress = attendance.calculateStudentProgressPct(student!!)
        assertEquals(91.66, progress, 0.5)
    }

    @Test
    fun testBleAttendanceBeaconProximityVerification() {
        val attendance = BootcampCurriculumAttendanceEngine()

        // Valid room beacon with strong RSSI (-65 dBm >= -75 dBm)
        val validRecord = attendance.verifyAndCheckIn(
            studentId = "STD-101",
            beaconUuid = attendance.standardBeaconUuid,
            majorId = 101,
            minorId = 2,
            rssiDbm = -65
        )
        assertNotNull(validRecord)
        assertTrue(validRecord!!.isProximityVerified)

        // Weak signal outside classroom (-85 dBm < -75 dBm)
        val weakRecord = attendance.verifyAndCheckIn(
            studentId = "STD-102",
            beaconUuid = attendance.standardBeaconUuid,
            majorId = 101,
            minorId = 2,
            rssiDbm = -85
        )
        assertNotNull(weakRecord)
        assertFalse(weakRecord!!.isProximityVerified)
    }

    @Test
    fun testHardwareSensorDriverLiveStreamFrames() = runBlocking {
        val driver = HardwareSensorDriver()
        val frames = driver.observePeripheralStream().take(2).toList()

        assertEquals("CONNECTING", frames[0].connectionState)
        assertEquals("ONLINE", frames[1].connectionState)
        assertTrue(frames[1].livePayload.contains("Smart Stylus Digitizer"))
    }
}
