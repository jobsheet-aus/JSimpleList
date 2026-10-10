package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SharedListMember(
    val userId: String,
    val displayName: String,
    val avatarIcon: String,
    val avatarColour: String,
    val role: String,
    val email: String? = null
)

data class SentInvitationSummary(
    val id: String,
    val invitedEmail: String
)

data class SharedListInfo(
    val members: List<SharedListMember>,
    val pendingInvitations: List<SentInvitationSummary>
)

@Serializable
private data class SharedListMemberRow(
    @SerialName("list_id")
    val listId: String,

    @SerialName("user_id")
    val userId: String,

    val role: String,

    @SerialName("removed_at")
    val removedAt: String? = null
)

@Serializable
private data class SentInvitationRow(
    val id: String,

    @SerialName("invited_email")
    val invitedEmail: String,

    @SerialName("accepted_at")
    val acceptedAt: String? = null,

    @SerialName("cancelled_at")
    val cancelledAt: String? = null
)

@Serializable
private data class OwnerMemberEmailRow(
    @SerialName("user_id")
    val userId: String,
    val email: String
)

class SharingRepository(
    private val profileRepository: ProfileRepository,
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    suspend fun loadSharedListInfo(
        listId: String
    ): SharedListInfo {
        val currentUserId =
            client.auth.currentSessionOrNull()?.user?.id
                ?: error("Not signed in")

        val memberRows =
            client
                .from("list_members")
                .select {
                    filter {
                        eq("list_id", listId)
                    }
                }
                .decodeList<SharedListMemberRow>()
                .filter { member ->
                    member.removedAt == null
                }

        val profiles =
            profileRepository.loadProfiles(
                memberRows
                    .map { it.userId }
                    .toSet()
            )

        val currentUserIsOwner =
            memberRows.any { member ->
                member.userId == currentUserId &&
                    member.role == "owner"
            }

        // Only list owners may request verified emails from auth.users.
        // The server RPC independently enforces this restriction.
        val ownerMemberEmails =
            if (currentUserIsOwner) {
                client.postgrest
                    .rpc(
                        function = "get_owner_list_member_emails",
                        parameters = buildJsonObject {
                            put("target_list_id", listId)
                        }
                    )
                    .decodeList<OwnerMemberEmailRow>()
                    .associate { it.userId to it.email }
            } else {
                emptyMap()
            }

        val members =
            memberRows
                .map { member ->
                    val profile = profiles[member.userId]

                    SharedListMember(
                        userId = member.userId,
                        displayName =
                            profile?.displayName
                                ?: "Unknown member",
                        avatarIcon =
                            profile?.avatarIcon
                                ?: "person",
                        avatarColour =
                            profile?.avatarColour
                                ?: "blue",
                        role = member.role,
                        email = ownerMemberEmails[member.userId]
                    )
                }
                .sortedWith(
                    compareBy<SharedListMember> {
                        if (it.role == "owner") 0 else 1
                    }.thenBy {
                        it.displayName.lowercase()
                    }
                )

        val pendingInvitations =
            if (currentUserIsOwner) {
                client
                    .from("list_invitations")
                    .select {
                        filter {
                            eq("list_id", listId)
                        }
                    }
                    .decodeList<SentInvitationRow>()
                    .filter { invitation ->
                        invitation.acceptedAt == null &&
                            invitation.cancelledAt == null
                    }
                    .map { invitation ->
                        SentInvitationSummary(
                            id = invitation.id,
                            invitedEmail = invitation.invitedEmail
                        )
                    }
            } else {
                emptyList()
            }

        return SharedListInfo(
            members = members,
            pendingInvitations = pendingInvitations
        )
    }
}
