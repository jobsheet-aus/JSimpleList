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
    val displayName: String,

    @SerialName("avatar_icon")
    val avatarIcon: String = "person",

    @SerialName("avatar_colour")
    val avatarColour: String = "blue"
)

@Serializable
private data class ProfileUpsert(
    @SerialName("user_id")
    val userId: String,

    @SerialName("display_name")
    val displayName: String,

    @SerialName("avatar_icon")
    val avatarIcon: String,

    @SerialName("avatar_colour")
    val avatarColour: String
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

    suspend fun loadOrCreateMyProfile(): Profile? {
        val session =
            client.auth.currentSessionOrNull()
                ?: return null

        val existingProfile = loadMyProfile()

        if (existingProfile != null) {
            return existingProfile
        }

        val email =
            session.user?.email
                ?: error("Authenticated email required")

        val localPart =
            email.substringBefore("@").trim()

        val fallbackBase =
            localPart
                .ifEmpty { "user" }
                .take(49)

        return saveMyDisplayName(
            "$fallbackBase@"
        )
    }

    suspend fun loadProfiles(userIds: Set<String>): Map<String, Profile> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        return client
            .from("profiles")
            .select {
                filter {
                    isIn(
                        "user_id",
                        userIds.toList()
                    )
                }
            }
            .decodeList<Profile>()
            .associateBy { profile ->
                profile.userId
            }
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

        val existingProfile = loadMyProfile()

        return saveMyProfile(
            userId = userId,
            displayName = trimmedName,
            avatarIcon =
                existingProfile?.avatarIcon
                    ?: "person",
            avatarColour =
                existingProfile?.avatarColour
                    ?: "blue"
        )
    }

    suspend fun saveMyAvatar(
        avatarIcon: String,
        avatarColour: String
    ): Profile {
        val existingProfile =
            loadMyProfile()
                ?: loadOrCreateMyProfile()
                ?: error("Could not load profile")

        return saveMyProfileIdentity(
            displayName = existingProfile.displayName,
            avatarIcon = avatarIcon,
            avatarColour = avatarColour
        )
    }

    suspend fun saveMyProfileIdentity(
        displayName: String,
        avatarIcon: String,
        avatarColour: String
    ): Profile {
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

        require(avatarIcon in ALLOWED_AVATAR_ICONS) {
            "Unknown avatar icon"
        }

        require(avatarColour in ALLOWED_AVATAR_COLOURS) {
            "Unknown avatar colour"
        }

        return saveMyProfile(
            userId = userId,
            displayName = trimmedName,
            avatarIcon = avatarIcon,
            avatarColour = avatarColour
        )
    }

    private suspend fun saveMyProfile(
        userId: String,
        displayName: String,
        avatarIcon: String,
        avatarColour: String
    ): Profile {
        return client
            .from("profiles")
            .upsert(
                ProfileUpsert(
                    userId = userId,
                    displayName = displayName,
                    avatarIcon = avatarIcon,
                    avatarColour = avatarColour
                )
            ) {
                onConflict = "user_id"
                select()
            }
            .decodeSingle()
    }

    companion object {
        val ALLOWED_AVATAR_ICONS = setOf(
            "person",
            "flower",
            "cat",
            "horse",
            "lightning",
            "coffee",
            "helmet",
            "paw",
            "book",
            "alien",
            "f1car",
            "music",
            "home",
            "heart",
            "star",
            "starfish"
        )

        val ALLOWED_AVATAR_COLOURS = setOf(
            "blue",
            "purple",
            "pink",
            "orange",
            "green",
            "grey",
            "red",
            "brown"
        )
    }
}