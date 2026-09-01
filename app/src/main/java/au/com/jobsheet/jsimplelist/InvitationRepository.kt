package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PendingInvitation(
    val id: String,

    @SerialName("list_id")
    val listId: String,

    @SerialName("invited_by")
    val invitedBy: String,

    val role: String,

    @SerialName("list_name")
    val listName: String,

    @SerialName("list_kind")
    val listKind: String,

    @SerialName("inviter_display_name")
    val inviterDisplayName: String
)

class InvitationRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    suspend fun sendInvitation(
        listId: String,
        email: String
    ) {
        client.functions.invoke(
            function = "invite-list",
            body = buildJsonObject {
                put("listId", listId)
                put("email", email.trim())
            }
        )
    }

    suspend fun loadPendingInvitations(): List<PendingInvitation> {
        return client.postgrest
            .rpc(
                function = "get_my_pending_invitations"
            )
            .decodeList<PendingInvitation>()
    }

    suspend fun acceptInvitation(
        invitationId: String
    ): String {
        return client.postgrest
            .rpc(
                function = "accept_list_invitation",
                parameters = buildJsonObject {
                    put("target_invitation_id", invitationId)
                }
            )
            .decodeAs<String>()
    }

    suspend fun declineInvitation(
        invitationId: String
    ): String {
        return client.postgrest
            .rpc(
                function = "decline_list_invitation",
                parameters = buildJsonObject {
                    put("target_invitation_id", invitationId)
                }
            )
            .decodeAs<String>()
    }
}
