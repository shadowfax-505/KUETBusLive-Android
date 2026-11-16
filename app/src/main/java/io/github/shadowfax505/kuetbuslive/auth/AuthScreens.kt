package io.github.shadowfax505.kuetbuslive.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.ui.components.AppIcon
import io.github.shadowfax505.kuetbuslive.ui.components.BackControl
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.ErrorBanner
import io.github.shadowfax505.kuetbuslive.ui.components.FormField
import io.github.shadowfax505.kuetbuslive.ui.components.InfoBanner
import io.github.shadowfax505.kuetbuslive.ui.components.PrimaryButton
import io.github.shadowfax505.kuetbuslive.ui.components.SecondaryButton
import io.github.shadowfax505.kuetbuslive.ui.components.Section
import io.github.shadowfax505.kuetbuslive.ui.components.SectionTitle
import io.github.shadowfax505.kuetbuslive.ui.components.SegmentedControl
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

// A dotted route drawn faintly across the top of the sign-in screen, with a bus dot
// gliding along it, so the first screen already hints at what the app does.
@Composable
private fun RouteBackdrop(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val stopFill = MaterialTheme.colorScheme.background
    val stopRing = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val bus = Brand.Seats
    val progress by rememberInfiniteTransition(label = "route").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "bus",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(-w * 0.05f, h * 0.78f)
            cubicTo(w * 0.22f, h * 0.98f, w * 0.30f, h * 0.30f, w * 0.55f, h * 0.42f)
            cubicTo(w * 0.78f, h * 0.53f, w * 0.80f, h * 0.10f, w * 1.05f, h * 0.18f)
        }
        val stroke = 2.dp.toPx()
        drawPath(path, line, style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 9.dp.toPx()))))
        val measure = PathMeasure().apply { setPath(path, false) }
        listOf(0.2f, 0.5f, 0.8f).forEach { at ->
            val p = measure.getPosition(measure.length * at)
            drawCircle(stopFill, 5.dp.toPx(), p)
            drawCircle(stopRing, 5.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        }
        val p = measure.getPosition(measure.length * progress)
        drawCircle(bus.copy(alpha = 0.18f), 14.dp.toPx(), p)
        drawCircle(Color.White, 7.dp.toPx(), p)
        drawCircle(bus, 5.dp.toPx(), p)
    }
}

// The app icon springs in, then the title, as on iOS.
@Composable
private fun AuthHeader(title: String, subtitle: String) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, size = 96.dp)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun AuthColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        content = content,
    )
}

@Composable
fun SignedOutFlow(session: SessionViewModel, state: SessionState) {
    var showSignUp by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(
        targetState = showSignUp,
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally { it * direction / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it * direction / 4 } + fadeOut())
        },
        label = "auth",
    ) { signUp ->
        if (signUp) {
            SignUpScreen(session, state, onSignIn = { session.clearMessages(); showSignUp = false })
        } else {
            LoginScreen(session, state, onSignUp = { session.clearMessages(); showSignUp = true })
        }
    }
}

@Composable
fun LoginScreen(session: SessionViewModel, state: SessionState, onSignUp: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RouteBackdrop(Modifier.fillMaxWidth().height(300.dp))
        AuthColumn {
            Spacer(Modifier.height(72.dp))
            AuthHeader(stringResource(R.string.app_name), "Your campus bus, live on the map.")
            Spacer(Modifier.height(36.dp))
            Section {
                FormField(email, { email = it.trim() }, "Email", keyboardType = KeyboardType.Email)
                FormField(password, { password = it }, "Password", isPassword = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ErrorBanner(state.error)
                InfoBanner(state.info)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Sign In", onClick = { session.signIn(email, password) }, loading = state.busy, enabled = email.isNotBlank() && password.isNotBlank())
            TextButton(onClick = { session.sendPasswordReset(email) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)) {
                Text("Forgot password?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 32.dp))
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("New here?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSignUp, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Create an account", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun SignUpScreen(session: SessionViewModel, state: SessionState, onSignIn: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var kuetId by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.STUDENT) }
    val roles = UserRole.signUpRoles

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AuthColumn {
            Box(Modifier.fillMaxWidth().height(44.dp).offset(x = (-16).dp)) {
                BackControl(onSignIn, Modifier.align(Alignment.CenterStart), label = "Sign In")
            }
            Text("Create Account", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            Text(
                "Use your KUET details. The transport office approves teacher and driver accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
            )
            SectionTitle("I am a")
            SegmentedControl(roles.map { it.title }, roles.indexOf(role), { role = roles[it] })
            Spacer(Modifier.height(10.dp))
            Section(
                header = "Your details",
                footer = when (role) {
                    UserRole.STUDENT -> "Use your @stud.kuet.ac.bd email. We send a link to check it."
                    UserRole.TEACHER -> "Use your KUET staff email. We send a link to check it."
                    else -> "Drivers can sign up with any email."
                },
            ) {
                FormField(name, { name = it }, "Full name")
                FormField(email, { email = it.trim() }, "Email", keyboardType = KeyboardType.Email)
                FormField(kuetId, { kuetId = it }, role.idLabel, keyboardType = if (role == UserRole.STUDENT) KeyboardType.Number else KeyboardType.Text)
                FormField(password, { password = it }, "Password (6 or more characters)", isPassword = true)
            }
            ErrorBanner(state.error)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Create Account", onClick = { session.signUp(name, email, password, role, kuetId) }, loading = state.busy)
            TextButton(onClick = onSignIn, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                Text("I already have an account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
fun VerifyEmailScreen(session: SessionViewModel, state: SessionState, email: String) {
    CenteredMessage {
        EmptyState(
            Icons.Rounded.MarkEmailRead,
            "Check your KUET email",
            "We sent a verification link to $email. Open it, then come back and tap the button below.",
        )
        ErrorBanner(state.error)
        InfoBanner(state.info)
        PrimaryButton("I've opened the link", onClick = { session.refreshVerification() }, loading = state.busy)
        SecondaryButton("Send the link again", onClick = { session.resendVerification() })
        TextButton(onClick = { session.signOut() }) { Text("Sign out") }
    }
}

@Composable
fun WaitingForApprovalScreen(session: SessionViewModel, user: AppUser) {
    CenteredMessage {
        EmptyState(
            Icons.Rounded.HourglassTop,
            "Waiting for approval",
            "The KUET transport office checks every ${user.role.title.lowercase()} account. " +
                "This screen opens the app by itself as soon as you're approved.",
        )
        Text(
            "${user.name} · ${user.role.idLabel} ${user.kuetId.orEmpty()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { session.signOut() }) { Text("Sign out") }
    }
}

@Composable
fun InactiveScreen(session: SessionViewModel) {
    CenteredMessage {
        EmptyState(
            Icons.Rounded.PersonOff,
            "Your account is no longer active",
            "Contact the KUET transport office if you think this is a mistake.",
        )
        TextButton(onClick = { session.signOut() }) { Text("Sign out") }
    }
}

@Composable
fun SetupNeededScreen() {
    CenteredMessage {
        Text(stringResource(R.string.setup_needed_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.setup_needed_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
