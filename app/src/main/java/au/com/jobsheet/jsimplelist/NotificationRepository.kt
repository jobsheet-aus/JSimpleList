package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class AppNotification(
    val id: String,

    @SerialName("event_type")
    val eventType: String,

    @SerialName("list_id")
    val listId: String? = null,

    @SerialName("actor_user_id")
    val actorUserId: String? = null,

    @SerialName("list_name")
    val listName: String,

    @SerialName("actor_display_name")
    val actorDisplayName: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("seen_at")
    val seenAt: String? = null
)

class NotificationRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    suspend fun loadUnseenNotifications(): List<AppNotification> {
        return client
            .from("notifications")
            .select()
            .decodeList<AppNotification>()
            .filter { notification ->
                notification.seenAt == null
            }
            .sortedBy { notification ->
                notification.createdAt
            }
    }

    suspend fun markSeen(
        notificationId: String
    ) {
        client.postgrest.rpc(
            function = "mark_notification_seen",
            parameters = buildJsonObject {
                put(
                    "target_notification_id",
                    notificationId
                )
            }
        )
    }
}
