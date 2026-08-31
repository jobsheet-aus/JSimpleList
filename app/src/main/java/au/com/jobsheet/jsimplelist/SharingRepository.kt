package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SharedListMember(
    val userId: String,
    val displayName: String,
    val role: String
)

data class SharedListInfo(
    val members: List<SharedListMember>,
    val pendingInvitations: List<PendingInvitation>
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

        val displayNames =
            profileRepository.loadProfiles(
                memberRows
                    .map { it.userId }
                    .toSet()
            )

        val members =
            memberRows
                .map { member ->
                    SharedListMember(
                        userId = member.userId,
                        displayName =
                            displayNames[member.userId]
                                ?: "Unknown member",
                        role = member.role
                    )
                }
                .sortedWith(
                    compareBy<SharedListMember> {
                        if (it.role == "owner") 0 else 1
                    }.thenBy {
                        it.displayName.lowercase()
                    }
                )

        val currentUserIsOwner =
            memberRows.any { member ->
                member.userId == currentUserId &&
                    member.role == "owner"
            }

        val pendingInvitations =
            if (currentUserIsOwner) {
                client
                    .from("list_invitations")
                    .select {
                        filter {
                            eq("list_id", listId)
                        }
                    }
                    .decodeList<PendingInvitation>()
                    .filter { invitation ->
                        invitation.acceptedAt == null &&
                            invitation.cancelledAt == null
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
