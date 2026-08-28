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
    val deletedAt: String? = null,

    @SerialName("origin_client_id")
    val originClientId: String? = null
)

@Serializable
private data class OnlineListDiscovery(
    val id: String,
    val name: String,
    val kind: String,
    val role: String,
    val position: Int,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("updated_at")
    val updatedAt: String
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
        items: List<ItemEntity>,
        originClientId: String
    ): ListSyncSnapshot {
        requireSignedIn()

        val createdAt =
            Instant.ofEpochMilli(list.createdAt).toString()
        val updatedAt =
            Instant.ofEpochMilli(list.updatedAt).toString()

        client.postgrest.rpc(
            function = "create_online_list",
            parameters = buildJsonObject {
                put("target_list_id", list.id)
                put("target_name", list.name)
                put("target_kind", list.kind)
                put("target_position", list.position)
                put("target_created_at", createdAt)
                put("target_updated_at", updatedAt)
            }
        )

        if (items.isNotEmpty()) {
            client
                .from("items")
                .upsert(
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
                                Instant.ofEpochMilli(item.updatedAt).toString(),
                            originClientId = originClientId
                        )
                    }
                )
        }

        return loadSnapshot(list.id)
    }

    suspend fun upsertItem(
        item: ItemEntity,
        originClientId: String
    ) {
        requireSignedIn()

        client
            .from("items")
            .upsert(
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
                        Instant.ofEpochMilli(item.updatedAt).toString(),
                    originClientId = originClientId
                )
            )
    }

    suspend fun deleteOnlineItem(
        itemId: String,
        originClientId: String
    ) {
        requireSignedIn()

        client.postgrest.rpc(
            function = "delete_online_item",
            parameters = buildJsonObject {
                put("target_item_id", itemId)
                put("target_origin_client_id", originClientId)
            }
        )
    }

    suspend fun discoverOnlineLists(
        dao: JSimpleListDao
    ): List<ListEntity> {
        requireSignedIn()

        val discoveredLists =
            client.postgrest
                .rpc(
                    function = "get_my_online_lists"
                )
                .decodeAs<List<OnlineListDiscovery>>()

        discoveredLists.forEach { discovered ->
            val onlineState =
                when (discovered.role) {
                    "owner" -> "ONLINE_OWNER"
                    "member" -> "ONLINE_MEMBER"
                    else -> error(
                        "Unknown online list role: ${discovered.role}"
                    )
                }

            val remoteCreatedAt =
                Instant.parse(discovered.createdAt).toEpochMilli()
            val remoteUpdatedAt =
                Instant.parse(discovered.updatedAt).toEpochMilli()

            val localList = dao.loadList(discovered.id)

            if (localList == null) {
                dao.insertList(
                    ListEntity(
                        id = discovered.id,
                        name = discovered.name,
                        kind = discovered.kind,
                        position = discovered.position,
                        createdAt = remoteCreatedAt,
                        updatedAt = remoteUpdatedAt,
                        onlineState = onlineState
                    )
                )
            } else {
                val remoteMetadataIsNewer =
                    remoteUpdatedAt > localList.updatedAt

                val updatedList =
                    localList.copy(
                        name =
                            if (remoteMetadataIsNewer) {
                                discovered.name
                            } else {
                                localList.name
                            },
                        kind =
                            if (remoteMetadataIsNewer) {
                                discovered.kind
                            } else {
                                localList.kind
                            },
                        position = discovered.position,
                        updatedAt =
                            maxOf(
                                localList.updatedAt,
                                remoteUpdatedAt
                            ),
                        onlineState = onlineState
                    )

                if (updatedList != localList) {
                    dao.updateList(updatedList)
                }
            }

            refreshList(
                listId = discovered.id,
                dao = dao
            )
        }

        return dao.loadLists()
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
            .decodeAs<ListSyncSnapshot>()
    }

    suspend fun refreshList(
        listId: String,
        dao: JSimpleListDao
    ): ListSyncSnapshot {
        val snapshot = loadSnapshot(listId)

        check(snapshot.state == "active") {
            "List is no longer active"
        }

        val remoteList =
            checkNotNull(snapshot.list) {
                "Active snapshot is missing list"
            }

        val localList = dao.loadList(listId)
        val remoteUpdatedAt =
            Instant.parse(remoteList.updatedAt).toEpochMilli()

        if (
            localList != null &&
            remoteUpdatedAt > localList.updatedAt
        ) {
            dao.updateList(
                localList.copy(
                    name = remoteList.name,
                    kind = remoteList.kind,
                    updatedAt = remoteUpdatedAt
                )
            )
        }

        val remoteItems =
            snapshot.items.map { item ->
                ItemEntity(
                    id = item.id,
                    listId = item.listId,
                    description = item.description,
                    quantity = item.quantity,
                    completed = item.completed,
                    position = item.position,
                    createdAt =
                        Instant.parse(item.createdAt).toEpochMilli(),
                    updatedAt =
                        Instant.parse(item.updatedAt).toEpochMilli(),
                    deletedAt =
                        item.deletedAt?.let {
                            Instant.parse(it).toEpochMilli()
                        }
                )
            }

        dao.mergeRemoteItems(
            listId = listId,
            remoteItems = remoteItems
        )

        return snapshot
    }

    private fun requireSignedIn() {
        check(client.auth.currentSessionOrNull() != null) {
            "Not signed in"
        }
    }
}
