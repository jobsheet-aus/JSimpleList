package au.com.jobsheet.jsimplelist

import androidx.compose.ui.graphics.Color

data class AvatarIconOption(
    val id: String,
    val label: String,
    val drawableRes: Int
)

data class AvatarColourOption(
    val id: String,
    val label: String,
    val colour: Color
)

object AvatarCatalog {
    const val DEFAULT_ICON_ID = "person"
    const val DEFAULT_COLOUR_ID = "blue"

    val icons = listOf(
        AvatarIconOption("person", "Person", R.drawable.ic_avatar_person),
        AvatarIconOption("flower", "Flower", R.drawable.ic_avatar_flower),
        AvatarIconOption("cat", "Cat", R.drawable.ic_avatar_cat),
        AvatarIconOption("horse", "Horse", R.drawable.ic_avatar_horse),
        AvatarIconOption("lightning", "Lightning", R.drawable.ic_avatar_lightning),
        AvatarIconOption("coffee", "Coffee", R.drawable.ic_avatar_coffee),
        AvatarIconOption("helmet", "Racing helmet", R.drawable.ic_avatar_helmet),
        AvatarIconOption("paw", "Paw", R.drawable.ic_avatar_paw),
        AvatarIconOption("book", "Book", R.drawable.ic_avatar_book),
        AvatarIconOption("alien", "Alien", R.drawable.ic_avatar_alien),
        AvatarIconOption("f1car", "F1 car", R.drawable.ic_avatar_f1car),
        AvatarIconOption("music", "Music", R.drawable.ic_avatar_music),
        AvatarIconOption("home", "Home", R.drawable.ic_avatar_home),
        AvatarIconOption("heart", "Heart", R.drawable.ic_avatar_heart),
        AvatarIconOption("star", "Star", R.drawable.ic_avatar_star),
        AvatarIconOption("wrench", "Wrench", R.drawable.ic_avatar_wrench),
        AvatarIconOption("camera", "Camera", R.drawable.ic_avatar_camera),
        AvatarIconOption("fish", "Fish", R.drawable.ic_avatar_fish),
        AvatarIconOption("football", "Football", R.drawable.ic_avatar_football),
        AvatarIconOption("smiley", "Smiley", R.drawable.ic_avatar_smiley)
    )

    val colours = listOf(
        AvatarColourOption("blue", "Blue", Color(0xFF4285F4)),
        AvatarColourOption("purple", "Purple", Color(0xFF7E57C2)),
        AvatarColourOption("pink", "Pink", Color(0xFFEC407A)),
        AvatarColourOption("orange", "Orange", Color(0xFFFB8C00)),
        AvatarColourOption("green", "Green", Color(0xFF43A047)),
        AvatarColourOption("grey", "Grey", Color(0xFF5F6368)),
        AvatarColourOption("red", "Red", Color(0xFFE53935)),
        AvatarColourOption("brown", "Brown", Color(0xFF795548))
    )

    fun drawableFor(id: String): Int =
        icons.firstOrNull { it.id == id }?.drawableRes
            ?: icons.first {
                it.id == DEFAULT_ICON_ID
            }.drawableRes

    fun colourFor(id: String): Color =
        colours.firstOrNull { it.id == id }?.colour
            ?: colours.first {
                it.id == DEFAULT_COLOUR_ID
            }.colour
}
