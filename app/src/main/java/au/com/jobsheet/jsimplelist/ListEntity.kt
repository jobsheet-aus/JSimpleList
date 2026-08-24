package au.com.jobsheet.jsimplelist

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val kind: String,
    val position: Int,
    val createdAt: Long,
    val onlineState: String = "LOCAL"
)