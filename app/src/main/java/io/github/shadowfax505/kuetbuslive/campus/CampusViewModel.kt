package io.github.shadowfax505.kuetbuslive.campus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.shadowfax505.kuetbuslive.data.Prefs
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.model.Announcement
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.BusRoute
import io.github.shadowfax505.kuetbuslive.model.BusSchedule
import io.github.shadowfax505.kuetbuslive.model.Complaint
import io.github.shadowfax505.kuetbuslive.model.ComplaintCategory
import io.github.shadowfax505.kuetbuslive.model.ComplaintStatus
import io.github.shadowfax505.kuetbuslive.model.Stop
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Timetable, routes, news and complaints: the data every role reads, in one place.
@OptIn(ExperimentalCoroutinesApi::class)
class CampusViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = Prefs(application)
    private val userId = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val schedules: StateFlow<List<BusSchedule>> = Repository.schedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routes: StateFlow<List<BusRoute>> = Repository.routes()
        .map { list -> list.sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val announcements: StateFlow<List<Announcement>> = Repository.announcements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myComplaints: StateFlow<List<Complaint>> = userId
        .flatMapLatest { uid -> if (uid == null) flowOf(emptyList()) else Repository.myComplaints(uid) }
        .map { list -> list.sortedByDescending { it.createdAtMillis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _readNews = MutableStateFlow(prefs.readAnnouncements)
    val readNews: StateFlow<Set<String>> = _readNews.asStateFlow()

    fun setUser(uid: String) {
        userId.value = uid
    }

    fun clearMessage() {
        _message.value = null
    }

    fun markNewsRead(ids: Collection<String>) {
        val updated = _readNews.value + ids
        if (updated != _readNews.value) {
            prefs.readAnnouncements = updated
            _readNews.value = updated
        }
    }

    fun saveFavoriteStop(user: AppUser, stop: Stop) = launch("Boarding at ${stop.name}.") {
        Repository.saveFavoriteStop(user.id, stop.name, stop.lat, stop.lng)
    }

    fun saveName(user: AppUser, name: String) = launch("Name saved.") {
        require(name.isNotBlank() && name.length <= 100) { "Enter a name up to 100 characters." }
        Repository.updateName(user.id, name.trim())
    }

    fun sendComplaint(user: AppUser, category: ComplaintCategory, busName: String?, routeName: String?, text: String, done: () -> Unit) =
        launch("Sent. The transport office will reply here.", done) {
            require(text.isNotBlank()) { "Write what happened first." }
            Repository.sendComplaint(user, category, busName, routeName, text)
        }

    // ---------- Admin ----------

    fun saveSchedule(schedule: BusSchedule, done: () -> Unit) = launch("Trip saved.", done) {
        require(schedule.tripName.isNotBlank()) { "Enter the trip name, like Normal-1." }
        require(schedule.spot.isNotBlank()) { "Enter the starting spot." }
        Repository.saveSchedule(schedule)
    }

    fun deleteSchedule(id: String) = launch("Trip deleted.") { Repository.deleteSchedule(id) }

    fun postAnnouncement(user: AppUser, title: String, body: String, routeName: String?, done: () -> Unit) =
        launch("Posted to every rider.", done) {
            require(title.isNotBlank() && body.isNotBlank()) { "Write a title and a message." }
            Repository.postAnnouncement(title, body, user.name, routeName)
        }

    fun deleteAnnouncement(id: String) = launch("Deleted.") { Repository.deleteAnnouncement(id) }

    fun replyToComplaint(id: String, status: ComplaintStatus, reply: String?, done: () -> Unit) =
        launch("Saved.", done) { Repository.replyToComplaint(id, status, reply) }

    fun markComplaintRead(id: String) = launch(null) { Repository.markComplaintRead(id) }

    private fun launch(success: String?, done: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { success?.let { _message.value = it }; done() }
                .onFailure { _message.value = it.message ?: "Something went wrong." }
        }
    }
}
