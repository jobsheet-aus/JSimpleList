package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AuthState(
    val userId: String? = null,
    val email: String? = null,
    val initialized: Boolean = false
) {
    val isSignedIn: Boolean
        get() = userId != null
}

class AuthRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    val authState: Flow<AuthState> =
        client.auth.sessionStatus.map { status ->
            if (status is SessionStatus.Initializing) {
                AuthState()
            } else {
                currentAuthState(initialized = true)
            }
        }

    fun currentAuthState(
        initialized: Boolean = true
    ): AuthState {
        val session = client.auth.currentSessionOrNull()

        return AuthState(
            userId = session?.user?.id,
            email = session?.user?.email,
            initialized = initialized
        )
    }

    suspend fun requestEmailOtp(email: String) {
        client.auth.signInWith(OTP) {
            this.email = email.trim()
        }
    }

    suspend fun verifyEmailOtp(
        email: String,
        code: String
    ) {
        client.auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = email.trim(),
            token = code.trim()
        )
    }

    suspend fun awaitSessionInitialization() {
        client.auth.sessionStatus.first { status ->
            status !is SessionStatus.Initializing
        }
    }

    fun currentUserId(): String? {
        return currentAuthState().userId
    }

    fun currentUserEmail(): String? {
        return currentAuthState().email
    }

    suspend fun deleteOnlineAccount() {
        client.functions.invoke("delete-account")
    }

    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (error: Exception) {
            client.auth.clearSession()
        }
    }
}