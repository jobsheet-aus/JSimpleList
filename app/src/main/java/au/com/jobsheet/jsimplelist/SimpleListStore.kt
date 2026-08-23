package au.com.jobsheet.jsimplelist

import android.content.Context
import org.json.JSONArray

data class LegacySimpleListItem(
    val id: Long,
    val description: String,
    val quantity: Int? = null,
    val completed: Boolean = false
)

class SimpleListStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("simple_list", Context.MODE_PRIVATE)

    fun hasLegacyLists(): Boolean =
        preferences.contains(KEY_TODO_ITEMS) ||
            preferences.contains(KEY_SHOPPING_ITEMS)

    fun loadItems(kind: ListKind): List<LegacySimpleListItem> {
        val raw = preferences.getString(itemsKey(kind), null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)

                    add(
                        LegacySimpleListItem(
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
            ListKind.DISCUSSION ->
                error("Discussion lists do not exist in legacy storage")
        }

    companion object {
        const val MIN_FONT_SCALE = 0.75f
        const val MAX_FONT_SCALE = 2.0f

        private const val KEY_TODO_ITEMS = "todo_items"
        private const val KEY_SHOPPING_ITEMS = "shopping_items"
        private const val KEY_FONT_SCALE = "font_scale"
    }
}