package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    @SerialName("user_id")
    val userId: String,

    @SerialName("display_name")
    val displayName: String
)

@Serializable
private data class ProfileInsert(
    @SerialName("user_id")
    val userId: String,

    @SerialName("display_name")
    val displayName: String
)

class ProfileRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    suspend fun loadMyProfile(): Profile? {
        val userId =
            client.auth.currentSessionOrNull()?.user?.id
                ?: return null

        return client
            .from("profiles")
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeSingleOrNull<Profile>()
    }

    suspend fun saveMyDisplayName(displayName: String): Profile {
        val userId =
            client.auth.currentSessionOrNull()?.user?.id
                ?: error("Not signed in")

        val trimmedName = displayName.trim()

        require(trimmedName.isNotEmpty()) {
            "Display name cannot be empty"
        }

        require(trimmedName.length <= 50) {
            "Display name cannot be longer than 50 characters"
        }

        return client
            .from("profiles")
            .upsert(
                ProfileInsert(
                    userId = userId,
                    displayName = trimmedName
                )
            ) {
                onConflict = "user_id"
                select()
            }
            .decodeSingle()
    }
}