package io.github.shadowfax505.kuetbuslive.data

import android.content.Context
import android.content.SharedPreferences

// Settings kept only on this phone, like on iOS: arrival alerts, read news,
// and the bus this rider is waving at.
class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kuet_bus_live", Context.MODE_PRIVATE)

    var arrivalAlerts: Boolean
        get() = prefs.getBoolean("arrivalAlertOn", true)
        set(value) = prefs.edit().putBoolean("arrivalAlertOn", value).apply()

    var showNews: Boolean
        get() = prefs.getBoolean("showNews", true)
        set(value) = prefs.edit().putBoolean("showNews", value).apply()

    var readAnnouncements: Set<String>
        get() = prefs.getStringSet("readAnnouncements", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("readAnnouncements", value).apply()

    var wavingAtBusId: String?
        get() = prefs.getString("wavingAtBusId", null)?.takeIf { prefs.getLong("wavingUntil", 0) > System.currentTimeMillis() }
        set(value) = prefs.edit().putString("wavingAtBusId", value).apply()

    var wavingUntil: Long
        get() = prefs.getLong("wavingUntil", 0)
        set(value) = prefs.edit().putLong("wavingUntil", value).apply()

    fun clear() = prefs.edit().clear().apply()
}
