package io.github.shadowfax505.kuetbuslive.model

// Everything here mirrors docs/data-model.md in the iOS repository, so both apps
// read and write the same Firestore documents. Field names and enum strings must
// stay exactly the same on both platforms.

enum class UserRole(val raw: String) {
    STUDENT("student"),
    TEACHER("teacher"),
    DRIVER("driver"),
    ADMIN("admin");

    val title: String
        get() = when (this) {
            STUDENT -> "Student"
            TEACHER -> "Teacher"
            DRIVER -> "Driver"
            ADMIN -> "Transport Admin"
        }

    val idLabel: String
        get() = when (this) {
            STUDENT -> "Roll number"
            TEACHER -> "Employee ID"
            DRIVER -> "Driver ID"
            ADMIN -> "Staff ID"
        }

    val isRider: Boolean get() = this == STUDENT || this == TEACHER

    companion object {
        // Admins are made by the transport office, never from the sign-up screen.
        val signUpRoles = listOf(STUDENT, TEACHER, DRIVER)
        fun from(raw: String?): UserRole? = entries.firstOrNull { it.raw == raw }
    }
}

// Who a bus is allocated to. "Normal" trips are for students, "Special" trips for teachers.
enum class BusAudience(val raw: String) {
    STUDENT("student"),
    TEACHER("teacher");

    val title: String get() = if (this == STUDENT) "Student bus" else "Teacher bus"

    // Teachers (and staff) can ride any bus. Students only ride student buses.
    fun canBoard(role: UserRole): Boolean = role != UserRole.STUDENT || this == STUDENT

    companion object {
        fun from(raw: String?): BusAudience = if (raw == TEACHER.raw) TEACHER else STUDENT

        // "Special-1 (AC)" -> teacher, "Normal-2" -> student
        fun fromTripName(tripName: String): BusAudience =
            if (tripName.trim().lowercase().startsWith("special")) TEACHER else STUDENT
    }
}

enum class CrowdLevel(val raw: String) {
    UNKNOWN("unknown"),
    SEATS("seats"),
    FULL("full");

    val label: String
        get() = when (this) {
            UNKNOWN -> "No report yet"
            SEATS -> "Seats available"
            FULL -> "Full"
        }

    companion object {
        fun from(raw: String?): CrowdLevel = entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}

enum class TripDays(val raw: String) {
    SUN_TO_THU("sunToThu"),
    SATURDAY("saturday");

    val title: String get() = if (this == SUN_TO_THU) "Sunday to Thursday" else "Saturday"

    // java.time.DayOfWeek values: 1 = Monday ... 7 = Sunday. Friday has no buses.
    fun includes(dayOfWeek: Int): Boolean = when (this) {
        SUN_TO_THU -> dayOfWeek == 7 || dayOfWeek in 1..4
        SATURDAY -> dayOfWeek == 6
    }

    companion object {
        fun from(raw: String?): TripDays = entries.firstOrNull { it.raw == raw } ?: SUN_TO_THU
    }
}

enum class TripPeriod(val raw: String) {
    MORNING("morning"),
    NOON("noon"),
    AFTERNOON("afternoon"),
    NIGHT("night");

    val title: String get() = raw.replaceFirstChar { it.uppercase() }

    companion object {
        fun from(raw: String?): TripPeriod = entries.firstOrNull { it.raw == raw } ?: MORNING
    }
}

data class LatLng(val lat: Double, val lng: Double)

data class AppUser(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val kuetId: String? = null,
    val isApproved: Boolean = false,
    val active: Boolean = true,
    val favoriteStopName: String? = null,
    val favoriteStopLat: Double? = null,
    val favoriteStopLng: Double? = null,
) {
    // Teachers and drivers wait for the transport admin before they can use the app.
    val needsApproval: Boolean get() = role == UserRole.TEACHER || role == UserRole.DRIVER

    val favoriteStop: Stop?
        get() {
            val name = favoriteStopName ?: return null
            val lat = favoriteStopLat ?: return null
            val lng = favoriteStopLng ?: return null
            return Stop(name, null, lat, lng)
        }

    val firstName: String get() = name.trim().split(" ").firstOrNull().orEmpty().ifBlank { name }
}

data class Stop(
    val name: String,          // English name, also the stop's identity
    val nameBn: String?,       // optional Bangla name
    val lat: Double,
    val lng: Double,
) {
    val position: LatLng get() = LatLng(lat, lng)
    fun displayName(bangla: Boolean): String = if (bangla && !nameBn.isNullOrBlank()) nameBn else name
}

data class BusRoute(
    val id: String,
    val name: String,
    val nameBn: String?,
    val stops: List<Stop>,
) {
    fun displayName(bangla: Boolean): String = if (bangla && !nameBn.isNullOrBlank()) nameBn else name
}

data class BusSchedule(
    val id: String,
    val tripName: String,       // "Normal-1", "Special-1 (AC)"
    val audience: BusAudience,
    val days: TripDays,
    val period: TripPeriod,
    val campusTime: String,     // "06:30", leaves KUET
    val spot: String,           // starting place in the city
    val spotBn: String?,
    val spotTime: String,       // "07:10", leaves the spot
    val remarks: String,
) {
    // Saved on the live bus document and shown on the map: "Normal-1 · Rupsha".
    val title: String get() = "$tripName · $spot"

    fun spotName(bangla: Boolean): String = if (bangla && !spotBn.isNullOrBlank()) spotBn else spot
}

data class Bus(
    val id: String,
    val number: String,        // "Normal-2 · Rupsha"
    val routeId: String,
    val routeName: String,
    val driverId: String,
    val lat: Double,
    val lng: Double,
    val updatedAtMillis: Long,
    val crowd: CrowdLevel,
    val audience: BusAudience,
    val scheduleId: String?,
    val fleetBusId: String?,
    val heading: Double?,      // degrees from north
    val speed: Double?,        // metres per second
) {
    val position: LatLng get() = LatLng(lat, lng)

    // "Normal-2" from "Normal-2 · Rupsha".
    val tripName: String get() = number.substringBefore(" · ")
    val spot: String get() = number.substringAfter(" · ", "")

    val speedKmh: Int? get() = speed?.takeIf { it >= 0 }?.let { (it * 3.6).toInt() }

    // No position for a minute usually means the driver lost signal.
    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis - updatedAtMillis > 60_000

    fun lastUpdateText(nowMillis: Long = System.currentTimeMillis()): String {
        val seconds = ((nowMillis - updatedAtMillis) / 1000).coerceAtLeast(0)
        return if (seconds < 60) "$seconds s ago" else "${seconds / 60} min ago"
    }
}

data class BusCover(
    val driverId: String,
    val fromDate: String,      // "2026-11-10", first day
    val untilDate: String,     // last day, inclusive
    val startMillis: Long,     // 00:00 on fromDate in Dhaka
    val endMillis: Long,       // 00:00 on the day after untilDate in Dhaka
    val reason: String,
    val setBy: String,
)

data class FleetBus(
    val id: String,            // "normal-1"
    val name: String,          // "Normal-1"
    val defaultDriverId: String?,
    val cover: BusCover?,
)

enum class AbsenceStatus(val raw: String) {
    OPEN("open"),
    COVERED("covered"),
    CANCELLED("cancelled");

    val title: String
        get() = when (this) {
            OPEN -> "Waiting for the office"
            COVERED -> "Cover arranged"
            CANCELLED -> "Cancelled"
        }

    companion object {
        fun from(raw: String?): AbsenceStatus = entries.firstOrNull { it.raw == raw } ?: OPEN
    }
}

data class DriverAbsence(
    val id: String,
    val driverId: String,
    val driverName: String,
    val fromDate: String,
    val untilDate: String,
    val reason: String,
    val status: AbsenceStatus,
    val createdAtMillis: Long,
    val coverDriverId: String?,
)

// "I'm waiting here": a rider tells the driver where they stand.
data class Wave(
    val uid: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
) {
    val position: LatLng get() = LatLng(lat, lng)
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean = expiresAtMillis > nowMillis

    companion object {
        const val LIFETIME_MILLIS = 15 * 60 * 1000L
    }
}

enum class ComplaintCategory(val raw: String, val title: String) {
    LATE("late", "Bus late / didn't come"),
    DRIVER_BEHAVIOUR("driverBehaviour", "Driver behaviour"),
    OVERCROWDING("overcrowding", "Overcrowding"),
    BUS_CONDITION("busCondition", "Bus condition"),
    APP("app", "App problem"),
    OTHER("other", "Other");

    companion object {
        fun from(raw: String?): ComplaintCategory = entries.firstOrNull { it.raw == raw } ?: OTHER
    }
}

enum class ComplaintStatus(val raw: String, val title: String) {
    OPEN("open", "Open"),
    IN_REVIEW("inReview", "In review"),
    RESOLVED("resolved", "Resolved");

    companion object {
        fun from(raw: String?): ComplaintStatus = entries.firstOrNull { it.raw == raw } ?: OPEN
    }
}

data class Complaint(
    val id: String,
    val uid: String,
    val userName: String,
    val userRole: String,
    val category: ComplaintCategory,
    val routeName: String?,
    val busName: String?,
    val tripTimeMillis: Long?,
    val text: String,
    val createdAtMillis: Long,
    val status: ComplaintStatus,
    val readByAdmin: Boolean,
    val adminReply: String?,
    val repliedAtMillis: Long?,
) {
    val busAndRoute: String?
        get() = listOfNotNull(busName, routeName).filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")

    companion object {
        const val MAX_TEXT_LENGTH = 2000
    }
}

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
    val postedBy: String,
    val routeName: String?,
)
