package au.com.jobsheet.jsimplelist

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

@Composable
fun ProfileAvatar(
    avatarIcon: String,
    avatarColour: String,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(
            AvatarCatalog.drawableFor(avatarIcon)
        ),
        contentDescription = contentDescription,
        tint = AvatarCatalog.colourFor(avatarColour),
        modifier = modifier.size(size)
    )
}
