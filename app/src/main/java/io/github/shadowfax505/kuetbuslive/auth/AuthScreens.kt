package io.github.shadowfax505.kuetbuslive.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.shadowfax505.kuetbuslive.R
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.UserRole
import io.github.shadowfax505.kuetbuslive.ui.components.AppTextField
import io.github.shadowfax505.kuetbuslive.ui.components.BrandGradient
import io.github.shadowfax505.kuetbuslive.ui.components.EmptyState
import io.github.shadowfax505.kuetbuslive.ui.components.ErrorBanner
import io.github.shadowfax505.kuetbuslive.ui.components.InfoBanner
import io.github.shadowfax505.kuetbuslive.ui.components.PrimaryButton
import io.github.shadowfax505.kuetbuslive.ui.components.SecondaryButton
import io.github.shadowfax505.kuetbuslive.ui.theme.Brand

// The brand header on top, a rounded sheet with the form below it.
@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(BrandGradient)
                    .statusBarsPadding()
                    .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 60.dp),
            ) {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                )
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
            }
            Surface(
                modifier = Modifier.fillMaxWidth().offset(y = (-32).dp),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    Modifier.padding(horizontal = 24.dp, vertical = 28.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    content()
                }
            }
        }
    }
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
    AuthLayout(stringResource(R.string.app_name), stringResource(R.string.tagline)) {
        Text("Welcome back", style = MaterialTheme.typography.headlineSmall)
        AppTextField(email, { email = it }, "Email", leadingIcon = Icons.Rounded.AlternateEmail, keyboardType = KeyboardType.Email)
        AppTextField(password, { password = it }, "Password", leadingIcon = Icons.Rounded.Lock, isPassword = true)
        ErrorBanner(state.error)
        InfoBanner(state.info)
        PrimaryButton("Sign in", onClick = { session.signIn(email, password) }, loading = state.busy)
        TextButton(onClick = { session.sendPasswordReset(email) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Forgot password?")
        }
        Spacer(Modifier.height(4.dp))
        SecondaryButton("New here? Create an account", onClick = onSignUp)
    }
}

@Composable
fun SignUpScreen(session: SessionViewModel, state: SessionState, onSignIn: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var kuetId by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.STUDENT) }

    AuthLayout("Create account", "Sign up with your KUET details. Teachers and drivers are approved by the transport office.") {
        Text("I am a", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.signUpRoles.forEach { option ->
                FilterChip(
                    selected = role == option,
                    onClick = { role = option },
                    label = { Text(option.title) },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        AppTextField(name, { name = it }, "Full name", leadingIcon = Icons.Rounded.Person)
        AppTextField(
            email, { email = it }, "Email",
            leadingIcon = Icons.Rounded.AlternateEmail,
            keyboardType = KeyboardType.Email,
            supportingText = when (role) {
                UserRole.STUDENT -> "Your @stud.kuet.ac.bd email"
                UserRole.TEACHER -> "Your KUET staff email"
                else -> null
            },
        )
        AppTextField(
            kuetId, { kuetId = it }, role.idLabel,
            leadingIcon = Icons.Rounded.Badge,
            keyboardType = if (role == UserRole.STUDENT) KeyboardType.Number else KeyboardType.Text,
        )
        AppTextField(password, { password = it }, "Password", leadingIcon = Icons.Rounded.Lock, isPassword = true)
        ErrorBanner(state.error)
        PrimaryButton("Create account", onClick = { session.signUp(name, email, password, role, kuetId) }, loading = state.busy)
        TextButton(onClick = onSignIn, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("I already have an account")
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
            color = Brand.Unknown,
        )
    }
}
