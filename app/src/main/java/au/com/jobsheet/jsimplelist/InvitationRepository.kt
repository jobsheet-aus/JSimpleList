package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
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

    @SerialName("invited_email")
    val invitedEmail: String,

    @SerialName("invited_by")
    val invitedBy: String,

    val role: String,

    @SerialName("accepted_at")
    val acceptedAt: String? = null,

    @SerialName("cancelled_at")
    val cancelledAt: String? = null
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
        val email =
            client.auth.currentSessionOrNull()
                ?.user
                ?.email
                ?.trim()
                ?.lowercase()
                ?: return emptyList()

        return client
            .from("list_invitations")
            .select()
            .decodeList<PendingInvitation>()
            .filter { invitation ->
                invitation.acceptedAt == null &&
                    invitation.cancelledAt == null &&
                    invitation.invitedEmail.trim().lowercase() == email
            }
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
}
