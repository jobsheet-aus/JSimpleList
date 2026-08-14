package au.com.jobsheet.jsimplelist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.com.jobsheet.jsimplelist.ui.theme.SimpleListTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SimpleListTheme {
                SimpleListApp()
            }
        }
    }
}

@Composable
private fun SimpleListApp() {
    val context = LocalContext.current
    val store = remember { SimpleListStore(context.applicationContext) }

    val todoItems = remember {
        mutableStateListOf<SimpleListItem>().apply {
            addAll(store.loadItems(ListKind.TODO))
        }
    }

    val shoppingItems = remember {
        mutableStateListOf<SimpleListItem>().apply {
            addAll(store.loadItems(ListKind.SHOPPING))
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var fontScale by remember { mutableFloatStateOf(store.loadFontScale()) }
    var showAbout by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(fontScale) {
        delay(250)
        store.saveFontScale(fontScale)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(
                start = 16.dp,
                top = 10.dp,
                end = 8.dp,
                bottom = 8.dp
            )
        ) {
            Image(
                painter = painterResource(R.drawable.jsimplelist_logo),
                contentDescription = null,
                modifier = Modifier.width(38.dp)
            )

            Text(
                text = "JSimpleList",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = { showAbout = true }
            ) {
                Text(
                    text = "ⓘ",
                    fontSize = 22.sp
                )
            }
        }

        if (showAbout) {
            AlertDialog(
                onDismissRequest = { showAbout = false },
                title = {
                    Text(
                        text = "JSimpleList",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column {
                        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        Text("Build date ${BuildConfig.BUILD_DATE}")
                        Text("Package ${BuildConfig.APPLICATION_ID}")

                        Text(
                            text = "Produced by JobSheet",
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        TextButton(
                            onClick = {
                                uriHandler.openUri("https://www.jobsheet.com.au")
                            }
                        ) {
                            Text("www.jobsheet.com.au")
                        }

                        Text(
                            text = "Local-only\nNo account, advertising, analytics or internet access",
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Text(
                            text = "Source code",
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        TextButton(
                            onClick = {
                                uriHandler.openUri("https://github.com/jobsheet-aus/JSimpleList")
                            }
                        ) {
                            Text("github.com/jobsheet-aus/JSimpleList")
                        }

                        Text(
                            text = "Licence: MIT",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showAbout = false }
                    ) {
                        Text("Close")
                    }
                }
            )
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Todo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            )

            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Shopping",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }

        when (selectedTab) {
            0 -> ListScreen(
                kind = ListKind.TODO,
                items = todoItems,
                fontScale = fontScale,
                onFontScaleChange = { fontScale = it },
                onItemsChanged = {
                    store.saveItems(ListKind.TODO, todoItems)
                }
            )

            else -> ListScreen(
                kind = ListKind.SHOPPING,
                items = shoppingItems,
                fontScale = fontScale,
                onFontScaleChange = { fontScale = it },
                onItemsChanged = {
                    store.saveItems(ListKind.SHOPPING, shoppingItems)
                }
            )
        }
    }
}

@Composable
private fun ListScreen(
    kind: ListKind,
    items: MutableList<SimpleListItem>,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onItemsChanged: () -> Unit
) {
    var description by remember(kind) { mutableStateOf("") }
    var quantityText by remember(kind) { mutableStateOf("1") }

    val descriptionFocusRequester = remember { FocusRequester() }

    val currentScale by rememberUpdatedState(fontScale)
    val currentOnScaleChange by rememberUpdatedState(onFontScaleChange)

    val transformState = rememberTransformableState { zoomChange, _, _ ->
        currentOnScaleChange(
            (currentScale * zoomChange).coerceIn(
                SimpleListStore.MIN_FONT_SCALE,
                SimpleListStore.MAX_FONT_SCALE
            )
        )
    }

    fun addItem() {
        val trimmedDescription = description.trim()

        if (trimmedDescription.isEmpty()) {
            return
        }

        val quantity =
            if (kind == ListKind.SHOPPING) {
                quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1
            } else {
                null
            }

        items.add(
            SimpleListItem(
                id = System.currentTimeMillis(),
                description = trimmedDescription,
                quantity = quantity
            )
        )

        description = ""

        if (kind == ListKind.SHOPPING) {
            quantityText = "1"
        }

        onItemsChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformState)
    ) {
        EntryRow(
            kind = kind,
            description = description,
            onDescriptionChange = { description = it },
            quantityText = quantityText,
            onQuantityChange = { value ->
                quantityText = value.filter(Char::isDigit)
            },
            descriptionFocusRequester = descriptionFocusRequester,
            fontScale = fontScale,
            onAdd = ::addItem
        )

        if (kind == ListKind.SHOPPING && items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Spacer(modifier = Modifier.width(48.dp))

                Text(
                    text = "Qty",
                    fontSize = scaledSp(12f, fontScale),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp)
                )

                Text(
                    text = "Item",
                    fontSize = scaledSp(12f, fontScale),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()
        }

        val displayedItems =
            remember(items.toList()) {
                items.filterNot { it.completed } +
                    items.filter { it.completed }
            }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            items(
                items = displayedItems,
                key = { item -> item.id }
            ) { item ->
                ListItemRow(
                    item = item,
                    kind = kind,
                    fontScale = fontScale,
                    onUpdate = { newDescription, newQuantity ->
                        val index = items.indexOfFirst { it.id == item.id }

                        if (index >= 0) {
                            items[index] = item.copy(
                                description = newDescription,
                                quantity = newQuantity
                            )
                            onItemsChanged()
                        }
                    },
                    onToggle = {
                        val index = items.indexOfFirst { it.id == item.id }

                        if (index >= 0) {
                            items[index] = item.copy(
                                completed = !item.completed
                            )
                            onItemsChanged()
                        }
                    },
                    onDelete = {
                        items.removeAll { it.id == item.id }
                        onItemsChanged()
                    }
                )

                HorizontalDivider()
            }
        }

        if (items.size < 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Pinch anywhere on the list to resize text",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    kind: ListKind,
    description: String,
    onDescriptionChange: (String) -> Unit,
    quantityText: String,
    onQuantityChange: (String) -> Unit,
    descriptionFocusRequester: FocusRequester,
    fontScale: Float,
    onAdd: () -> Unit
) {
    var quantityFieldValue by remember(quantityText) {
        mutableStateOf(
            TextFieldValue(
                text = quantityText,
                selection = TextRange(quantityText.length)
            )
        )
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (kind == ListKind.SHOPPING) {
            OutlinedTextField(
                colors = textFieldColors,
                value = quantityFieldValue,
                onValueChange = { newValue ->
                    val digits = newValue.text.filter(Char::isDigit)

                    quantityFieldValue = newValue.copy(
                        text = digits,
                        selection = TextRange(
                            newValue.selection.start.coerceAtMost(digits.length),
                            newValue.selection.end.coerceAtMost(digits.length)
                        )
                    )

                    onQuantityChange(digits)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = scaledSp(16f, fontScale)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        descriptionFocusRequester.requestFocus()
                    }
                ),
                modifier = Modifier
                    .width(72.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            quantityFieldValue = quantityFieldValue.copy(
                                selection = TextRange(
                                    0,
                                    quantityFieldValue.text.length
                                )
                            )
                        }
                    }
            )
        }

        OutlinedTextField(
            colors = textFieldColors,
            value = description,
            onValueChange = onDescriptionChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = if (kind == ListKind.SHOPPING) {
                        "Item"
                    } else {
                        "Todo item"
                    },
                    fontSize = scaledSp(16f, fontScale)
                )
            },
            textStyle = TextStyle(
                fontSize = scaledSp(16f, fontScale)
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onAdd() }
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(descriptionFocusRequester)
        )

        Button(onClick = onAdd) {
            Text(
                text = "Add",
                fontSize = scaledSp(14f, fontScale)
            )
        }
    }
}

@Composable
private fun ListItemRow(
    item: SimpleListItem,
    kind: ListKind,
    fontScale: Float,
    onUpdate: (String, Int?) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(item.id) { mutableStateOf(false) }
    var editDescription by remember(item.id, item.description) {
        mutableStateOf(item.description)
    }
    var editQuantity by remember(item.id, item.quantity) {
        mutableStateOf(item.quantity?.toString().orEmpty())
    }
    var editQuantityFieldValue by remember(item.id, item.quantity) {
        val initialValue = item.quantity?.toString().orEmpty()

        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length)
            )
        )
    }

    fun saveEdit() {
        val newDescription = editDescription.trim()

        if (newDescription.isEmpty()) {
            return
        }

        val newQuantity =
            if (kind == ListKind.SHOPPING) {
                editQuantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
            } else {
                null
            }

        onUpdate(newDescription, newQuantity)
        editing = false
    }

    if (editing) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = item.completed,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                if (kind == ListKind.SHOPPING) {
                    OutlinedTextField(
                        value = editQuantityFieldValue,
                        onValueChange = { newValue ->
                            val digits = newValue.text.filter(Char::isDigit)

                            editQuantityFieldValue = newValue.copy(
                                text = digits,
                                selection = TextRange(
                                    newValue.selection.start.coerceAtMost(digits.length),
                                    newValue.selection.end.coerceAtMost(digits.length)
                                )
                            )

                            editQuantity = digits
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = scaledSp(16f, fontScale)
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .width(72.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    editQuantityFieldValue = editQuantityFieldValue.copy(
                                        selection = TextRange(
                                            0,
                                            editQuantityFieldValue.text.length
                                        )
                                    )
                                }
                            }
                    )
                }

                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = scaledSp(16f, fontScale)
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { saveEdit() }
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        editDescription = item.description
                        editQuantity = item.quantity?.toString().orEmpty()
                        editQuantityFieldValue = TextFieldValue(
                            text = editQuantity,
                            selection = TextRange(editQuantity.length)
                        )
                        editing = false
                    }
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp
                    )
                }

                TextButton(onClick = { saveEdit() }) {
                    Text(
                        text = "Save",
                        fontSize = 13.sp
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            if (kind == ListKind.SHOPPING) {
                Text(
                    text = item.quantity?.toString().orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = scaledSp(16f, fontScale),
                    modifier = Modifier
                        .width(48.dp)
                        .clickable { editing = true }
                )
            }

            Text(
                text = item.description,
                fontSize = scaledSp(16f, fontScale),
                textDecoration = if (item.completed) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                color = if (item.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
                    .clickable { editing = true }
            )

            TextButton(onClick = onDelete) {
                Text(
                    text = "Delete",
                    fontSize = scaledSp(13f, fontScale)
                )
            }
        }
    }
}

private fun scaledSp(
    baseSize: Float,
    scale: Float
) = (baseSize * scale).sp