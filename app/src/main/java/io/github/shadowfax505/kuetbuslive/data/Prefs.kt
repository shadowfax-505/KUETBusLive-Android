package io.github.shadowfax505.kuetbuslive.data

import android.content.Context
import android.content.SharedPreferences
import io.github.shadowfax505.kuetbuslive.model.RunningStat
import io.github.shadowfax505.kuetbuslive.model.WalkingSpeed

// Settings kept only on this phone, like on iOS: arrival alerts, read news,
// the bus this rider is waving at, and how fast they walk.
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

    // How fast this rider walks, learned on this phone and never uploaded.
    var walkingSpeed: WalkingSpeed
        get() = WalkingSpeed(
            RunningStat(
                prefs.getInt("walkingN", 0),
                prefs.getFloat("walkingMean", 0f).toDouble(),
                prefs.getFloat("walkingVariance", 0f).toDouble(),
            )
        )
        set(value) = prefs.edit()
            .putInt("walkingN", value.stat.n)
            .putFloat("walkingMean", value.stat.mean.toFloat())
            .putFloat("walkingVariance", value.stat.variance.toFloat())
            .apply()

    fun clear() = prefs.edit().clear().apply()
}
