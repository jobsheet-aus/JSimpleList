package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
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
        return client.auth.currentSessionOrNull()?.user?.id
    }

    fun currentUserEmail(): String? {
        return client.auth.currentSessionOrNull()?.user?.email
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