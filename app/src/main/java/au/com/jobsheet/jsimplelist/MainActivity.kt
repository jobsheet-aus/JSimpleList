package au.com.jobsheet.jsimplelist

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
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
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class RealtimeListChangedPayload(
    @SerialName("list_id")
    val listId: String,

    @SerialName("origin_client_id")
    val originClientId: String? = null
)

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

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(
        connection: androidx.sqlite.SQLiteConnection
    ) {
        connection.execSQL(
            "ALTER TABLE lists ADD COLUMN onlineState TEXT NOT NULL DEFAULT 'LOCAL'"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(
        connection: androidx.sqlite.SQLiteConnection
    ) {
        connection.execSQL(
            "ALTER TABLE items ADD COLUMN deletedAt INTEGER"
        )
    }
}

@Composable
private fun SimpleListApp() {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val store = remember { SimpleListStore(applicationContext) }
    val clientInstanceId = remember {
        store.loadOrCreateClientInstanceId()
    }
    val authRepository = remember { AuthRepository() }
    val profileRepository = remember { ProfileRepository() }
    val listSyncRepository = remember { ListSyncRepository() }
    val database = remember {
        Room.databaseBuilder<JSimpleListDatabase>(
            context = applicationContext,
            name = "jsimplelist.db"
        )
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3
            )
            .build()
    }

    val dao = remember(database) { database.dao() }
    val lists = remember { mutableStateListOf<ListEntity>() }
    val itemsByList = remember {
        mutableStateMapOf<String, SnapshotStateList<ItemEntity>>()
    }
    val pagerState = rememberPagerState(
        pageCount = { lists.size }
    )
    var activeListRestored by remember { mutableStateOf(false) }

    LaunchedEffect(database) {
        LegacyDataImporter(
            store = store,
            dao = dao
        ).importIfNeeded()

        val loadedLists = dao.loadLists()

        lists.clear()
        itemsByList.clear()

        loadedLists.forEach { list ->
            lists.add(list)
            itemsByList[list.id] =
                mutableStateListOf<ItemEntity>().apply {
                    addAll(dao.loadItems(list.id))
                }
        }

        if (lists.isNotEmpty()) {
            val savedListId = store.loadLastActiveListId()
            val savedIndex =
                lists.indexOfFirst { it.id == savedListId }
            val targetIndex =
                if (savedIndex >= 0) {
                    savedIndex
                } else {
                    0
                }

            pagerState.requestScrollToPage(targetIndex)
        }

        activeListRestored = true
    }

    LaunchedEffect(
        pagerState.currentPage,
        activeListRestored,
        lists.size
    ) {
        if (activeListRestored && lists.isNotEmpty()) {
            val currentIndex =
                pagerState.currentPage.coerceIn(
                    0,
                    lists.lastIndex
                )

            store.saveLastActiveListId(
                lists[currentIndex].id
            )
        }
    }

    val currentRealtimeListId =
        if (activeListRestored && lists.isNotEmpty()) {
            val currentIndex =
                pagerState.currentPage.coerceIn(
                    0,
                    lists.lastIndex
                )

            lists[currentIndex]
                .takeIf { it.onlineState != "LOCAL" }
                ?.id
        } else {
            null
        }

    LaunchedEffect(
        currentRealtimeListId,
        clientInstanceId
    ) {
        val listId = currentRealtimeListId ?: return@LaunchedEffect

        val client = JSimpleListSupabase.client

        val channel =
            client.channel(
                "jsimplelist:list:$listId"
            ) {
                isPrivate = true
            }

        try {
            client.realtime.setAuth()

            val changes =
                channel.broadcastFlow<RealtimeListChangedPayload>(
                    event = "list_changed"
                )

            channel.subscribe(
                blockUntilSubscribed = false
            )

            changes.collect { payload ->
                if (payload.listId != listId) {
                    return@collect
                }

                if (payload.originClientId == clientInstanceId) {
                    Log.d(
                        "JSimpleListSync",
                        "Ignoring own realtime change list=$listId"
                    )
                    return@collect
                }

                Log.i(
                    "JSimpleListSync",
                    "Realtime change received list=$listId origin=${payload.originClientId}"
                )

                try {
                    listSyncRepository.refreshList(
                        listId = listId,
                        dao = dao
                    )

                    val refreshedItems =
                        dao.loadItems(listId)

                    itemsByList[listId]?.apply {
                        clear()
                        addAll(refreshedItems)
                    }
                } catch (exception: Exception) {
                    Log.e(
                        "JSimpleListSync",
                        "Realtime refresh failed list=$listId",
                        exception
                    )
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(
                "JSimpleListSync",
                "Realtime subscription failed list=$listId",
                exception
            )
        } finally {
            client.realtime.removeChannel(channel)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var fontScale by remember { mutableFloatStateOf(store.loadFontScale()) }
    var showMenu by remember { mutableStateOf(false) }
    var showListSharing by remember { mutableStateOf(false) }
    var showDisableSharing by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showListSelector by remember { mutableStateOf(false) }
    var refreshingListId by remember { mutableStateOf<String?>(null) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var newListName by remember {
        mutableStateOf(
            TextFieldValue(
                text = "To-do list",
                selection = TextRange(0, "To-do list".length)
            )
        )
    }
    var newListKind by remember { mutableStateOf(ListKind.TODO) }
    var renameListId by remember { mutableStateOf<String?>(null) }
    var renameListName by remember {
        mutableStateOf(TextFieldValue(""))
    }
    var deleteListId by remember { mutableStateOf<String?>(null) }
    var makingOnlineListId by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    suspend fun persistItemUpdate(
        list: ListEntity,
        item: ItemEntity
    ) {
        dao.updateItem(item)

        if (list.onlineState != "LOCAL") {
            try {
                listSyncRepository.upsertItem(
                    item = item,
                    originClientId = clientInstanceId
                )
            } catch (exception: Exception) {
                Log.e(
                    "JSimpleListSync",
                    "Item push failed after update id=${item.id} list=${list.id}: ${exception::class.simpleName}"
                )
            }
        }
    }

    BackHandler(enabled = showListSelector) {
        showListSelector = false
    }

    LaunchedEffect(fontScale) {
        delay(250)
        store.saveFontScale(fontScale)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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

            Box {
                TextButton(
                    onClick = { showMenu = true }
                ) {
                    Text(
                        text = "☰",
                        fontSize = 24.sp
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.width(230.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "List sharing",
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            showListSharing = true
                        },
                        modifier = Modifier.height(58.dp)
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Disable sharing",
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            showDisableSharing = true
                        },
                        modifier = Modifier.height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "About",
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            showAbout = true
                        },
                        modifier = Modifier.height(58.dp)
                    )
                }
            }
        }

        if (showListSharing) {
            AuthDialog(
                repository = authRepository,
                profileRepository = profileRepository,
                onDismiss = {
                    showListSharing = false
                }
            )
        }

        if (showDisableSharing) {
            AlertDialog(
                onDismissRequest = {
                    showDisableSharing = false
                },
                title = {
                    Text("Disable sharing")
                },
                text = {
                    Column {
                        Text(
                            "This permanently removes your JSimpleList online " +
                                "account and shared-list access."
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Local lists stored on this device are not deleted."
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Shared lists you own will be destroyed and other " +
                                "members will lose access."
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Account deletion is not yet enabled in this " +
                                "development build."
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDisableSharing = false
                        }
                    ) {
                        Text("Close")
                    }
                }
            )
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
                        Row {
                            Text(
                                text = "Version",
                                modifier = Modifier.width(64.dp)
                            )
                            Text(BuildConfig.VERSION_NAME)
                        }

                        Row {
                            Text(
                                text = "Built",
                                modifier = Modifier.width(64.dp)
                            )
                            Text(BuildConfig.BUILD_DATE)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Simple To-do and Shopping lists",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Lists are stored locally on this device")
                        Text("Completely free and without ads")

                        Spacer(modifier = Modifier.height(20.dp))

                        Text("Produced by JobSheet")
                        Text(
                            text = "www.jobsheet.com.au",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    uriHandler.openUri("https://www.jobsheet.com.au")
                                }
                        )

                        Text(
                            text = "Source code",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = "GitHub repository",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    uriHandler.openUri("https://github.com/jobsheet-aus/JSimpleList")
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Copyright © 2026")
                        Text("21 Twelve Consulting Pty Ltd")

                        Text(
                            text = "Licensed under the MIT Licence",
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

        val newListFocusRequester = remember { FocusRequester() }

        LaunchedEffect(showNewListDialog) {
            if (showNewListDialog) {
                newListFocusRequester.requestFocus()
            }
        }

        if (showNewListDialog) {
            AlertDialog(
                onDismissRequest = {
                    showNewListDialog = false
                },
                title = {
                    Text("New list")
                },
                text = {
                    Column {
                        Text(
                            text = "Type",
                            fontWeight = FontWeight.Medium
                        )

                        ListKind.entries.forEach { kind ->
                            val selectKind = {
                                val existingDefault =
                                    when (newListKind) {
                                        ListKind.TODO -> "To-do list"
                                        ListKind.SHOPPING -> "Shopping list"
                                        ListKind.DISCUSSION -> "Discussion points"
                                    }

                                val newDefault =
                                    when (kind) {
                                        ListKind.TODO -> "To-do list"
                                        ListKind.SHOPPING -> "Shopping list"
                                        ListKind.DISCUSSION -> "Discussion points"
                                    }

                                val replaceName =
                                    newListName.text.isBlank() ||
                                        newListName.text == existingDefault

                                newListKind = kind

                                if (replaceName) {
                                    newListName = TextFieldValue(
                                        text = newDefault,
                                        selection = TextRange(
                                            0,
                                            newDefault.length
                                        )
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectKind()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = newListKind == kind,
                                    onClick = {
                                        selectKind()
                                    }
                                )

                                Text(
                                    text = listKindLabel(kind)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = newListName,
                            onValueChange = { newListName = it },
                            label = {
                                Text("Name")
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(newListFocusRequester)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = newListName.text.trim().isNotEmpty(),
                        onClick = {
                            val name = newListName.text.trim()
                            val now = System.currentTimeMillis()
                            val list = ListEntity(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                kind = newListKind.name,
                                position =
                                    (lists.maxOfOrNull { it.position } ?: 0) + 10,
                                createdAt = now
                            )

                            coroutineScope.launch {
                                dao.insertList(list)

                                lists.add(list)
                                itemsByList[list.id] =
                                    mutableStateListOf<ItemEntity>()

                                showNewListDialog = false

                                pagerState.animateScrollToPage(
                                    lists.lastIndex
                                )
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNewListDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        val renameList =
            renameListId?.let { listId ->
                lists.firstOrNull { it.id == listId }
            }

        val renameFocusRequester = remember { FocusRequester() }

        LaunchedEffect(renameListId) {
            if (renameListId != null) {
                renameFocusRequester.requestFocus()
            }
        }

        if (renameList != null) {
            AlertDialog(
                onDismissRequest = {
                    renameListId = null
                },
                title = {
                    Text("Rename list")
                },
                text = {
                    OutlinedTextField(
                        value = renameListName,
                        onValueChange = { renameListName = it },
                        label = {
                            Text("Name")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameFocusRequester)
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = renameListName.text.trim().isNotEmpty(),
                        onClick = {
                            val name = renameListName.text.trim()
                            val index =
                                lists.indexOfFirst {
                                    it.id == renameList.id
                                }

                            if (index >= 0) {
                                val updatedList =
                                    renameList.copy(name = name)

                                lists[index] = updatedList

                                coroutineScope.launch {
                                    dao.updateList(updatedList)
                                }
                            }

                            renameListId = null
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            renameListId = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        val deleteList =
            deleteListId?.let { listId ->
                lists.firstOrNull { it.id == listId }
            }

        if (deleteList != null) {
            AlertDialog(
                onDismissRequest = {
                    deleteListId = null
                },
                title = {
                    Text("Delete list")
                },
                text = {
                    Text(
                        "Delete \"${deleteList.name}\" and all its items?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val deleteIndex =
                                lists.indexOfFirst {
                                    it.id == deleteList.id
                                }

                            coroutineScope.launch {
                                dao.deleteList(deleteList.id)

                                itemsByList.remove(deleteList.id)

                                if (deleteIndex >= 0) {
                                    lists.removeAt(deleteIndex)
                                }

                                deleteListId = null

                                if (lists.isNotEmpty()) {
                                    val targetIndex =
                                        deleteIndex.coerceIn(
                                            0,
                                            lists.lastIndex
                                        )

                                    pagerState.scrollToPage(targetIndex)
                                }
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            deleteListId = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (activeListRestored && lists.isNotEmpty()) {
            val currentIndex =
                pagerState.currentPage.coerceIn(0, lists.lastIndex)
            val currentList = lists[currentIndex]
            val currentItems = itemsByList[currentList.id]
            val currentKind = ListKind.valueOf(currentList.kind)
            val currentItemCount =
                currentItems?.size ?: 0
            val hasCompletedItems =
                currentItems?.any { it.completed } == true

            val uncheckAllItems = {
                val affectedItems =
                    currentItems
                        ?.filter { it.completed }
                        ?.toList()
                        ?: emptyList()

                if (affectedItems.isNotEmpty()) {
                    val operationTime =
                        System.currentTimeMillis()

                    affectedItems.forEach { item ->
                        val index =
                            currentItems?.indexOfFirst {
                                it.id == item.id
                            } ?: -1

                        if (index >= 0) {
                            val updatedItem =
                                item.copy(
                                    completed = false,
                                    updatedAt = operationTime
                                )

                            currentItems?.set(
                                index,
                                updatedItem
                            )

                            coroutineScope.launch {
                                persistItemUpdate(
                                    list = currentList,
                                    item = updatedItem
                                )
                            }
                        }
                    }

                    coroutineScope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = "All items unchecked",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long
                            )

                        if (
                            result ==
                            SnackbarResult.ActionPerformed
                        ) {
                            val undoTime =
                                System.currentTimeMillis()

                            affectedItems.forEach { originalItem ->
                                val index =
                                    currentItems?.indexOfFirst {
                                        it.id == originalItem.id
                                    } ?: -1

                                if (index >= 0) {
                                    val currentItem =
                                        currentItems?.getOrNull(index)

                                    if (
                                        currentItem != null &&
                                        !currentItem.completed &&
                                        currentItem.updatedAt ==
                                        operationTime
                                    ) {
                                        val restoredItem =
                                            currentItem.copy(
                                                completed = true,
                                                updatedAt = undoTime
                                            )

                                        currentItems?.set(
                                            index,
                                            restoredItem
                                        )

                                        coroutineScope.launch {
                                            persistItemUpdate(
                                                list = currentList,
                                                item = restoredItem
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (showListSelector) {
                            16.dp
                        } else {
                            4.dp
                        },
                        top = 4.dp,
                        end = 8.dp,
                        bottom = 6.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {


                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            showListSelector = !showListSelector
                        }
                ) {
                    Text(
                        text = if (showListSelector) {
                            "Lists"
                        } else {
                            "${currentList.name} ▾"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (!showListSelector) {
                        Text(
                            text =
                                "${listKindLabel(currentKind)} · " +
                                    "$currentItemCount " +
                                    if (currentItemCount == 1) {
                                        "item"
                                    } else {
                                        "items"
                                    },
                            fontSize = 12.sp,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (showListSelector) {
                    TextButton(
                        onClick = {
                            showListSelector = false
                        }
                    ) {
                        Text("Close")
                    }
                }

                if (
                    !showListSelector &&
                    currentList.onlineState != "LOCAL"
                ) {
                    TextButton(
                        enabled = refreshingListId == null,
                        onClick = {
                            coroutineScope.launch {
                                refreshingListId = currentList.id

                                try {
                                    listSyncRepository.refreshList(
                                        listId = currentList.id,
                                        dao = dao
                                    )

                                    val refreshedItems =
                                        dao.loadItems(currentList.id)

                                    currentItems?.apply {
                                        clear()
                                        addAll(refreshedItems)
                                    }

                                    Toast.makeText(
                                        context,
                                        "List refreshed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (exception: Exception) {
                                    Log.e(
                                        "JSimpleListSync",
                                        "Refresh failed for list id=${currentList.id}: ${exception::class.simpleName}"
                                    )

                                    Toast.makeText(
                                        context,
                                        exception.message
                                            ?: "Could not refresh list",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    refreshingListId = null
                                }
                            }
                        }
                    ) {
                        Text(
                            if (refreshingListId == currentList.id) {
                                "Refreshing"
                            } else {
                                "Refresh"
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            if (showListSelector) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable {
                            showListSelector = false
                        }
                ) {
                    item {
                        TextButton(
                            onClick = {
                                val defaultName = "To-do list"

                                newListName = TextFieldValue(
                                    text = defaultName,
                                    selection = TextRange(
                                        0,
                                        defaultName.length
                                    )
                                )
                                newListKind = ListKind.TODO
                                showNewListDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text("+ New list")
                        }

                        HorizontalDivider()
                    }

                    items(
                        items = lists,
                        key = { list -> list.id }
                    ) { list ->
                        val listItems = itemsByList[list.id]
                        val kind = ListKind.valueOf(list.kind)
                        val itemCount =
                            listItems?.size ?: 0
                        val index = lists.indexOf(list)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 16.dp,
                                    end = 8.dp,
                                    top = 6.dp,
                                    bottom = 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showListSelector = false

                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = list.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text =
                                        "${listKindLabel(kind)} · " +
                                            "$itemCount " +
                                            if (itemCount == 1) {
                                                "item"
                                            } else {
                                                "items"
                                            },
                                    fontSize = 12.sp,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (list.onlineState == "LOCAL") {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            makingOnlineListId = list.id

                                        try {
                                            val localItems =
                                                listItems?.toList() ?: emptyList()

                                            Log.i(
                                                "JSimpleListSync",
                                                "Sharing list id=${list.id} name=${list.name} items=${localItems.size}"
                                            )

                                            val snapshot =
                                                listSyncRepository.makeListOnline(
                                                    list = list,
                                                    items = localItems,
                                                    originClientId = clientInstanceId
                                                )

                                            check(snapshot.state == "active") {
                                                "Unexpected sync state: ${snapshot.state}"
                                            }

                                            check(snapshot.list?.id == list.id) {
                                                "Online list UUID does not match local list"
                                            }

                                            check(
                                                snapshot.items
                                                    .map { it.id }
                                                    .toSet() ==
                                                    localItems
                                                        .map { it.id }
                                                        .toSet()
                                            ) {
                                                "Online item UUIDs do not match local items"
                                            }

                                            val listIndex =
                                                lists.indexOfFirst {
                                                    it.id == list.id
                                                }

                                            if (listIndex >= 0) {
                                                val onlineList =
                                                    lists[listIndex].copy(
                                                        onlineState = "ONLINE_OWNER"
                                                    )

                                                lists[listIndex] = onlineList
                                                dao.updateList(onlineList)
                                            }

                                            Log.i(
                                                "JSimpleListSync",
                                                "Shared list id=${list.id} state=${snapshot.state} items=${snapshot.items.size}"
                                            )

                                            Toast.makeText(
                                                context,
                                                "List shared · ${snapshot.items.size} items",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (exception: Exception) {
                                            Log.e(
                                                "JSimpleListSync",
                                                "Share failed for list id=${list.id} name=${list.name}: ${exception::class.simpleName}"
                                            )

                                            Toast.makeText(
                                                context,
                                                exception.message
                                                    ?: "Could not share list",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            makingOnlineListId = null
                                        }
                                    }
                                },
                                enabled = makingOnlineListId == null
                            ) {
                                Text(
                                    if (makingOnlineListId == list.id) {
                                        "Sharing"
                                    } else {
                                        "Share"
                                    }
                                )
                            }
                            }

                            TextButton(
                                onClick = {
                                    renameListName =
                                        TextFieldValue(
                                            text = list.name,
                                            selection = TextRange(
                                                0,
                                                list.name.length
                                            )
                                        )
                                    renameListId = list.id
                                }
                            ) {
                                Text("Rename")
                            }

                            TextButton(
                                onClick = {
                                    deleteListId = list.id
                                }
                            ) {
                                Text("Delete")
                            }
                        }

                        HorizontalDivider()
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val list = lists[page]
                    val items = itemsByList[list.id]
                    val kind = ListKind.valueOf(list.kind)

                    if (items != null) {
                        ListScreen(
                            listId = list.id,
                            kind = kind,
                            items = items,
                            hasCompletedItems = hasCompletedItems,
                            onUncheckAll = uncheckAllItems,
                            fontScale = fontScale,
                            onFontScaleChange = { fontScale = it },
                            onItemAdded = { item ->
                                coroutineScope.launch {
                                    dao.insertItem(item)

                                    if (list.onlineState != "LOCAL") {
                                        try {
                                            listSyncRepository.upsertItem(
                                                item = item,
                                                originClientId = clientInstanceId
                                            )
                                        } catch (exception: Exception) {
                                            Log.e(
                                                "JSimpleListSync",
                                                "Item push failed after add id=${item.id} list=${list.id}: ${exception::class.simpleName}"
                                            )
                                        }
                                    }
                                }
                            },
                            onItemUpdated = { item ->
                                coroutineScope.launch {
                                    persistItemUpdate(
                                        list = list,
                                        item = item
                                    )
                                }
                            },
                            onItemDeleted = { item ->
                                coroutineScope.launch {
                                    if (list.onlineState == "LOCAL") {
                                        dao.deleteItem(item.id)
                                    } else {
                                        val now = System.currentTimeMillis()
                                        val deletedItem =
                                            item.copy(
                                                updatedAt = now,
                                                deletedAt = now
                                            )

                                        dao.updateItem(deletedItem)

                                        try {
                                            listSyncRepository.deleteOnlineItem(
                                                itemId = item.id,
                                                originClientId = clientInstanceId
                                            )
                                        } catch (exception: Exception) {
                                            Log.e(
                                                "JSimpleListSync",
                                                "Item tombstone push failed id=${item.id} list=${list.id}: ${exception::class.simpleName}"
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else if (activeListRestored) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No lists",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                TextButton(
                    onClick = {
                        val defaultName = "To-do list"

                        newListName = TextFieldValue(
                            text = defaultName,
                            selection = TextRange(
                                0,
                                defaultName.length
                            )
                        )
                        newListKind = ListKind.TODO
                        showNewListDialog = true
                    }
                ) {
                    Text("+ New list")
                }
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

private fun listKindLabel(kind: ListKind): String =
    when (kind) {
        ListKind.TODO -> "To-do"
        ListKind.SHOPPING -> "Shopping"
        ListKind.DISCUSSION -> "Discussion points"
    }

@Composable
private fun ListScreen(
    listId: String,
    kind: ListKind,
    items: MutableList<ItemEntity>,
    hasCompletedItems: Boolean,
    onUncheckAll: () -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onItemAdded: (ItemEntity) -> Unit,
    onItemUpdated: (ItemEntity) -> Unit,
    onItemDeleted: (ItemEntity) -> Unit
) {
    var description by remember(kind) { mutableStateOf("") }
    var quantityText by remember(kind) { mutableStateOf("1") }
    var pendingScrollItemId by remember(listId) {
        mutableStateOf<String?>(null)
    }

    val descriptionFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val currentScale by rememberUpdatedState(fontScale)
    val currentOnScaleChange by rememberUpdatedState(onFontScaleChange)

    val pinchModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            var pinching = false
            var gestureScale = currentScale

            while (true) {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }

                if (pressedCount >= 2) {
                    if (!pinching) {
                        pinching = true
                        gestureScale = currentScale
                    }

                    gestureScale = (
                        gestureScale * event.calculateZoom()
                    ).coerceIn(
                        SimpleListStore.MIN_FONT_SCALE,
                        SimpleListStore.MAX_FONT_SCALE
                    )

                    currentOnScaleChange(gestureScale)
                }

                if (pinching) {
                    event.changes.forEach { it.consume() }
                }

                if (event.changes.none { it.pressed }) {
                    break
                }
            }
        }
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

        val now = System.currentTimeMillis()
        val item = ItemEntity(
            id = UUID.randomUUID().toString(),
            listId = listId,
            description = trimmedDescription,
            quantity = quantity,
            completed = false,
            position = (items.minOfOrNull { it.position } ?: 10) - 10,
            createdAt = now,
            updatedAt = now
        )

        items.add(item)
        onItemAdded(item)
        pendingScrollItemId = item.id

        description = ""

        if (kind == ListKind.SHOPPING) {
            quantityText = "1"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(pinchModifier)
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

        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasCompletedItems,
                    enabled = hasCompletedItems,
                    onCheckedChange = {
                        onUncheckAll()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                if (kind == ListKind.SHOPPING) {
                    Text(
                        text = "Qty",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(48.dp)
                    )
                }

                Text(
                    text = "Item",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()
        }

        val displayedItems =
            remember(items.toList()) {
                val orderedItems =
                    items.sortedWith(
                        compareBy<ItemEntity> { it.position }
                            .thenBy { it.createdAt }
                    )

                orderedItems.filterNot { it.completed } +
                    orderedItems.filter { it.completed }
            }

        LaunchedEffect(
            pendingScrollItemId,
            displayedItems
        ) {
            val targetId = pendingScrollItemId

            if (
                targetId != null &&
                displayedItems.firstOrNull()?.id == targetId
            ) {
                listState.scrollToItem(0)
                pendingScrollItemId = null
            }
        }

        val resizeHintHeightPx =
            with(LocalDensity.current) {
                48.dp.roundToPx()
            }

        val showResizeHint by remember(
            listState,
            resizeHintHeightPx,
            displayedItems.size
        ) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo

                if (displayedItems.isEmpty() || visibleItems.isEmpty()) {
                    true
                } else {
                    val lastVisibleItem = visibleItems.last()

                    lastVisibleItem.index == displayedItems.lastIndex &&
                        lastVisibleItem.offset + lastVisibleItem.size <=
                            layoutInfo.viewportEndOffset - resizeHintHeightPx
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
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
                                val updatedItem = item.copy(
                                    description = newDescription,
                                    quantity = newQuantity,
                                    updatedAt = System.currentTimeMillis()
                                )
                                items[index] = updatedItem
                                onItemUpdated(updatedItem)
                            }
                        },
                        onToggle = {
                            val index = items.indexOfFirst { it.id == item.id }

                            if (index >= 0) {
                                val updatedItem = item.copy(
                                    completed = !item.completed,
                                    updatedAt = System.currentTimeMillis()
                                )
                                items[index] = updatedItem
                                onItemUpdated(updatedItem)
                            }
                        },
                        onDelete = {
                            items.removeAll { it.id == item.id }
                            onItemDeleted(item)
                        }
                    )

                    HorizontalDivider()
                }
            }

            if (showResizeHint) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
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

    var quantityFocused by remember { mutableStateOf(false) }
    var descriptionFocused by remember { mutableStateOf(false) }

    LaunchedEffect(quantityFocused) {
        if (quantityFocused) {
            delay(50)

            quantityFieldValue = quantityFieldValue.copy(
                selection = TextRange(
                    0,
                    quantityFieldValue.text.length
                )
            )
        }
    }

    val entryTextStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = entryFieldSp(16f, fontScale),
        lineHeight = entryFieldSp(16f, fontScale)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (kind == ListKind.SHOPPING) {
            BasicTextField(
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
                textStyle = entryTextStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                    .width(48.dp)
                    .height(48.dp)
                    .border(
                        width = if (quantityFocused) 2.dp else 1.dp,
                        color = if (quantityFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = MaterialTheme.shapes.small
                    )
                    .onFocusChanged { focusState ->
                        quantityFocused = focusState.isFocused
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        innerTextField()
                    }
                }
            )
        }

        BasicTextField(
            value = description,
            onValueChange = onDescriptionChange,
            singleLine = true,
            textStyle = entryTextStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onAdd() }
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .border(
                    width = if (descriptionFocused) 2.dp else 1.dp,
                    color = if (descriptionFocused) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = MaterialTheme.shapes.small
                )
                .focusRequester(descriptionFocusRequester)
                .onFocusChanged { focusState ->
                    descriptionFocused = focusState.isFocused
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (description.isEmpty()) {
                        Text(
                            text = if (kind == ListKind.SHOPPING) {
                                "Item"
                            } else {
                                "To-do item"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = entryFieldSp(16f, fontScale),
                            lineHeight = entryFieldSp(16f, fontScale)
                        )
                    }

                    innerTextField()
                }
            }
        )

        Button(
            onClick = onAdd,
            modifier = Modifier.height(40.dp)
        ) {
            Text(
                text = "Add",
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ListItemRow(
    item: ItemEntity,
    kind: ListKind,
    fontScale: Float,
    onUpdate: (String, Int?) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember(item.id) { mutableStateOf(false) }
    var editDescription by remember(item.id, item.description) {
        mutableStateOf(
            TextFieldValue(
                text = item.description,
                selection = TextRange(item.description.length)
            )
        )
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

    var editQuantityFocused by remember { mutableStateOf(false) }
    val editDescriptionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(editQuantityFocused) {
        if (editQuantityFocused) {
            delay(50)

            editQuantityFieldValue = editQuantityFieldValue.copy(
                selection = TextRange(
                    0,
                    editQuantityFieldValue.text.length
                )
            )
        }
    }

    LaunchedEffect(editing) {
        if (editing) {
            editDescriptionFocusRequester.requestFocus()
        }
    }

    fun cancelEdit() {
        editDescription = TextFieldValue(
            text = item.description,
            selection = TextRange(item.description.length)
        )
        editQuantity = item.quantity?.toString().orEmpty()
        editQuantityFieldValue = TextFieldValue(
            text = editQuantity,
            selection = TextRange(editQuantity.length)
        )
        editing = false
    }

    BackHandler(enabled = editing) {
        cancelEdit()
    }

    fun saveEdit() {
        val newDescription = editDescription.text.trim()

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
                            .width(48.dp)
                            .height(48.dp)
                            .onFocusChanged { focusState ->
                                editQuantityFocused = focusState.isFocused
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
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(editDescriptionFocusRequester)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { cancelEdit() }
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
                .padding(horizontal = 4.dp, vertical = 0.dp),
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
                    textDecoration = if (item.completed) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
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
                    .clickable {
                        editDescription = TextFieldValue(
                            text = item.description,
                            selection = TextRange(item.description.length)
                        )
                        editing = true
                    }
            )

            TextButton(onClick = onDelete) {
                Text(
                    text = "Delete",
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun entryFieldSp(
    baseSize: Float,
    scale: Float
) = (baseSize * scale).coerceAtMost(22f).sp

private fun scaledSp(
    baseSize: Float,
    scale: Float
) = (baseSize * scale).sp
