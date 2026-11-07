package sa.factory.ilmibootcampsmarte.engine

enum class BootcampTrack(val arabicTitle: String, val totalModules: Int) {
    FULL_STACK_KOTLIN("تطوير تطبيقات كوتلن المتقدمة بـ Jetpack Compose", 12),
    CLOUD_ARCHITECTURE("معمارية السحابة والأنظمة الموزعة", 10),
    AUTONOMOUS_AI_AGENTS("هندسة وكلاء الذكاء الاصطناعي المستقلين", 14),
    CYBERSECURITY("الأمن السيبراني واختبار الاختراق الأخلاقي", 16)
}

data class BootcampStudent(
    val studentId: String,
    val fullName: String,
    val track: BootcampTrack,
    val completedModules: Int,
    val gpaScore: Double,
    val isCapstoneApproved: Boolean = false
) {
    val id: String get() = studentId
}

data class BeaconAttendanceRecord(
    val recordId: String,
    val studentId: String,
    val beaconUuid: String,
    val campusMajorId: Int,
    val classroomMinorId: Int,
    val measuredRssiDbm: Int,
    val isProximityVerified: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class BootcampCurriculumAttendanceEngine {
    val standardBeaconUuid: String = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0"
    val proximityRssiThresholdDbm: Int = -75

    private val students = mutableListOf(
        BootcampStudent("STD-101", "عبد الله بن فهد التميمي", BootcampTrack.AUTONOMOUS_AI_AGENTS, 12, 4.92, true),
        BootcampStudent("STD-102", "سارة بنت خالد الشمري", BootcampTrack.FULL_STACK_KOTLIN, 11, 4.96, true),
        BootcampStudent("STD-103", "محمد بن إبراهيم الدوسري", BootcampTrack.CLOUD_ARCHITECTURE, 9, 4.85, false),
        BootcampStudent("STD-104", "نورة بنت سعد القحطاني", BootcampTrack.CYBERSECURITY, 15, 4.98, true)
    )

    private val attendanceLog = mutableListOf<BeaconAttendanceRecord>()

    fun getStudents(): List<BootcampStudent> = students.toList()
    fun getAttendanceRecords(): List<BeaconAttendanceRecord> = attendanceLog.toList()

    fun findStudentById(id: String): BootcampStudent? = students.find { it.studentId == id || it.id == id }

    fun verifyAndCheckIn(
        studentId: String,
        beaconUuid: String,
        majorId: Int,
        minorId: Int,
        rssiDbm: Int
    ): BeaconAttendanceRecord? {
        val student = findStudentById(studentId) ?: return null
        val uuidMatches = beaconUuid.equals(standardBeaconUuid, ignoreCase = true)
        val isVerified = uuidMatches && (rssiDbm >= proximityRssiThresholdDbm)

        val record = BeaconAttendanceRecord(
            recordId = "ATT-${System.currentTimeMillis() % 100000}",
            studentId = student.studentId,
            beaconUuid = beaconUuid,
            campusMajorId = majorId,
            classroomMinorId = minorId,
            measuredRssiDbm = rssiDbm,
            isProximityVerified = isVerified
        )
        attendanceLog.add(record)
        return record
    }

    fun calculateStudentProgressPct(student: BootcampStudent): Double {
        val total = student.track.totalModules
        if (total == 0) return 0.0
        val pct = (student.completedModules.toDouble() / total.toDouble()) * 100.0
        return if (pct > 100.0) 100.0 else pct
    }

    fun getVerifiedAttendanceRate(): Double {
        if (attendanceLog.isEmpty()) return 100.0
        val verifiedCount = attendanceLog.count { it.isProximityVerified }
        return (verifiedCount.toDouble() / attendanceLog.size.toDouble()) * 100.0
    }
}
