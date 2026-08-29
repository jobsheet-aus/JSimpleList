package au.com.jobsheet.jsimplelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
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
    profileRepository: ProfileRepository,
    onSignedIn: () -> Unit,
    onDisableSharing: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val displayNameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var signedInEmail by remember {
        mutableStateOf(repository.currentUserEmail())
    }
    var email by remember {
        mutableStateOf(signedInEmail ?: "")
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

    LaunchedEffect(editingDisplayName) {
        if (editingDisplayName) {
            displayNameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(signedInEmail) {
        if (signedInEmail == null) {
            profile = null
            profileLoaded = false
            displayName = ""
        } else {
            busy = true
            message = null

            try {
                profile = profileRepository.loadMyProfile()
                displayName = profile?.displayName ?: ""
                profileLoaded = true
            } catch (error: Exception) {
                message =
                    error.message ?: "Could not load sharing details"
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sharing")
        },
        text = {
            Column {
                if (signedInEmail != null) {
                    if (!profileLoaded) {
                        Text("Loading sharing details")
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
                                            error.message
                                                ?: "Could not save display name"
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
                        Text("List sharing is enabled")

                        Spacer(modifier = Modifier.height(12.dp))

                        if (editingDisplayName) {
                            Text(
                                text = "Display name",
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(6.dp))

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
                                    .focusRequester(displayNameFocusRequester)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    busy = true
                                    message = null

                                    coroutineScope.launch {
                                        try {
                                            profile =
                                                profileRepository.saveMyDisplayName(
                                                    displayNameEdit.text
                                                )
                                            displayName =
                                                profile?.displayName ?: displayName
                                            editingDisplayName = false
                                        } catch (error: Exception) {
                                            message =
                                                error.message
                                                    ?: "Could not save display name"
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

                            TextButton(
                                onClick = {
                                    editingDisplayName = false
                                    message = null
                                },
                                enabled = !busy
                            ) {
                                Text("Cancel")
                            }
                        } else {
                            Text(
                                text = profile!!.displayName,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    val currentName = profile!!.displayName

                                    displayNameEdit = TextFieldValue(
                                        text = currentName,
                                        selection = TextRange(
                                            0,
                                            currentName.length
                                        )
                                    )
                                    editingDisplayName = true
                                    message = null
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Signed in with")
                        Text(signedInEmail!!)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                busy = true
                                message = null

                                coroutineScope.launch {
                                    try {
                                        repository.signOut()
                                        signedInEmail = null
                                        email = ""
                                        code = ""
                                        codeRequested = false
                                        profile = null
                                        profileLoaded = false
                                        displayName = ""
                                        message = "Signed out"
                                    } catch (error: Exception) {
                                        message =
                                            error.message ?: "Could not sign out"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy
                        ) {
                            Text("Sign out")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = onDisableSharing,
                            enabled = !busy
                        ) {
                            Text("Disable sharing")
                        }
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
                                            error.message ?: "Could not send code"
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

                                        signedInEmail =
                                            repository.currentUserEmail()
                                                ?: email.trim()

                                        onSignedIn()
                                        message = null
                                    } catch (error: Exception) {
                                        message =
                                            error.message ?: "Could not verify code"
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
}