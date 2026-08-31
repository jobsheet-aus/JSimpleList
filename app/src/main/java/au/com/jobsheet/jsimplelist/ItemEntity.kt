package au.com.jobsheet.jsimplelist

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("listId")
    ]
)
data class ItemEntity(
    @PrimaryKey
    val id: String,
    val listId: String,
    val description: String,
    val quantity: Int?,
    val completed: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val createdByUserId: String? = null,
    val updatedByUserId: String? = null
)