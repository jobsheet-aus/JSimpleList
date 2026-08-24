package au.com.jobsheet.jsimplelist

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable
private data class OnlineItemInsert(
    val id: String,

    @SerialName("list_id")
    val listId: String,

    val description: String,
    val quantity: Int?,
    val completed: Boolean,
    val position: Int,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String,

    @SerialName("deleted_at")
    val deletedAt: String? = null
)

@Serializable
data class ListSyncSnapshot(
    val state: String,

    @SerialName("list_id")
    val listId: String? = null,

    val list: OnlineListSnapshot? = null,
    val membership: OnlineMembershipSnapshot? = null,
    val items: List<OnlineItemSnapshot> = emptyList(),

    @SerialName("deleted_at")
    val deletedAt: String? = null,

    @SerialName("removed_at")
    val removedAt: String? = null
)

@Serializable
data class OnlineListSnapshot(
    val id: String,

    @SerialName("owner_id")
    val ownerId: String,

    val name: String,
    val kind: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String,

    @SerialName("deleted_at")
    val deletedAt: String? = null
)

@Serializable
data class OnlineMembershipSnapshot(
    @SerialName("user_id")
    val userId: String,

    val role: String,
    val position: Int? = null,

    @SerialName("joined_at")
    val joinedAt: String? = null,

    @SerialName("removed_at")
    val removedAt: String? = null,

    @SerialName("last_seen_at")
    val lastSeenAt: String? = null
)

@Serializable
data class OnlineItemSnapshot(
    val id: String,

    @SerialName("list_id")
    val listId: String,

    val description: String,
    val quantity: Int?,
    val completed: Boolean,
    val position: Int,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String,

    @SerialName("deleted_at")
    val deletedAt: String? = null
)

class ListSyncRepository(
    private val client: SupabaseClient = JSimpleListSupabase.client
) {
    suspend fun makeListOnline(
        list: ListEntity,
        items: List<ItemEntity>
    ): ListSyncSnapshot {
        requireSignedIn()

        val createdAt =
            Instant.ofEpochMilli(list.createdAt).toString()

        client.postgrest.rpc(
            function = "create_online_list",
            parameters = buildJsonObject {
                put("target_list_id", list.id)
                put("target_name", list.name)
                put("target_kind", list.kind)
                put("target_position", list.position)
                put("target_created_at", createdAt)
                put("target_updated_at", createdAt)
            }
        )

        if (items.isNotEmpty()) {
            client
                .from("items")
                .insert(
                    items.map { item ->
                        OnlineItemInsert(
                            id = item.id,
                            listId = item.listId,
                            description = item.description,
                            quantity = item.quantity,
                            completed = item.completed,
                            position = item.position,
                            createdAt =
                                Instant.ofEpochMilli(item.createdAt).toString(),
                            updatedAt =
                                Instant.ofEpochMilli(item.updatedAt).toString()
                        )
                    }
                )
        }

        return loadSnapshot(list.id)
    }

    suspend fun loadSnapshot(listId: String): ListSyncSnapshot {
        requireSignedIn()

        return client.postgrest
            .rpc(
                function = "get_list_sync_snapshot",
                parameters = buildJsonObject {
                    put("target_list_id", listId)
                }
            )
            .decodeSingle<ListSyncSnapshot>()
    }

    private fun requireSignedIn() {
        check(client.auth.currentSessionOrNull() != null) {
            "Not signed in"
        }
    }
}