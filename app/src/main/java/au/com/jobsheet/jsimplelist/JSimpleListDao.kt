package au.com.jobsheet.jsimplelist

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update

@Dao
interface JSimpleListDao {
    @Query("SELECT COUNT(*) FROM lists")
    suspend fun countLists(): Int

    @Query("SELECT * FROM lists ORDER BY position, createdAt")
    suspend fun loadLists(): List<ListEntity>

    @Query(
        """
        SELECT
            lists.id,
            lists.name,
            lists.kind,
            lists.position,
            lists.createdAt,
            lists.updatedAt,
            CASE
                WHEN list_accounts.onlineState IS NOT NULL
                    THEN list_accounts.onlineState
                ELSE lists.onlineState
            END AS onlineState
        FROM lists
        LEFT JOIN list_accounts
            ON list_accounts.listId = lists.id
           AND list_accounts.accountId = :accountId
        WHERE (
            lists.onlineState = 'LOCAL'
            AND NOT EXISTS (
                SELECT 1
                FROM list_accounts AS any_account
                WHERE any_account.listId = lists.id
            )
        )
        OR list_accounts.accountId IS NOT NULL
        ORDER BY lists.position, lists.createdAt
        """
    )
    suspend fun loadVisibleLists(
        accountId: String?
    ): List<ListEntity>

    @Query(
        """
        INSERT OR REPLACE INTO list_accounts (
            listId,
            accountId,
            onlineState
        )
        VALUES (
            :listId,
            :accountId,
            :onlineState
        )
        """
    )
    suspend fun upsertListAccount(
        listId: String,
        accountId: String,
        onlineState: String
    )

    @Query(
        """
        SELECT onlineState
        FROM list_accounts
        WHERE listId = :listId
          AND accountId = :accountId
        LIMIT 1
        """
    )
    suspend fun loadListAccountState(
        listId: String,
        accountId: String
    ): String?

    @Query(
        """
        DELETE FROM list_accounts
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteListAccountsForAccount(
        accountId: String
    )

    @Query("SELECT * FROM lists WHERE id = :listId LIMIT 1")
    suspend fun loadList(listId: String): ListEntity?

    @Query(
        """
        SELECT * FROM items
        WHERE listId = :listId
          AND deletedAt IS NULL
        ORDER BY completed, position, createdAt
        """
    )
    suspend fun loadItems(listId: String): List<ItemEntity>

    @Query(
        """
        SELECT * FROM items
        WHERE listId = :listId
        ORDER BY position, createdAt
        """
    )
    suspend fun loadAllItems(listId: String): List<ItemEntity>

    @Insert
    suspend fun insertList(list: ListEntity)

    @Insert
    suspend fun insertLists(lists: List<ListEntity>)

    @Insert
    suspend fun insertItem(item: ItemEntity)

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

    @Transaction
    suspend fun mergeRemoteItems(
        listId: String,
        remoteItems: List<ItemEntity>
    ) {
        val localItems =
            loadAllItems(listId).associateBy { it.id }

        remoteItems.forEach { remoteItem ->
            val localItem = localItems[remoteItem.id]

            if (localItem == null) {
                insertItem(remoteItem)
            } else if (remoteItem.deletedAt != null) {
                if (localItem.deletedAt == null) {
                    updateItem(remoteItem)
                }
            } else if (
                localItem.deletedAt == null &&
                remoteItem.updatedAt > localItem.updatedAt
            ) {
                updateItem(remoteItem)
            }
        }
    }

    @Transaction
    suspend fun insertInitialDataIfEmpty(
        lists: List<ListEntity>,
        items: List<ItemEntity>
    ): Boolean {
        if (countLists() > 0) {
            return false
        }

        insertLists(lists)

        if (items.isNotEmpty()) {
            insertItems(items)
        }

        return true
    }
}