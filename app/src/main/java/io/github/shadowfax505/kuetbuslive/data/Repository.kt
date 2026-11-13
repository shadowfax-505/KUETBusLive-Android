package io.github.shadowfax505.kuetbuslive.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.shadowfax505.kuetbuslive.model.AbsenceStatus
import io.github.shadowfax505.kuetbuslive.model.Announcement
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.Bus
import io.github.shadowfax505.kuetbuslive.model.BusCover
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Complaint
import io.github.shadowfax505.kuetbuslive.model.ComplaintCategory
import io.github.shadowfax505.kuetbuslive.model.ComplaintStatus
import io.github.shadowfax505.kuetbuslive.model.CrowdLevel
import io.github.shadowfax505.kuetbuslive.model.DriverAbsence
import io.github.shadowfax505.kuetbuslive.model.FleetBus
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.model.Wave
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// One place for every Firestore read and write. Collections and fields follow
// docs/data-model.md in the iOS repository, and the shared firestore.rules decide
// who may do what, exactly as for the iOS app.
object Repository {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    // A live query as a Flow. A listener error (for example "permission denied" right
    // after signing out) ends in an empty list instead of crashing the screen.
    private fun <T> Query.live(map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull(map) ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    // ---------- Users ----------

    fun profile(uid: String): Flow<AppUser?> = callbackFlow {
        val registration = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.takeIf { it.exists() }?.toAppUser())
        }
        awaitClose { registration.remove() }
    }

    suspend fun createProfile(uid: String, name: String, email: String, role: UserRole, kuetId: String) {
        // Only students start approved; the rules check this too.
        val data = mapOf(
            "name" to name,
            "email" to email,
            "role" to role.raw,
            "kuetId" to kuetId,
            "isApproved" to (role == UserRole.STUDENT),
        )
        db.collection("users").document(uid).set(data).await()
    }

    suspend fun updateName(uid: String, name: String) {
        db.collection("users").document(uid).update("name", name).await()
    }

    suspend fun saveFavoriteStop(uid: String, name: String, lat: Double, lng: Double) {
        db.collection("users").document(uid)
            .update(mapOf("favoriteStopName" to name, "favoriteStopLat" to lat, "favoriteStopLng" to lng))
            .await()
    }

    // Admin: teachers and drivers waiting for approval, and every driver.
    fun usersWithRoles(roles: List<UserRole>): Flow<List<AppUser>> =
        db.collection("users").whereIn("role", roles.map { it.raw }).live { it.toAppUser() }

    suspend fun setApproved(uid: String, approved: Boolean) {
        db.collection("users").document(uid).update("isApproved", approved).await()
    }

    suspend fun setActive(uid: String, active: Boolean) {
        db.collection("users").document(uid).update("active", active).await()
    }

    // ---------- Timetable and routes ----------

    fun schedules(): Flow<List<BusSchedule>> = db.collection("schedules").live { it.toSchedule() }

    suspend fun saveSchedule(schedule: BusSchedule) {
        val collection = db.collection("schedules")
        val ref = if (schedule.id.isBlank()) collection.document() else collection.document(schedule.id)
        ref.set(schedule.toMap()).await()
    }

    suspend fun deleteSchedule(id: String) {
        db.collection("schedules").document(id).delete().await()
    }

    fun routes(): Flow<List<BusRoute>> = db.collection("routes").live { it.toRoute() }

    // ---------- Live buses ----------

    fun buses(): Flow<List<Bus>> = db.collection("buses").live { it.toBus() }

    suspend fun reportCrowd(busId: String, crowd: CrowdLevel) {
        db.collection("buses").document(busId).update("crowd", crowd.raw).await()
    }

    suspend fun sendWave(busId: String, uid: String, label: String, lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        val data = mapOf(
            "uid" to uid,
            "label" to label.take(60),
            "lat" to lat,
            "lng" to lng,
            "createdAt" to now.toTimestamp(),
            "expiresAt" to (now + Wave.LIFETIME_MILLIS).toTimestamp(),
        )
        db.collection("buses").document(busId).collection("waves").document(uid).set(data).await()
    }

    suspend fun cancelWave(busId: String, uid: String) {
        db.collection("buses").document(busId).collection("waves").document(uid).delete().await()
    }

    fun waves(busId: String): Flow<List<Wave>> =
        db.collection("buses").document(busId).collection("waves").live { it.toWave() }

    suspend fun deleteWave(busId: String, uid: String) {
        runCatching { db.collection("buses").document(busId).collection("waves").document(uid).delete().await() }
    }

    // ---------- Driver trips ----------

    // First GPS fix of a trip: the whole live document. The rules check that this
    // fleet bus is driven by this driver today.
    suspend fun createLiveBus(
        busDocId: String,
        schedule: BusSchedule,
        route: BusRoute,
        fleetBusId: String,
        driverId: String,
        lat: Double,
        lng: Double,
        heading: Double?,
        speed: Double?,
    ) {
        val data = buildMap<String, Any> {
            put("number", schedule.title)
            put("routeId", route.id)
            put("routeName", route.name)
            put("driverId", driverId)
            put("lat", lat)
            put("lng", lng)
            put("updatedAt", FieldValue.serverTimestamp())
            put("crowd", CrowdLevel.UNKNOWN.raw)
            put("audience", schedule.audience.raw)
            put("scheduleId", schedule.id)
            put("fleetBusId", fleetBusId)
            heading?.let { put("heading", it) }
            speed?.let { put("speed", it) }
        }
        db.collection("buses").document(busDocId).set(data).await()
    }

    // Later fixes: only the moving fields, so crowd reports are kept.
    suspend fun moveLiveBus(busDocId: String, lat: Double, lng: Double, heading: Double?, speed: Double?) {
        val fields = buildMap<String, Any> {
            put("lat", lat)
            put("lng", lng)
            put("updatedAt", FieldValue.serverTimestamp())
            heading?.let { put("heading", it) }
            speed?.let { put("speed", it) }
        }
        db.collection("buses").document(busDocId).update(fields).await()
    }

    suspend fun endLiveBus(busDocId: String) {
        db.collection("buses").document(busDocId).delete().await()
    }

    suspend fun recordTripStart(driverId: String, fleetBusId: String, scheduleId: String, routeId: String): String {
        val ref = db.collection("trips").document()
        ref.set(
            mapOf(
                "driverId" to driverId,
                "fleetBusId" to fleetBusId,
                "scheduleId" to scheduleId,
                "routeId" to routeId,
                "startedAt" to System.currentTimeMillis().toTimestamp(),
            )
        ).await()
        return ref.id
    }

    suspend fun recordTripEnd(tripId: String) {
        db.collection("trips").document(tripId).update("endedAt", System.currentTimeMillis().toTimestamp()).await()
    }

    // ---------- Fleet and absences ----------

    fun fleet(): Flow<List<FleetBus>> = db.collection("fleet").live { it.toFleetBus() }

    suspend fun setDefaultDriver(fleetBusId: String, busName: String, driverId: String?) {
        db.collection("fleet").document(fleetBusId)
            .set(mapOf("name" to busName, "defaultDriverId" to driverId), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun setCover(fleetBusId: String, cover: BusCover?) {
        db.collection("fleet").document(fleetBusId)
            .update("cover", cover?.toMap() ?: FieldValue.delete())
            .await()
    }

    // A different driver for one trip of this bus, or null to give the trip back to the
    // bus's usual (cover or regular) driver. Same field as the iOS app: tripDrivers.<scheduleId>.
    suspend fun setTripDriver(fleetBusId: String, busName: String, scheduleId: String, driverId: String?) {
        val ref = db.collection("fleet").document(fleetBusId)
        if (driverId != null) {
            ref.set(mapOf("name" to busName, "tripDrivers" to mapOf(scheduleId to driverId)), com.google.firebase.firestore.SetOptions.merge()).await()
        } else {
            ref.update(com.google.firebase.firestore.FieldPath.of("tripDrivers", scheduleId), FieldValue.delete()).await()
        }
    }

    fun myAbsences(driverId: String): Flow<List<DriverAbsence>> =
        db.collection("driverAbsences").whereEqualTo("driverId", driverId).live { it.toAbsence() }

    fun allAbsences(): Flow<List<DriverAbsence>> = db.collection("driverAbsences").live { it.toAbsence() }

    suspend fun requestAbsence(driverId: String, driverName: String, fromDate: String, untilDate: String, reason: String) {
        db.collection("driverAbsences").add(
            mapOf(
                "driverId" to driverId,
                "driverName" to driverName,
                "fromDate" to fromDate,
                "untilDate" to untilDate,
                "reason" to reason,
                "status" to AbsenceStatus.OPEN.raw,
                "createdAt" to System.currentTimeMillis().toTimestamp(),
            )
        ).await()
    }

    suspend fun updateAbsence(id: String, status: AbsenceStatus, coverDriverId: String?) {
        val fields = buildMap<String, Any> {
            put("status", status.raw)
            coverDriverId?.let { put("coverDriverId", it) }
        }
        db.collection("driverAbsences").document(id).update(fields).await()
    }

    suspend fun withdrawAbsence(id: String) {
        db.collection("driverAbsences").document(id).delete().await()
    }

    // ---------- Complaints ----------

    fun myComplaints(uid: String): Flow<List<Complaint>> =
        db.collection("complaints").whereEqualTo("uid", uid).live { it.toComplaint() }

    fun allComplaints(): Flow<List<Complaint>> =
        db.collection("complaints").orderBy("createdAt", Query.Direction.DESCENDING).live { it.toComplaint() }

    suspend fun sendComplaint(
        user: AppUser,
        category: ComplaintCategory,
        busName: String?,
        routeName: String?,
        text: String,
    ) {
        val data = buildMap<String, Any> {
            put("uid", user.id)
            put("userName", user.name)
            put("userRole", user.role.raw)
            put("category", category.raw)
            busName?.takeIf { it.isNotBlank() }?.let { put("busName", it) }
            routeName?.takeIf { it.isNotBlank() }?.let { put("routeName", it) }
            put("text", text.trim().take(Complaint.MAX_TEXT_LENGTH))
            put("createdAt", System.currentTimeMillis().toTimestamp())
            put("status", ComplaintStatus.OPEN.raw)
            put("readByAdmin", false)
        }
        db.collection("complaints").add(data).await()
    }

    suspend fun markComplaintRead(id: String) {
        db.collection("complaints").document(id).update("readByAdmin", true).await()
    }

    suspend fun replyToComplaint(id: String, status: ComplaintStatus, reply: String?) {
        val fields = buildMap<String, Any> {
            put("status", status.raw)
            put("readByAdmin", true)
            if (!reply.isNullOrBlank()) {
                put("adminReply", reply.trim())
                put("repliedAt", System.currentTimeMillis().toTimestamp())
            }
        }
        db.collection("complaints").document(id).update(fields).await()
    }

    // ---------- Announcements ----------

    fun announcements(): Flow<List<Announcement>> =
        db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING).live { it.toAnnouncement() }

    suspend fun postAnnouncement(title: String, body: String, postedBy: String, routeName: String?) {
        val data = buildMap<String, Any> {
            put("title", title.trim())
            put("body", body.trim())
            put("createdAt", System.currentTimeMillis().toTimestamp())
            put("postedBy", postedBy)
            routeName?.takeIf { it.isNotBlank() }?.let { put("routeName", it) }
        }
        db.collection("announcements").add(data).await()
    }

    suspend fun deleteAnnouncement(id: String) {
        db.collection("announcements").document(id).delete().await()
    }
}
