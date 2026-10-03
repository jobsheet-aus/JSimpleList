package au.com.jobsheet.jsimplelist

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** A user move retained until server acknowledgement or deliberate resolution. */
@Entity(
    tableName = "pending_reorders",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("listId"),
        Index(value = ["accountId", "listId", "createdAt"])
    ]
)
data class PendingReorderEntity(
    @PrimaryKey val operationId: String,
    val listId: String,
    val accountId: String,
    /** Null until an authoritative server order revision is known. */
    val expectedRevision: Long?,
    val movedItemId: String,
    /** Completion group when the user committed the drag. */
    val movedCompleted: Boolean? = null,
    /** Neighbours in the moved item's completion group; null at the edge. */
    val beforeItemId: String?,
    val afterItemId: String?,
    /** JSON arrays of item UUIDs; encode/decode in the sync layer. */
    val baseOrderJson: String,
    val targetOrderJson: String,
    val createdAt: Long,
    /** PENDING, RETRY or CONFLICT; updated by the durable reorder sender. */
    val state: String = "PENDING",
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null
)
