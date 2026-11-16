package io.github.shadowfax505.kuetbuslive.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.FirebaseNetworkException
import io.github.shadowfax505.kuetbuslive.BuildConfig
import io.github.shadowfax505.kuetbuslive.data.Repository
import io.github.shadowfax505.kuetbuslive.model.AppUser
import io.github.shadowfax505.kuetbuslive.model.KuetVerifier
import io.github.shadowfax505.kuetbuslive.model.UserRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Which screen the app shows, worked out from the login and the profile,
// in the same order as the iOS ContentView.
sealed interface Screen {
    data object SignedOut : Screen
    data object Loading : Screen
    data class VerifyEmail(val email: String) : Screen
    data object Inactive : Screen
    data class WaitingForApproval(val user: AppUser) : Screen
    data class Home(val user: AppUser) : Screen
}

data class SessionState(
    val uid: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val profile: AppUser? = null,
    val profileLoaded: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    val verifier = KuetVerifier(allowDemoAccounts = BuildConfig.DEMO_ACCOUNTS)

    // Starts from the saved login, so a signed-in person doesn't see the sign-in screen
    // flash at launch (and the widget and push topics aren't cleared by it).
    private val _state = MutableStateFlow(
        auth.currentUser.let { SessionState(uid = it?.uid, email = it?.email, emailVerified = it?.isEmailVerified ?: false) }
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var profileJob: Job? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user?.uid != _state.value.uid) {
            _state.update {
                SessionState(uid = user?.uid, email = user?.email, emailVerified = user?.isEmailVerified ?: false)
            }
            listenToProfile(user?.uid)
        }
    }

    init {
        listenToProfile(_state.value.uid)
        auth.addAuthStateListener(authListener)
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
    }

    // A live profile, so an admin's approval shows up without signing in again.
    private fun listenToProfile(uid: String?) {
        profileJob?.cancel()
        if (uid == null) return
        profileJob = viewModelScope.launch {
            Repository.profile(uid).collect { profile ->
                _state.update { it.copy(profile = profile, profileLoaded = true) }
            }
        }
    }

    fun screen(state: SessionState): Screen {
        if (state.uid == null) return Screen.SignedOut
        val profile = state.profile ?: return Screen.Loading
        // A turned-off account is told so first, as on iOS, even before the email link.
        if (!profile.active) return Screen.Inactive
        if (verifier.needsEmailVerification(profile.role, profile.email) && !state.emailVerified) {
            return Screen.VerifyEmail(profile.email)
        }
        if (profile.needsApproval && !profile.isApproved) return Screen.WaitingForApproval(profile)
        return Screen.Home(profile)
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Enter your email and password.") }
            return
        }
        run {
            auth.signInWithEmailAndPassword(email.trim().lowercase(), password).await()
        }
    }

    fun signUp(name: String, email: String, password: String, role: UserRole, kuetId: String) {
        val mail = email.trim().lowercase()
        val id = kuetId.trim()
        val problem = when {
            name.isBlank() || name.trim().length > 100 -> "Enter your name (up to 100 characters)."
            password.length < 6 -> "Use a password of at least 6 characters."
            else -> verifier.validate(role, mail, id)
        }
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        run {
            val result = auth.createUserWithEmailAndPassword(mail, password).await()
            val user = result.user ?: return@run
            // The profile document must exist before the screen can be chosen. If the
            // server refuses it, the login is removed again, so the email can be used for
            // a fresh try instead of leaving an account stuck on the loading screen.
            try {
                Repository.createProfile(user.uid, name.trim(), mail, role, id)
            } catch (e: Exception) {
                runCatching { user.delete().await() }
                auth.signOut()
                throw e
            }
            if (verifier.needsEmailVerification(role, mail)) {
                user.sendEmailVerification().await()
            }
        }
    }

    fun signOut() {
        profileJob?.cancel()
        auth.signOut()
    }

    // Firebase only learns that the link was opened after the user is reloaded.
    fun refreshVerification() {
        run {
            val user = auth.currentUser ?: return@run
            user.reload().await()
            val verified = auth.currentUser?.isEmailVerified ?: false
            // The rules read "email verified" from the sign-in token, and the old token
            // still says no for up to an hour, so fetch a fresh one first.
            if (verified) auth.currentUser?.getIdToken(true)?.await()
            _state.update {
                it.copy(
                    emailVerified = verified,
                    info = if (verified) null else "Not verified yet. Open the link in your KUET email first.",
                )
            }
        }
    }

    fun resendVerification() {
        run {
            val user = auth.currentUser ?: return@run
            user.sendEmailVerification().await()
            _state.update { it.copy(info = "Verification email sent to ${user.email}.") }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _state.update { it.copy(error = "Enter your email first, then tap Forgot password.") }
            return
        }
        run {
            auth.sendPasswordResetEmail(email.trim().lowercase()).await()
            _state.update { it.copy(info = "Password reset link sent to ${email.trim()}.") }
        }
    }

    // Removes the account for good (Google Play requires it in the app), like iOS:
    // 1. The last admin can't leave, or nobody could approve teachers and drivers.
    // 2. Checks the password again, because Firebase only deletes a recent sign-in.
    // 3. Ends a driver's trip, then deletes their bus, places, complaints and profile.
    // 4. Deletes the login, then forgets what this phone saved.
    // `done` gets null when finished, or a message to show.
    fun deleteAccount(password: String, wavingAtBusId: String?, beforeDeleting: () -> Unit, clearPhone: () -> Unit, done: (String?) -> Unit) {
        val user = auth.currentUser
        val email = user?.email
        val profile = _state.value.profile
        if (user == null || email == null || profile == null) {
            done("You are not signed in.")
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val problem = try {
                AccountDeletion.blockReason(profile.role, Repository.adminCount().takeIf { profile.role == UserRole.ADMIN })
                    ?: run {
                        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
                        beforeDeleting()
                        profileJob?.cancel()
                        Repository.deleteAccountData(user.uid, wavingAtBusId)
                        user.delete().await()
                        clearPhone()
                        null
                    }
            } catch (e: Exception) {
                listenToProfile(user.uid)
                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "That password is not right. Nothing was deleted."
                    is FirebaseNetworkException -> "No internet connection. Try again when you're online."
                    else -> "Could not delete the account: ${e.localizedMessage ?: "unknown error"}"
                }
            }
            _state.update { it.copy(busy = false) }
            done(problem)
        }
    }

    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = friendly(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun friendly(e: Exception): String = when (e) {
        is FirebaseNetworkException -> "No internet connection. Try again when you're online."
        is FirebaseAuthWeakPasswordException -> "Use a stronger password (at least 6 characters)."
        is FirebaseAuthUserCollisionException -> "An account with this email already exists. Sign in instead."
        is FirebaseAuthInvalidUserException -> "No account found with this email."
        is FirebaseAuthInvalidCredentialsException -> "The email or password is not right."
        is FirebaseAuthException -> e.localizedMessage ?: "Sign-in failed."
        else -> e.localizedMessage ?: "Something went wrong."
    }
}
