package au.com.jobsheet.jsimplelist

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface JSimpleListDao {
    @Query("SELECT * FROM lists ORDER BY position, createdAt")
    suspend fun loadLists(): List<ListEntity>

    @Query(
        """
        SELECT * FROM items
        WHERE listId = :listId
        ORDER BY completed, position, createdAt
        """
    )
    suspend fun loadItems(listId: String): List<ItemEntity>

    @Insert
    suspend fun insertList(list: ListEntity)

    @Insert
    suspend fun insertItems(items: List<ItemEntity>)

    @Update
    suspend fun updateList(list: ListEntity)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("DELETE FROM lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)
}