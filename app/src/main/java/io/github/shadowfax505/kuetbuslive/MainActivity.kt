package io.github.shadowfax505.kuetbuslive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shadowfax505.kuetbuslive.auth.InactiveScreen
import io.github.shadowfax505.kuetbuslive.auth.Screen
import io.github.shadowfax505.kuetbuslive.auth.SessionViewModel
import io.github.shadowfax505.kuetbuslive.auth.SetupNeededScreen
import io.github.shadowfax505.kuetbuslive.auth.SignedOutFlow
import io.github.shadowfax505.kuetbuslive.auth.VerifyEmailScreen
import io.github.shadowfax505.kuetbuslive.auth.WaitingForApprovalScreen
import io.github.shadowfax505.kuetbuslive.ui.HomeRouter
import io.github.shadowfax505.kuetbuslive.ui.components.IntroAnimation
import io.github.shadowfax505.kuetbuslive.ui.components.LoadingScreen
import io.github.shadowfax505.kuetbuslive.ui.theme.KuetBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuetBusTheme {
                // The intro plays once per launch, over the first screen as it loads.
                var showIntro by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                Box(Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (KuetBusApp.firebaseReady) AppRoot() else SetupNeededScreen()
                    }
                    if (showIntro) IntroAnimation { showIntro = false }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(session: SessionViewModel = viewModel()) {
    val state by session.state.collectAsStateWithLifecycle()
    val screen = session.screen(state)
    // Screen changes (signed in, approved, verified) fade into each other.
    AnimatedContent(
        targetState = screen,
        contentKey = { it::class },
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith fadeOut() },
        label = "root",
    ) { current ->
        when (current) {
            Screen.SignedOut -> SignedOutFlow(session, state)
            Screen.Loading -> LoadingScreen()
            is Screen.VerifyEmail -> VerifyEmailScreen(session, state, current.email)
            Screen.Inactive -> InactiveScreen(session)
            is Screen.WaitingForApproval -> WaitingForApprovalScreen(session, current.user)
            is Screen.Home -> HomeRouter(session, current.user)
        }
    }
}
