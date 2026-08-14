package au.com.jobsheet.simplelist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ListKind {
    TODO,
    SHOPPING
}

data class SimpleListItem(
    val id: Long,
    val description: String,
    val quantity: Int? = null,
    val completed: Boolean = false
)

class SimpleListStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("simple_list", Context.MODE_PRIVATE)

    fun loadItems(kind: ListKind): List<SimpleListItem> {
        val raw = preferences.getString(itemsKey(kind), null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)

                    add(
                        SimpleListItem(
                            id = item.getLong("id"),
                            description = item.getString("description"),
                            quantity = if (item.isNull("quantity")) {
                                null
                            } else {
                                item.getInt("quantity")
                            },
                            completed = item.optBoolean("completed", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveItems(kind: ListKind, items: List<SimpleListItem>) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("description", item.description)

                    if (item.quantity == null) {
                        put("quantity", JSONObject.NULL)
                    } else {
                        put("quantity", item.quantity)
                    }

                    put("completed", item.completed)
                }
            )
        }

        preferences.edit()
            .putString(itemsKey(kind), array.toString())
            .apply()
    }

    fun loadFontScale(): Float =
        preferences.getFloat(KEY_FONT_SCALE, 1.0f)
            .coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    fun saveFontScale(scale: Float) {
        preferences.edit()
            .putFloat(
                KEY_FONT_SCALE,
                scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
            )
            .apply()
    }

    private fun itemsKey(kind: ListKind): String =
        when (kind) {
            ListKind.TODO -> KEY_TODO_ITEMS
            ListKind.SHOPPING -> KEY_SHOPPING_ITEMS
        }

    companion object {
        const val MIN_FONT_SCALE = 0.75f
        const val MAX_FONT_SCALE = 2.0f

        private const val KEY_TODO_ITEMS = "todo_items"
        private const val KEY_SHOPPING_ITEMS = "shopping_items"
        private const val KEY_FONT_SCALE = "font_scale"
    }
}