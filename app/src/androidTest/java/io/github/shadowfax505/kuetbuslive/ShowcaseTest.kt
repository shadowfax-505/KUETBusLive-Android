package io.github.shadowfax505.kuetbuslive

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Walks through the main screens of each role on made-up data and saves a
// screenshot of each, for the README and for showing the app without a phone.
// It needs the Firebase emulators (see .github/workflows/screenshots.yml).
@RunWith(AndroidJUnit4::class)
class ShowcaseTest {
    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val shots by lazy { File(instrumentation.targetContext.filesDir, "shots").apply { mkdirs() } }
    private val uids = mutableMapOf<String, String>()
    @Volatile private var driving = true

    // ---------- Made-up data ----------

    private val sonadanga = listOf(
        stop("KUET Main Gate", 22.8999, 89.5024), stop("Fulbarigate", 22.8890, 89.5095),
        stop("Daulatpur", 22.8660, 89.5170), stop("Notun Rasta", 22.8460, 89.5330),
        stop("Sonadanga Bus Terminal", 22.8170, 89.5440),
    )
    private val royalMore = listOf(
        stop("KUET Main Gate", 22.8999, 89.5024), stop("Fulbarigate", 22.8890, 89.5095),
        stop("Daulatpur", 22.8660, 89.5170), stop("Shibbari More", 22.8150, 89.5590),
        stop("Royal More", 22.8105, 89.5630),
    )

    private fun stop(name: String, lat: Double, lng: Double) = mapOf("name" to name, "lat" to lat, "lng" to lng)

    private fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)

    private fun account(key: String, name: String, role: String, extra: Map<String, Any> = emptyMap()) {
        val email = "$key@test.com"
        val uid = runCatching { await(auth.createUserWithEmailAndPassword(email, PASSWORD)).user!!.uid }
            .getOrElse { await(auth.signInWithEmailAndPassword(email, PASSWORD)).user!!.uid }
        uids[key] = uid
        verifyEmail(email)
        val kuetId = mapOf("student" to "2107045", "driver" to "D-12", "admin" to "S-1")[key] ?: "X-1"
        await(db.collection("users").document(uid).set(
            mapOf("name" to name, "email" to email, "role" to role, "kuetId" to kuetId, "isApproved" to true) + extra
        ))
    }

    // Opens the verification link the Auth emulator "sent", like a rider tapping it in their mail.
    private fun verifyEmail(email: String) {
        val user = auth.currentUser ?: return
        if (user.isEmailVerified) return
        await(user.sendEmailVerification())
        val codes = JSONObject(URL("http://${ShowcaseRunner.HOST}:9099/emulator/v1/projects/demo-kuetbuslive/oobCodes").readText())
            .getJSONArray("oobCodes")
        for (i in codes.length() - 1 downTo 0) {
            val code = codes.getJSONObject(i)
            if (code.optString("email") == email && code.optString("requestType") == "VERIFY_EMAIL") {
                await(auth.applyActionCode(code.getString("oobCode")))
                break
            }
        }
        await(user.reload())
    }

    @Before
    fun seed() {
        account("student", "Nusrat Jahan", "student", mapOf(
            "favoriteStopName" to "Daulatpur", "favoriteStopLat" to 22.8660, "favoriteStopLng" to 89.5170,
        ))
        account("driver", "Abdul Karim", "driver")
        account("admin", "Transport Office", "admin")
        account("pending", "Dr. Rafiq Hasan", "teacher")
        await(db.collection("users").document(uids.getValue("pending")).update("isApproved", false))
        auth.signOut()

        await(db.collection("routes").document("sonadanga").set(mapOf("name" to "KUET to Sonadanga", "stops" to sonadanga)))
        await(db.collection("routes").document("royal").set(mapOf("name" to "KUET to Royal More", "stops" to royalMore)))

        val json = instrumentation.context.assets.open("kuet_schedule.json").bufferedReader().readText()
        val trips = JSONObject(json).getJSONArray("trips")
        val batch = db.batch()
        for (i in 0 until trips.length()) {
            val t = trips.getJSONObject(i)
            val name = t.getString("tripName")
            batch.set(db.collection("schedules").document("trip-$i"), mapOf(
                "tripName" to name,
                "audience" to if (name.startsWith("Special")) "teacher" else "student",
                "days" to t.getString("days"), "period" to t.getString("period"),
                "campusTime" to t.getString("campusTime"), "spot" to t.getString("spot"),
                "spotTime" to t.getString("spotTime"), "remarks" to t.optString("remarks"),
            ))
        }
        await(batch.commit())

        await(db.collection("fleet").document("normal-1").set(mapOf("name" to "Normal-1")))
        // Normal-2 runs every day, Saturdays included, so the driver always has trips to show.
        await(db.collection("fleet").document("normal-2").set(mapOf("name" to "Normal-2", "defaultDriverId" to uids.getValue("driver"))))
        await(db.collection("fleet").document("special-1").set(mapOf("name" to "Special-1")))
        await(db.collection("announcements").document("a1").set(mapOf(
            "title" to "Night trips run 15 minutes later this week",
            "body" to "Because of road works near Notun Rasta, the 9:00 PM trips leave KUET at 9:15 PM until Thursday.",
            "createdAt" to Timestamp(Date(System.currentTimeMillis() - 3_600_000)), "postedBy" to "Transport Office",
        )))

        startBuses()
    }

    // Two buses drive their routes, a position every 2 seconds, like a driver's phone.
    private fun startBuses() {
        thread(isDaemon = true) {
            var step = 0
            while (driving) {
                moveBus("bus-normal-1", "Normal-1", "sonadanga", "KUET to Sonadanga", sonadanga, "student", step, 0.20)
                moveBus("bus-special-1", "Special-1", "royal", "KUET to Royal More", royalMore, "teacher", step, 0.55)
                step++
                Thread.sleep(2_000)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun moveBus(id: String, number: String, routeId: String, routeName: String, stops: List<Map<String, Any>>,
                        audience: String, step: Int, start: Double) {
        val t = (start + step * 0.004).coerceAtMost(0.98) * (stops.size - 1)
        val i = t.toInt().coerceAtMost(stops.size - 2)
        val f = t - i
        val a = stops[i]; val b = stops[i + 1]
        val lat = (a["lat"] as Double) + ((b["lat"] as Double) - (a["lat"] as Double)) * f
        val lng = (a["lng"] as Double) + ((b["lng"] as Double) - (a["lng"] as Double)) * f
        val heading = Math.toDegrees(Math.atan2((b["lng"] as Double) - (a["lng"] as Double), (b["lat"] as Double) - (a["lat"] as Double)))
        runCatching {
            await(db.collection("buses").document(id).set(mapOf(
                "number" to number, "routeId" to routeId, "routeName" to routeName,
                "driverId" to uids.getValue("driver"), "lat" to lat, "lng" to lng,
                "updatedAt" to Timestamp.now(), "crowd" to "unknown", "audience" to audience,
                "heading" to (heading + 360) % 360, "speed" to 7.5, "fleetBusId" to id.removePrefix("bus-"),
            )))
        }
    }

    @After
    fun stop() { driving = false }

    // ---------- Helpers ----------

    // The test drives Compose's clock, so let frames run while waiting (the map draws on its own).
    private fun shot(name: String, settle: Long = 2_500) {
        val end = System.currentTimeMillis() + settle
        while (System.currentTimeMillis() < end) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(100)
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(shots, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitFor(text: String, timeout: Long = 20_000) {
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(text: String) {
        waitFor(text)
        compose.onAllNodes(hasText(text, substring = true)).onFirst().performClick()
    }

    private fun signedInAs(key: String, block: () -> Unit) = part(key) {
        await(auth.signInWithEmailAndPassword("$key@test.com", PASSWORD))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { block() }
        } finally {
            auth.signOut()
        }
    }

    // One part failing still leaves the screenshots of the others.
    private fun part(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.e("Showcase", "Part $name stopped", it) }
    }

    // ---------- The tour ----------

    @Test
    fun tour() {
        // Signing in.
        part("sign-in") {
          ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Welcome back")
            shot("01-sign-in")
            compose.onAllNodes(hasText("Email")).onFirst().performTextInput("student@test.com")
            compose.onAllNodes(hasText("Password")).onFirst().performTextInput(PASSWORD)
            tap("Sign in")
            waitFor("Schedule")
            auth.signOut()
          }
        }

        // A student.
        signedInAs("student") {
            waitFor("Normal-1")
            shot("02-live-map", settle = 10_000)
            tap("Schedule")
            shot("06-schedule")
            tap("Routes")
            shot("07-routes")
            tap("News")
            shot("08-news")
            tap("Profile")
            shot("09-profile")
            tap("Map")
            tap("Normal-1")
            waitFor("Where to?")
            shot("03-bus-selected", settle = 6_000)
            tap("Where to?")
            tap("Sonadanga Bus Terminal")
            waitFor("Get off at")
            shot("04-best-place-to-get-off", settle = 4_000)
            tap("Compare")
            waitFor("fastest first")
            shot("05-compare-buses", settle = 4_000)
        }

        // The driver.
        signedInAs("driver") {
            waitFor("Start trip")
            shot("10-driver-home", settle = 5_000)
        }

        // The transport office.
        signedInAs("admin") {
            waitFor("Approvals")
            shot("11-admin-map", settle = 8_000)
            tap("Approvals")
            shot("12-admin-approvals")
            tap("More")
            shot("13-admin-more")
        }
    }

    companion object {
        const val PASSWORD = "test123"
    }
}
