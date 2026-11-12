package io.github.shadowfax505.kuetbuslive.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import io.github.shadowfax505.kuetbuslive.model.AbsenceStatus
import io.github.shadowfax505.kuetbuslive.model.Announcement
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.BusAudience
import io.github.shadowfax505.kuetbuslive.model.BusCover
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Complaint
import io.github.shadowfax505.kuetbuslive.model.ComplaintCategory
import io.github.shadowfax505.kuetbuslive.model.ComplaintStatus
import io.github.shadowfax505.kuetbuslive.model.CrowdLevel
import io.github.shadowfax505.kuetbuslive.model.DriverAbsence
import io.github.shadowfax505.kuetbuslive.model.FleetBus
import io.github.shadowfax505.kuetbuslive.model.Stop
import io.github.shadowfax505.kuetbuslive.model.TripDays
import io.github.shadowfax505.kuetbuslive.model.TripPeriod
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.model.Wave
import java.util.Date

// Reads Firestore documents by hand instead of with reflection, so a missing or
// unexpected field (for example a document written by an older iOS build) never
// crashes the app: the document is skipped or a safe default is used.

private fun Any?.asDouble(): Double? = (this as? Number)?.toDouble()

private fun Any?.asMillis(): Long? = when (this) {
    is Timestamp -> toDate().time
    is Date -> time
    is Number -> toLong()
    else -> null
}

fun Long.toTimestamp(): Timestamp = Timestamp(Date(this))

private fun DocumentSnapshot.str(field: String): String? = getString(field)
private fun DocumentSnapshot.dbl(field: String): Double? = get(field).asDouble()
private fun DocumentSnapshot.millis(field: String): Long? = get(field).asMillis()

fun DocumentSnapshot.toAppUser(): AppUser? {
    val role = UserRole.from(str("role")) ?: return null
    return AppUser(
        id = id,
        name = str("name") ?: "",
        email = str("email") ?: "",
        role = role,
        kuetId = str("kuetId"),
        isApproved = getBoolean("isApproved") ?: false,
        active = getBoolean("active") ?: true,
        favoriteStopName = str("favoriteStopName"),
        favoriteStopLat = dbl("favoriteStopLat"),
        favoriteStopLng = dbl("favoriteStopLng"),
    )
}

fun DocumentSnapshot.toRoute(): BusRoute? {
    val name = str("name") ?: return null
    @Suppress("UNCHECKED_CAST")
    val rawStops = get("stops") as? List<Map<String, Any?>> ?: emptyList()
    val stops = rawStops.mapNotNull { map ->
        val stopName = map["name"] as? String ?: return@mapNotNull null
        val lat = map["lat"].asDouble() ?: return@mapNotNull null
        val lng = map["lng"].asDouble() ?: return@mapNotNull null
        Stop(stopName, map["nameBn"] as? String, lat, lng)
    }
    return BusRoute(id, name, str("nameBn"), stops)
}

fun Stop.toMap(): Map<String, Any> = buildMap {
    put("name", name)
    nameBn?.takeIf { it.isNotBlank() }?.let { put("nameBn", it) }
    put("lat", lat)
    put("lng", lng)
}

fun DocumentSnapshot.toSchedule(): BusSchedule? {
    val tripName = str("tripName") ?: return null
    return BusSchedule(
        id = id,
        tripName = tripName,
        audience = str("audience")?.let { BusAudience.from(it) } ?: BusAudience.fromTripName(tripName),
        days = TripDays.from(str("days")),
        period = TripPeriod.from(str("period")),
        campusTime = str("campusTime") ?: "",
        spot = str("spot") ?: "",
        spotBn = str("spotBn"),
        spotTime = str("spotTime") ?: "",
        remarks = str("remarks") ?: "",
    )
}

fun BusSchedule.toMap(): Map<String, Any> = buildMap {
    put("tripName", tripName)
    put("audience", audience.raw)
    put("days", days.raw)
    put("period", period.raw)
    put("campusTime", campusTime)
    put("spot", spot)
    spotBn?.takeIf { it.isNotBlank() }?.let { put("spotBn", it) }
    put("spotTime", spotTime)
    put("remarks", remarks)
}

fun DocumentSnapshot.toBus(): Bus? {
    val lat = dbl("lat") ?: return null
    val lng = dbl("lng") ?: return null
    // A trip that has not sent its first GPS fix yet sits at 0,0: keep it off the map.
    if (lat == 0.0 && lng == 0.0) return null
    return Bus(
        id = id,
        number = str("number") ?: "Bus",
        routeId = str("routeId") ?: "",
        routeName = str("routeName") ?: "",
        driverId = str("driverId") ?: "",
        lat = lat,
        lng = lng,
        updatedAtMillis = millis("updatedAt") ?: 0L,
        crowd = CrowdLevel.from(str("crowd")),
        // Trips saved before allocation existed count as student buses.
        audience = BusAudience.from(str("audience")),
        scheduleId = str("scheduleId"),
        fleetBusId = str("fleetBusId"),
        heading = dbl("heading"),
        speed = dbl("speed"),
    )
}

fun DocumentSnapshot.toFleetBus(): FleetBus? {
    val name = str("name") ?: return null
    @Suppress("UNCHECKED_CAST")
    val cover = (get("cover") as? Map<String, Any?>)?.let { map ->
        val driverId = map["driverId"] as? String ?: return@let null
        BusCover(
            driverId = driverId,
            fromDate = map["fromDate"] as? String ?: "",
            untilDate = map["untilDate"] as? String ?: "",
            startMillis = map["start"].asMillis() ?: 0L,
            endMillis = map["end"].asMillis() ?: 0L,
            reason = map["reason"] as? String ?: "",
            setBy = map["setBy"] as? String ?: "",
        )
    }
    return FleetBus(id, name, str("defaultDriverId"), cover)
}

fun BusCover.toMap(): Map<String, Any> = mapOf(
    "driverId" to driverId,
    "fromDate" to fromDate,
    "untilDate" to untilDate,
    "start" to startMillis.toTimestamp(),
    "end" to endMillis.toTimestamp(),
    "reason" to reason,
    "setBy" to setBy,
)

fun DocumentSnapshot.toAbsence(): DriverAbsence? {
    val driverId = str("driverId") ?: return null
    return DriverAbsence(
        id = id,
        driverId = driverId,
        driverName = str("driverName") ?: "",
        fromDate = str("fromDate") ?: "",
        untilDate = str("untilDate") ?: "",
        reason = str("reason") ?: "",
        status = AbsenceStatus.from(str("status")),
        createdAtMillis = millis("createdAt") ?: 0L,
        coverDriverId = str("coverDriverId"),
    )
}

fun DocumentSnapshot.toWave(): Wave? {
    val lat = dbl("lat") ?: return null
    val lng = dbl("lng") ?: return null
    return Wave(
        uid = str("uid") ?: id,
        label = str("label") ?: "",
        lat = lat,
        lng = lng,
        createdAtMillis = millis("createdAt") ?: 0L,
        expiresAtMillis = millis("expiresAt") ?: 0L,
    )
}

fun DocumentSnapshot.toComplaint(): Complaint? {
    val uid = str("uid") ?: return null
    return Complaint(
        id = id,
        uid = uid,
        userName = str("userName") ?: "",
        userRole = str("userRole") ?: "",
        category = ComplaintCategory.from(str("category")),
        routeName = str("routeName"),
        busName = str("busName"),
        tripTimeMillis = millis("tripTime"),
        text = str("text") ?: "",
        createdAtMillis = millis("createdAt") ?: 0L,
        status = ComplaintStatus.from(str("status")),
        readByAdmin = getBoolean("readByAdmin") ?: false,
        adminReply = str("adminReply"),
        repliedAtMillis = millis("repliedAt"),
    )
}

fun DocumentSnapshot.toAnnouncement(): Announcement? {
    val title = str("title") ?: return null
    return Announcement(
        id = id,
        title = title,
        body = str("body") ?: "",
        createdAtMillis = millis("createdAt") ?: 0L,
        postedBy = str("postedBy") ?: "",
        routeName = str("routeName"),
    )
}
