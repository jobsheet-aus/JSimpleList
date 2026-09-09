package au.com.jobsheet.jsimplelist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AuthDialog(
    repository: AuthRepository,
    authState: AuthState,
    profileRepository: ProfileRepository,
    onOpenNotificationSettings: () -> Unit,
    onDeleteOnlineAccount: () -> Unit,
    onSignedIn: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val displayNameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var email by remember {
        mutableStateOf(authState.email ?: "")
    }
    var code by remember {
        mutableStateOf("")
    }
    var codeRequested by remember {
        mutableStateOf(false)
    }
    var busy by remember {
        mutableStateOf(false)
    }
    var message by remember {
        mutableStateOf<String?>(null)
    }
    var profile by remember {
        mutableStateOf<Profile?>(null)
    }
    var profileLoaded by remember {
        mutableStateOf(false)
    }
    var displayName by remember {
        mutableStateOf("")
    }
    var editingDisplayName by remember {
        mutableStateOf(false)
    }
    var displayNameEdit by remember {
        mutableStateOf(TextFieldValue(""))
    }
    var editingAvatar by remember {
        mutableStateOf(false)
    }
    var selectedAvatarIcon by remember {
        mutableStateOf("person")
    }
    var selectedAvatarColour by remember {
        mutableStateOf("blue")
    }

    LaunchedEffect(editingDisplayName) {
        if (editingDisplayName) {
            displayNameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(authState.userId) {
        if (!authState.isSignedIn) {
            profile = null
            profileLoaded = false
            displayName = ""
        } else {
            busy = true
            message = null

            try {
                profile = profileRepository.loadOrCreateMyProfile()
                displayName = profile?.displayName ?: ""
                selectedAvatarIcon =
                    profile?.avatarIcon ?: "person"
                selectedAvatarColour =
                    profile?.avatarColour ?: "blue"
                profileLoaded = true
            } catch (error: Exception) {
                message =
                    "Could not load online account details"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Account profile")
        },
        text = {
            Column {
                if (authState.isSignedIn) {
                    if (!profileLoaded) {
                        Text("Loading account profile")
                    } else if (profile == null) {
                        Text("What should we call you?")

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Display name",
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { value ->
                                if (value.length <= 50) {
                                    displayName = value
                                    message = null
                                }
                            },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                busy = true
                                message = null

                                coroutineScope.launch {
                                    try {
                                        profile =
                                            profileRepository.saveMyDisplayName(
                                                displayName
                                            )
                                        displayName =
                                            profile?.displayName ?: displayName
                                    } catch (error: Exception) {
                                        message =
                                            "Could not save display name"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled =
                                !busy &&
                                    displayName.trim().isNotEmpty()
                        ) {
                            Text("Save")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(
                                    AvatarCatalog.drawableFor(
                                        profile!!.avatarIcon
                                    )
                                ),
                                contentDescription = "Profile avatar",
                                tint =
                                    AvatarCatalog.colourFor(
                                        profile!!.avatarColour
                                    ),
                                modifier = Modifier.size(48.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = profile!!.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Email address",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = authState.email ?: email,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_cloud_connected
                                ),
                                contentDescription = null,
                                tint =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Cloud connected",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val currentName =
                                        profile!!.displayName

                                    displayNameEdit =
                                        TextFieldValue(
                                            text = currentName,
                                            selection =
                                                TextRange(
                                                    0,
                                                    currentName.length
                                                )
                                        )

                                    selectedAvatarIcon =
                                        profile!!.avatarIcon
                                    selectedAvatarColour =
                                        profile!!.avatarColour
                                    editingDisplayName = false
                                    editingAvatar = true
                                    message = null
                                }
                            ) {
                                Text("Edit profile")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Notifications",
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Manage invitations and sharing alerts in Android"
                    )

                    TextButton(
                        onClick = onOpenNotificationSettings
                    ) {
                        Text("Notification settings")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDeleteOnlineAccount,
                        enabled = !busy
                    ) {
                        Text(
                            text = "Delete online account",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text("Share selected lists with other people")

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Sign in with your email address to enable list sharing"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Local lists on this device continue to work without sign-in"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Email address",
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            message = null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        enabled = !busy && !codeRequested,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!codeRequested) {
                        Text(
                            "We'll email you a 6-digit one time password"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                busy = true
                                message = null

                                coroutineScope.launch {
                                    try {
                                        repository.requestEmailOtp(email)
                                        codeRequested = true
                                        message = "Code sent"
                                    } catch (error: Exception) {
                                        message =
                                            "Could not send code"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && email.isNotBlank()
                        ) {
                            Text("Send code")
                        }
                    } else {
                        Text("Enter the 6-digit code emailed to you")

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = code,
                            onValueChange = { value ->
                                if (
                                    value.length <= 6 &&
                                    value.all { it.isDigit() }
                                ) {
                                    code = value
                                    message = null
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                busy = true
                                message = null

                                coroutineScope.launch {
                                    try {
                                        repository.verifyEmailOtp(
                                            email = email,
                                            code = code
                                        )

                                        onSignedIn()
                                        message = null
                                    } catch (error: Exception) {
                                        message =
                                            "Could not verify code"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled =
                                !busy &&
                                    code.length == 6 &&
                                    code.all { it.isDigit() }
                        ) {
                            Text("Verify")
                        }

                        TextButton(
                            onClick = {
                                code = ""
                                codeRequested = false
                                message = null
                            },
                            enabled = !busy
                        ) {
                            Text("Use a different email address")
                        }
                    }
                }

                message?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy
            ) {
                Text("Close")
            }
        }
    )

    if (editingAvatar && profile != null) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    editingDisplayName = false
                    editingAvatar = false
                }
            },
            title = {
                Text("Edit profile")
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    )
                ) {
                    if (editingDisplayName) {
                        OutlinedTextField(
                            value = displayNameEdit,
                            onValueChange = { value ->
                                if (value.text.length <= 50) {
                                    displayNameEdit = value
                                    message = null
                                }
                            },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(
                                    displayNameFocusRequester
                                )
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .clickable {
                                    editingDisplayName = true
                                },
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayNameEdit.text,
                                style =
                                    MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Icon(
                                painter = painterResource(
                                    R.drawable.ic_edit_pencil
                                ),
                                contentDescription = "Edit display name",
                                tint =
                                    MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Icon(
                        painter = painterResource(
                            AvatarCatalog.drawableFor(
                                selectedAvatarIcon
                            )
                        ),
                        contentDescription = "Avatar preview",
                        tint =
                            AvatarCatalog.colourFor(
                                selectedAvatarColour
                            ),
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    AvatarCatalog.icons
                        .chunked(4)
                        .forEach { iconRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceEvenly
                            ) {
                                iconRow.forEach { option ->
                                    val selected =
                                        option.id ==
                                            selectedAvatarIcon

                                    Box(
                                        contentAlignment =
                                            Alignment.Center,
                                        modifier = Modifier
                                            .size(50.dp)
                                            .border(
                                                width = 1.5.dp,
                                                color =
                                                    if (selected) {
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                shape =
                                                    MaterialTheme
                                                        .shapes.small
                                            )
                                            .clickable {
                                                selectedAvatarIcon =
                                                    option.id
                                            }
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                option.drawableRes
                                            ),
                                            contentDescription =
                                                option.label,
                                            tint =
                                                AvatarCatalog.colourFor(
                                                    selectedAvatarColour
                                                ),
                                            modifier =
                                                Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )
                        }

                    Spacer(modifier = Modifier.height(12.dp))

                    AvatarCatalog.colours
                        .chunked(4)
                        .forEach { colourRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceEvenly
                            ) {
                                colourRow.forEach { option ->
                                    val selected =
                                        option.id ==
                                            selectedAvatarColour

                                    Box(
                                        contentAlignment =
                                            Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .border(
                                                width = 2.dp,
                                                color =
                                                    if (selected) {
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedAvatarColour =
                                                    option.id
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(option.colour)
                                        )
                                    }
                                }
                            }

                            Spacer(
                                modifier = Modifier.height(6.dp)
                            )
                        }

                    message?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        message = null

                        coroutineScope.launch {
                            try {
                                profile =
                                    profileRepository.saveMyProfileIdentity(
                                        displayName =
                                            displayNameEdit.text,
                                        avatarIcon =
                                            selectedAvatarIcon,
                                        avatarColour =
                                            selectedAvatarColour
                                    )

                                displayName =
                                    profile?.displayName
                                        ?: displayName

                                editingDisplayName = false
                                editingAvatar = false
                            } catch (error: Exception) {
                                message = "Could not save profile"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled =
                        !busy &&
                            displayNameEdit.text.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingDisplayName = false
                        editingAvatar = false
                    },
                    enabled = !busy
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}