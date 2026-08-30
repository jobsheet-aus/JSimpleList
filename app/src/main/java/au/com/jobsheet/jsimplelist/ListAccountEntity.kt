package au.com.jobsheet.jsimplelist

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "list_accounts",
    primaryKeys = [
        "listId",
        "accountId"
    ],
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
        Index("accountId")
    ]
)
data class ListAccountEntity(
    val listId: String,
    val accountId: String,
    val onlineState: String
)