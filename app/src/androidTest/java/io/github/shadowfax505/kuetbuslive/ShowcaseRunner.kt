package io.github.shadowfax505.kuetbuslive

import android.app.Application
import androidx.test.runner.AndroidJUnitRunner
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Points the app at the local Firebase emulators (Auth and Firestore on the build
// machine, reached from the Android emulator as 10.0.2.2) before the app starts,
// so the showcase runs on made-up data and never touches the real project.
class ShowcaseRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        if (FirebaseApp.getApps(app).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId("demo-kuetbuslive")
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("emulator-only-key")
                .build()
            FirebaseApp.initializeApp(app, options)
            FirebaseFirestore.getInstance().useEmulator(HOST, 8080)
            FirebaseAuth.getInstance().useEmulator(HOST, 9099)
        }
        super.callApplicationOnCreate(app)
    }

    companion object {
        const val HOST = "10.0.2.2"
    }
}
