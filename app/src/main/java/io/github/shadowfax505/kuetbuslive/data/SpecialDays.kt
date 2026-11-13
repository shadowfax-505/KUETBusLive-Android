package io.github.shadowfax505.kuetbuslive.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import io.github.shadowfax505.kuetbuslive.model.SpecialDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The special-days calendar (specialDays/{yyyy-MM-dd}), kept live for the travel
// model: trips on exam days, holidays and events are learned apart, as on iOS.
object SpecialDays {
    private val _days = MutableStateFlow<List<SpecialDay>>(emptyList())
    val days: StateFlow<List<SpecialDay>> = _days.asStateFlow()
    private var listener: ListenerRegistration? = null

    fun start() {
        if (listener != null) return
        listener = FirebaseFirestore.getInstance().collection("specialDays").addSnapshotListener { snapshot, _ ->
            _days.value = snapshot?.documents?.mapNotNull { d ->
                d.getString("kind")?.let { SpecialDay(d.id, it, d.getString("note") ?: "") }
            }?.sortedBy { it.id } ?: _days.value
        }
    }

    // nil on an ordinary day.
    fun kindOn(millis: Long): String? = SpecialDay.kindOn(_days.value, millis)
}
