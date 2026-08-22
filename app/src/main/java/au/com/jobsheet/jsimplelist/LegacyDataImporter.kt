package au.com.jobsheet.jsimplelist

import java.util.UUID

class LegacyDataImporter(
    private val store: SimpleListStore,
    private val dao: JSimpleListDao
) {
    suspend fun importIfNeeded(): Boolean {
        val now = System.currentTimeMillis()

        val todoListId = UUID.randomUUID().toString()
        val shoppingListId = UUID.randomUUID().toString()

        val todoItems = store.loadItems(ListKind.TODO)
        val shoppingItems = store.loadItems(ListKind.SHOPPING)

        val lists = listOf(
            ListEntity(
                id = todoListId,
                name = "To-do",
                kind = ListKind.TODO.name,
                position = 10,
                createdAt = now
            ),
            ListEntity(
                id = shoppingListId,
                name = "Shopping",
                kind = ListKind.SHOPPING.name,
                position = 20,
                createdAt = now
            )
        )

        val items = buildList {
            addAll(
                todoItems.mapIndexed { index, item ->
                    item.toEntity(
                        listId = todoListId,
                        position = (index + 1) * 10
                    )
                }
            )

            addAll(
                shoppingItems.mapIndexed { index, item ->
                    item.toEntity(
                        listId = shoppingListId,
                        position = (index + 1) * 10
                    )
                }
            )
        }

        return dao.insertInitialDataIfEmpty(
            lists = lists,
            items = items
        )
    }

    private fun SimpleListItem.toEntity(
        listId: String,
        position: Int
    ): ItemEntity {
        val legacyCreatedAt =
            id.takeIf { it > 0L } ?: System.currentTimeMillis()

        return ItemEntity(
            id = UUID.randomUUID().toString(),
            listId = listId,
            description = description,
            quantity = quantity,
            completed = completed,
            position = position,
            createdAt = legacyCreatedAt,
            updatedAt = legacyCreatedAt
        )
    }
}