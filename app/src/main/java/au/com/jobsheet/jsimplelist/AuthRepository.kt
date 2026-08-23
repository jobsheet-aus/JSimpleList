package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP

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

    fun currentUserId(): String? {
        return client.auth.currentSessionOrNull()?.user?.id
    }

    fun currentUserEmail(): String? {
        return client.auth.currentSessionOrNull()?.user?.email
    }

    suspend fun signOut() {
        client.auth.signOut()
    }
}