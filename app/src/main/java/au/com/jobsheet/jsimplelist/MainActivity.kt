package au.com.jobsheet.jsimplelist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Icon
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import au.com.jobsheet.jsimplelist.ui.theme.SimpleListTheme
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

private data class SharingNotificationRoute(
    val eventType: String,
    val recipientUserId: String,
    val listId: String,
    val invitationId: String?
)

class MainActivity : ComponentActivity() {
    private var authRefreshSignal by mutableStateOf(0)

    private var sharingNotificationRoute by
        mutableStateOf<SharingNotificationRoute?>(null)

    private var sharingNotificationRouteSignal by
        mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SharingNotificationManager(
            applicationContext
        ).createChannel()

        handleAuthIntent(intent)
        handleSharingNotificationIntent(intent)

        setContent {
            SimpleListTheme {
                SimpleListApp(
                    authRefreshSignal = authRefreshSignal,
                    sharingNotificationRoute =
                        sharingNotificationRoute,
                    sharingNotificationRouteSignal =
                        sharingNotificationRouteSignal
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        handleSharingNotificationIntent(intent)
    }

    private fun handleSharingNotificationIntent(
        intent: Intent
    ) {
        val eventType =
            intent.getStringExtra(
                SharingNotificationManager.EXTRA_EVENT_TYPE
            )
                ?.trim()
                .orEmpty()

        if (
            eventType !=
                SharingNotificationManager.EVENT_LIST_INVITATION &&
            eventType !=
                SharingNotificationManager.EVENT_INVITATION_ACCEPTED &&
            eventType !=
                SharingNotificationManager.EVENT_INVITATION_DECLINED
        ) {
            return
        }

        val recipientUserId =
            intent.getStringExtra(
                SharingNotificationManager.EXTRA_RECIPIENT_USER_ID
            )
                ?.trim()
                .orEmpty()

        val listId =
            intent.getStringExtra(
                SharingNotificationManager.EXTRA_LIST_ID
            )
                ?.trim()
                .orEmpty()

        if (
            recipientUserId.isEmpty() ||
            listId.isEmpty()
        ) {
            Log.w(
                "JSimpleListPush",
                "Ignoring incomplete notification route"
            )
            return
        }

        val invitationId =
            intent.getStringExtra(
                SharingNotificationManager.EXTRA_INVITATION_ID
            )
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        if (
            eventType ==
                SharingNotificationManager.EVENT_LIST_INVITATION &&
            invitationId == null
        ) {
            Log.w(
                "JSimpleListPush",
                "Ignoring invitation route without invitation ID"
            )
            return
        }

        sharingNotificationRoute =
            SharingNotificationRoute(
                eventType = eventType,
                recipientUserId = recipientUserId,
                listId = listId,
                invitationId = invitationId
            )

        sharingNotificationRouteSignal += 1
    }

    private fun handleAuthIntent(intent: Intent) {
        val uri = intent.data

        if (
            uri?.scheme != "https" ||
            uri.host != "jslist.jobsheet.com.au" ||
            uri.path != "/auth/invite"
        ) {
            return
        }

        JSimpleListSupabase.client.handleDeeplinks(intent) {
            authRefreshSignal += 1
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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(
        connection: androidx.sqlite.SQLiteConnection
    ) {
        connection.execSQL(
            "ALTER TABLE lists ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "UPDATE lists SET updatedAt = createdAt WHERE updatedAt = 0"
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(
        connection: androidx.sqlite.SQLiteConnection
    ) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS list_accounts (
                listId TEXT NOT NULL,
                accountId TEXT NOT NULL,
                onlineState TEXT NOT NULL,
                PRIMARY KEY (listId, accountId),
                FOREIGN KEY (listId)
                    REFERENCES lists(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_list_accounts_listId " +
                "ON list_accounts(listId)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_list_accounts_accountId " +
                "ON list_accounts(accountId)"
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(
        connection: androidx.sqlite.SQLiteConnection
    ) {
        connection.execSQL(
            "ALTER TABLE items ADD COLUMN createdByUserId TEXT"
        )
        connection.execSQL(
            "ALTER TABLE items ADD COLUMN updatedByUserId TEXT"
        )
    }
}

@Composable
private fun SimpleListApp(
    authRefreshSignal: Int,
    sharingNotificationRoute: SharingNotificationRoute?,
    sharingNotificationRouteSignal: Int
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val sharingNotificationManager = remember {
        SharingNotificationManager(applicationContext)
    }
    val store = remember { SimpleListStore(applicationContext) }
    val clientInstanceId = remember {
        store.loadOrCreateClientInstanceId()
    }
    val authRepository = remember { AuthRepository() }
    val pushDeviceRepository = remember { PushDeviceRepository() }
    val pushOutboxRepository = remember { PushOutboxRepository() }
    val profileRepository = remember { ProfileRepository() }
    val sharingRepository = remember {
        SharingRepository(profileRepository)
    }
    val listSyncRepository = remember { ListSyncRepository() }
    val database = remember {
        Room.databaseBuilder<JSimpleListDatabase>(
            context = applicationContext,
            name = "jsimplelist.db"
        )
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6
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
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    val invitationRepository = remember { InvitationRepository() }
    val pendingInvitations = remember {
        mutableStateListOf<PendingInvitation>()
    }
    var acceptingInvitationId by remember {
        mutableStateOf<String?>(null)
    }

    var decliningInvitationId by remember {
        mutableStateOf<String?>(null)
    }
    var targetedInvitationId by remember {
        mutableStateOf<String?>(null)
    }
    var showNotificationPermissionExplanation by remember {
        mutableStateOf(false)
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            showNotificationPermissionExplanation = false
        }

    fun offerNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationPermissionExplanation = true
        }
    }

    LaunchedEffect(database, authRefreshSignal) {
        LegacyDataImporter(
            store = store,
            dao = dao
        ).importIfNeeded()

        authRepository.awaitSessionInitialization()

        signedInEmail = authRepository.currentUserEmail()

        val accountId = authRepository.currentUserId()

        store.saveActivePushUserId(accountId)

        if (accountId != null) {
            offerNotificationPermission()
            try {
                pushDeviceRepository.requestFirebaseRegistration()

                pushDeviceRepository.registerStoredDevice(
                    store = store,
                    clientInstanceId = clientInstanceId
                )
            } catch (exception: Exception) {
                Log.w(
                    "JSimpleListPush",
                    "Push-device registration failed at startup",
                    exception
                )
            }

            try {
                profileRepository.loadOrCreateMyProfile()
            } catch (exception: Exception) {
                Log.e(
                    "JSimpleListProfile",
                    "Automatic profile creation failed",
                    exception
                )
            }

            try {
                listSyncRepository.discoverOnlineLists(
                    dao = dao
                )
            } catch (exception: Exception) {
                Log.e(
                    "JSimpleListSync",
                    "Online list discovery failed at startup",
                    exception
                )
            }

            try {
                pendingInvitations.clear()
                pendingInvitations.addAll(
                    invitationRepository.loadPendingInvitations()
                )
            } catch (exception: Exception) {
                Log.e(
                    "JSimpleListInvitation",
                    "Invitation discovery failed at startup",
                    exception
                )
            }

        }

        val loadedLists =
            dao.loadVisibleLists(accountId)

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
        sharingNotificationRouteSignal,
        activeListRestored
    ) {
        if (!activeListRestored) {
            return@LaunchedEffect
        }

        val route =
            sharingNotificationRoute
                ?: return@LaunchedEffect

        val activeAccountId =
            authRepository.currentUserId()

        if (
            activeAccountId == null ||
            activeAccountId != route.recipientUserId
        ) {
            Log.i(
                "JSimpleListPush",
                "Ignoring notification route for inactive account"
            )
            targetedInvitationId = null
            return@LaunchedEffect
        }

        when (route.eventType) {
            SharingNotificationManager.EVENT_LIST_INVITATION -> {
                val invitationId =
                    route.invitationId
                        ?: return@LaunchedEffect

                try {
                    val refreshedInvitations =
                        invitationRepository
                            .loadPendingInvitations()

                    pendingInvitations.clear()
                    pendingInvitations.addAll(
                        refreshedInvitations
                    )

                    targetedInvitationId =
                        refreshedInvitations
                            .firstOrNull {
                                it.id == invitationId
                            }
                            ?.id

                    if (targetedInvitationId == null) {
                        Log.i(
                            "JSimpleListPush",
                            "Tapped invitation is no longer pending"
                        )
                    }
                } catch (exception: Exception) {
                    Log.e(
                        "JSimpleListInvitation",
                        "Could not refresh tapped invitation",
                        exception
                    )
                }
            }

            SharingNotificationManager.EVENT_INVITATION_ACCEPTED,
            SharingNotificationManager.EVENT_INVITATION_DECLINED -> {
                targetedInvitationId = null

                val targetIndex =
                    lists.indexOfFirst {
                        it.id == route.listId
                    }

                if (targetIndex >= 0) {
                    pagerState.scrollToPage(
                        targetIndex
                    )
                } else {
                    Log.i(
                        "JSimpleListPush",
                        "Tapped notification list is not visible"
                    )
                }
            }
        }
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

                    val refreshedList =
                        dao.loadList(listId)

                    if (refreshedList != null) {
                        val listIndex =
                            lists.indexOfFirst {
                                it.id == listId
                            }

                        if (listIndex >= 0) {
                            lists[listIndex] = refreshedList
                        }
                    }

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
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var deletingOnlineAccount by remember { mutableStateOf(false) }
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
    var sharingListId by remember { mutableStateOf<String?>(null) }
    var sharedListInfoListId by remember {
        mutableStateOf<String?>(null)
    }
    var sharedListInfo by remember {
        mutableStateOf<SharedListInfo?>(null)
    }
    var sharedListInfoLoading by remember {
        mutableStateOf(false)
    }
    var sharedListInfoError by remember {
        mutableStateOf<String?>(null)
    }
    var invitationEmail by remember { mutableStateOf("") }
    var sendingInvitation by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    fun openSharedListInfo(listId: String) {
        sharedListInfoListId = listId
        sharedListInfo = null
        sharedListInfoError = null
        sharedListInfoLoading = true

        coroutineScope.launch {
            try {
                sharedListInfo =
                    sharingRepository.loadSharedListInfo(listId)
            } catch (exception: Exception) {
                Log.e(
                    "JSimpleListSharing",
                    "Could not load shared list info",
                    exception
                )

                sharedListInfoError =
                    exception.message
                        ?: "Could not load sharing details"
            } finally {
                sharedListInfoLoading = false
            }
        }
    }

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

    suspend fun makeListAvailableOnline(
        list: ListEntity,
        listItems: List<ItemEntity>
    ) {
        makingOnlineListId = list.id

        try {
            Log.i(
                "JSimpleListSync",
                "Sharing list id=${list.id} name=${list.name} items=${listItems.size}"
            )

            val snapshot =
                listSyncRepository.makeListOnline(
                    list = list,
                    items = listItems,
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
                    listItems
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

                val accountId =
                    checkNotNull(
                        authRepository.currentUserId()
                    ) {
                        "Not signed in"
                    }

                dao.upsertListAccount(
                    listId = onlineList.id,
                    accountId = accountId,
                    onlineState = "ONLINE_OWNER"
                )
            }

            Log.i(
                "JSimpleListSync",
                "Shared list id=${list.id} state=${snapshot.state} items=${snapshot.items.size}"
            )

            Toast.makeText(
                context,
                "List available online · ${snapshot.items.size} items",
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
                    ?: "Could not make list available online",
                Toast.LENGTH_LONG
            ).show()

            throw exception
        } finally {
            makingOnlineListId = null
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

            androidx.compose.material3.IconButton(
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
                Text(
                    text = "+",
                    fontSize = 28.sp
                )
            }

            Box {
                androidx.compose.material3.IconButton(
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
                    modifier = Modifier.width(210.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Manage lists",
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            showListSelector = true
                        },
                        modifier = Modifier.height(52.dp)
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                text =
                                    if (signedInEmail == null) {
                                        "Sign in"
                                    } else {
                                        "Online account"
                                    },
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            showMenu = false
                            showListSharing = true
                        },
                        modifier = Modifier.height(52.dp)
                    )

                    if (signedInEmail != null) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "Sign out",
                                        fontSize = 16.sp
                                    )

                                    Text(
                                        text = signedInEmail!!,
                                        fontSize = 12.sp,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                showMenu = false
                                showSignOutConfirmation = true
                            },
                            modifier = Modifier.height(52.dp)
                        )
                    }

                    HorizontalDivider()

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
                        modifier = Modifier.height(52.dp)
                    )
                }
            }
        }

        if (showSignOutConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    if (!signingOut) {
                        showSignOutConfirmation = false
                    }
                },
                title = {
                    Text("Sign out?")
                },
                text = {
                    Text(
                        "Shared lists will be unavailable on this device " +
                            "until you sign in again. Local lists will " +
                            "remain available."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            signingOut = true

                            coroutineScope.launch {
                                try {
                                    try {
                                        pushDeviceRepository.unregister(
                                            clientInstanceId
                                        )
                                    } catch (exception: Exception) {
                                        /*
                                         * Do not trap the user in a signed-in
                                         * state because a network unregister
                                         * failed. A later registration of the
                                         * same FCM token displaces stale
                                         * ownership server-side.
                                         */
                                        Log.w(
                                            "JSimpleListPush",
                                            "Push-device unregister failed",
                                            exception
                                        )
                                    }

                                    authRepository.signOut()

                                    store.saveActivePushUserId(null)

                                    signedInEmail = null
                                    pendingInvitations.clear()
                                    acceptingInvitationId = null

                                    val loadedLists =
                                        dao.loadVisibleLists(null)

                                    lists.clear()
                                    itemsByList.clear()

                                    loadedLists.forEach { list ->
                                        lists.add(list)
                                        itemsByList[list.id] =
                                            mutableStateListOf<ItemEntity>().apply {
                                                addAll(
                                                    dao.loadItems(list.id)
                                                )
                                            }
                                    }

                                    showSignOutConfirmation = false
                                } catch (exception: Exception) {
                                    Log.e(
                                        "JSimpleListAuth",
                                        "Sign out failed",
                                        exception
                                    )

                                    Toast.makeText(
                                        context,
                                        exception.message
                                            ?: "Could not sign out",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    signingOut = false
                                }
                            }
                        },
                        enabled = !signingOut
                    ) {
                        Text(
                            if (signingOut) {
                                "Signing out"
                            } else {
                                "Sign out"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSignOutConfirmation = false
                        },
                        enabled = !signingOut
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showListSharing) {
            AuthDialog(
                repository = authRepository,
                profileRepository = profileRepository,
                onOpenNotificationSettings = {
                    val intent =
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).apply {
                            putExtra(
                                Settings.EXTRA_APP_PACKAGE,
                                context.packageName
                            )
                        }

                    context.startActivity(intent)
                },
                onDeleteOnlineAccount = {
                    showListSharing = false
                    showDeleteAccount = true
                },
                onSignedIn = {
                    coroutineScope.launch {
                        try {
                            listSyncRepository.discoverOnlineLists(
                                dao = dao
                            )

                            signedInEmail =
                                authRepository.currentUserEmail()

                            store.saveActivePushUserId(
                                authRepository.currentUserId()
                            )

                            try {
                                pushDeviceRepository.requestFirebaseRegistration()

                                pushDeviceRepository.registerStoredDevice(
                                    store = store,
                                    clientInstanceId = clientInstanceId
                                )
                            } catch (exception: Exception) {
                                Log.w(
                                    "JSimpleListPush",
                                    "Push-device registration failed after sign-in",
                                    exception
                                )
                            }

                            offerNotificationPermission()

                            pendingInvitations.clear()
                            pendingInvitations.addAll(
                                invitationRepository.loadPendingInvitations()
                            )



                            val loadedLists =
                                dao.loadVisibleLists(
                                    authRepository.currentUserId()
                                )

                            lists.clear()
                            itemsByList.clear()

                            loadedLists.forEach { list ->
                                lists.add(list)
                                itemsByList[list.id] =
                                    mutableStateListOf<ItemEntity>().apply {
                                        addAll(dao.loadItems(list.id))
                                    }
                            }
                        } catch (exception: Exception) {
                            Log.e(
                                "JSimpleListSync",
                                "Online list discovery failed after sign-in",
                                exception
                            )

                            Toast.makeText(
                                context,
                                exception.message
                                    ?: "Could not load online lists",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onDismiss = {
                    showListSharing = false
                }
            )
        }

        if (showNotificationPermissionExplanation) {
            AlertDialog(
                onDismissRequest = {
                    showNotificationPermissionExplanation = false
                },
                title = {
                    Text("Sharing notifications")
                },
                text = {
                    Text(
                        "JSimpleList uses notifications for list invitations " +
                            "and important sharing updates. Enable notifications " +
                            "so you do not miss activity on shared lists"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (
                                store.hasRequestedNotificationPermission()
                            ) {
                                val intent =
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).apply {
                                        putExtra(
                                            Settings.EXTRA_APP_PACKAGE,
                                            context.packageName
                                        )
                                    }

                                context.startActivity(intent)
                                showNotificationPermissionExplanation = false
                            } else {
                                store.markNotificationPermissionRequested()

                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        }
                    ) {
                        Text(
                            if (
                                store.hasRequestedNotificationPermission()
                            ) {
                                "Open notification settings"
                            } else {
                                "Enable notifications"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotificationPermissionExplanation = false
                        }
                    ) {
                        Text("Not now")
                    }
                }
            )
        }

        if (
            !showListSharing &&
            pendingInvitations.isNotEmpty()
        ) {
            val invitation =
                targetedInvitationId
                    ?.let { targetId ->
                        pendingInvitations
                            .firstOrNull {
                                it.id == targetId
                            }
                    }
                    ?: pendingInvitations.first()

            val listTypeDescription =
                when (invitation.listKind) {
                    "SHOPPING" -> "Shopping list"
                    "TODO" -> "To-do list"
                    "DISCUSSION" -> "Discussion list"
                    else -> "list"
                }

            val invitationBusy =
                acceptingInvitationId != null ||
                    decliningInvitationId != null

            AlertDialog(
                onDismissRequest = {
                    /*
                     * Deliberately do nothing.
                     *
                     * A pending invitation remains pending until the user
                     * explicitly accepts or declines it.
                     */
                },
                title = {
                    Text("List invitation")
                },
                text = {
                    Text(
                        "You have been invited by " +
                            invitation.inviterDisplayName +
                            " to join " +
                            invitation.listName +
                            ". This is a " +
                            listTypeDescription +
                            " that you can add items to and mark as done.\n\n" +
                            "Accepting the invitation gives you access to the list."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            acceptingInvitationId = invitation.id

                            coroutineScope.launch {
                                try {
                                    val acceptedListId =
                                        invitationRepository.acceptInvitation(
                                            invitation.id
                                        )

                                    try {
                                        pushOutboxRepository.processPending()
                                    } catch (exception: Exception) {
                                        Log.w(
                                            "JSimpleListPush",
                                            "Push outbox nudge failed",
                                            exception
                                        )
                                    }

                                    val loadedLists =
                                        listSyncRepository.discoverOnlineLists(
                                            dao = dao
                                        )

                                    lists.clear()
                                    itemsByList.clear()

                                    loadedLists.forEach { list ->
                                        lists.add(list)
                                        itemsByList[list.id] =
                                            mutableStateListOf<ItemEntity>().apply {
                                                addAll(
                                                    dao.loadItems(list.id)
                                                )
                                            }
                                    }

                                    pendingInvitations.remove(invitation)

                                    if (
                                        targetedInvitationId ==
                                        invitation.id
                                    ) {
                                        targetedInvitationId = null
                                    }

                                    val acceptedIndex =
                                        lists.indexOfFirst {
                                            it.id == acceptedListId
                                        }

                                    if (acceptedIndex >= 0) {
                                        pagerState.scrollToPage(
                                            acceptedIndex
                                        )
                                    }

                                    Toast.makeText(
                                        context,
                                        "Shared list added",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } catch (exception: Exception) {
                                    Log.e(
                                        "JSimpleListInvitation",
                                        "Could not accept invitation",
                                        exception
                                    )

                                    Toast.makeText(
                                        context,
                                        exception.message
                                            ?: "Could not accept invitation",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    acceptingInvitationId = null
                                }
                            }
                        },
                        enabled = !invitationBusy
                    ) {
                        Text(
                            if (
                                acceptingInvitationId ==
                                invitation.id
                            ) {
                                "Accepting"
                            } else {
                                "Accept"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            decliningInvitationId = invitation.id

                            coroutineScope.launch {
                                try {
                                    invitationRepository.declineInvitation(
                                        invitation.id
                                    )

                                    pendingInvitations.remove(invitation)

                                    if (
                                        targetedInvitationId ==
                                        invitation.id
                                    ) {
                                        targetedInvitationId = null
                                    }

                                    try {
                                        pushOutboxRepository.processPending()
                                    } catch (exception: Exception) {
                                        Log.w(
                                            "JSimpleListPush",
                                            "Push outbox nudge failed after decline",
                                            exception
                                        )
                                    }
                                } catch (exception: Exception) {
                                    Log.e(
                                        "JSimpleListInvitation",
                                        "Could not decline invitation",
                                        exception
                                    )

                                    Toast.makeText(
                                        context,
                                        exception.message
                                            ?: "Could not decline invitation",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    decliningInvitationId = null
                                }
                            }
                        },
                        enabled = !invitationBusy
                    ) {
                        Text(
                            if (
                                decliningInvitationId ==
                                invitation.id
                            ) {
                                "Declining"
                            } else {
                                "Decline"
                            }
                        )
                    }
                }
            )
        }



        if (showDeleteAccount) {
            AlertDialog(
                onDismissRequest = {
                    if (!deletingOnlineAccount) {
                        showDeleteAccount = false
                    }
                },
                title = {
                    Text("Delete account")
                },
                text = {
                    Column {
                        Text(
                            "This permanently removes your JSimpleList online account."
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Shared lists you own will be destroyed and other " +
                                "members will lose access to those lists."
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Lists stored on this device will remain available."
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deletingOnlineAccount = true

                            coroutineScope.launch {
                                try {
                                    val accountId =
                                        authRepository.currentUserId()
                                            ?: error("No signed-in account")

                                    val ownedListIds =
                                        lists
                                            .filter {
                                                it.onlineState == "ONLINE_OWNER"
                                            }
                                            .map { it.id }

                                    authRepository.deleteOnlineAccount()

                                    dao.deleteListAccountsForAccount(accountId)

                                    ownedListIds.forEach { listId ->
                                        dao.deleteList(listId)
                                    }

                                    try {
                                        authRepository.signOut()
                                    } catch (exception: Exception) {
                                        Log.w(
                                            "JSimpleListAuth",
                                            "Sign out after account deletion failed",
                                            exception
                                        )
                                    }

                                    store.saveActivePushUserId(null)

                                    signedInEmail = null
                                    pendingInvitations.clear()
                                    acceptingInvitationId = null

                                    val loadedLists =
                                        dao.loadVisibleLists(null)

                                    lists.clear()
                                    itemsByList.clear()

                                    loadedLists.forEach { list ->
                                        lists.add(list)
                                        itemsByList[list.id] =
                                            mutableStateListOf<ItemEntity>().apply {
                                                addAll(dao.loadItems(list.id))
                                            }
                                    }

                                    showDeleteAccount = false

                                    Toast.makeText(
                                        context,
                                        "Online account deleted",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (exception: Exception) {
                                    Log.e(
                                        "JSimpleListAuth",
                                        "Online account deletion failed",
                                        exception
                                    )

                                    Toast.makeText(
                                        context,
                                        exception.message
                                            ?: "Could not delete online account",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    deletingOnlineAccount = false
                                }
                            }
                        },
                        enabled = !deletingOnlineAccount
                    ) {
                        Text(
                            if (deletingOnlineAccount) {
                                "Deleting..."
                            } else {
                                "Delete account"
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteAccount = false
                        },
                        enabled = !deletingOnlineAccount
                    ) {
                        Text("Cancel")
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
                            text = "Simple To-do, Shopping and Discussion lists",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Keep lists on this device or share them privately online")
                        Text("No ads. No fees.")

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

        sharedListInfoListId?.let { listId ->
            val infoList =
                lists.firstOrNull {
                    it.id == listId
                }

            if (infoList == null) {
                sharedListInfoListId = null
                sharedListInfo = null
                sharedListInfoError = null
                sharedListInfoLoading = false
            } else {
                AlertDialog(
                    onDismissRequest = {
                        sharedListInfoListId = null
                        sharedListInfo = null
                        sharedListInfoError = null
                        sharedListInfoLoading = false
                    },
                    title = {
                        Text("Shared list")
                    },
                    text = {
                        Column {
                            Text(
                                text = infoList.name,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            when {
                                sharedListInfoLoading -> {
                                    Text("Loading sharing details")
                                }

                                sharedListInfoError != null -> {
                                    Text(
                                        sharedListInfoError
                                            ?: "Could not load sharing details"
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = {
                                            openSharedListInfo(listId)
                                        }
                                    ) {
                                        Text("Retry")
                                    }
                                }

                                sharedListInfo != null -> {
                                    val info = sharedListInfo!!
                                    val owner =
                                        info.members.firstOrNull {
                                            it.role == "owner"
                                        }
                                    val members =
                                        info.members.filter {
                                            it.role != "owner"
                                        }

                                    Text(
                                        text = "Owner",
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        owner?.displayName
                                            ?: "Unknown owner"
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Members",
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (members.isEmpty()) {
                                        Text("No other members")
                                    } else {
                                        members.forEach { member ->
                                            Text(member.displayName)
                                        }
                                    }

                                    if (
                                        info.pendingInvitations.isNotEmpty()
                                    ) {
                                        Spacer(
                                            modifier =
                                                Modifier.height(16.dp)
                                        )

                                        Text(
                                            text = "Pending invitations",
                                            fontWeight = FontWeight.Medium
                                        )

                                        Spacer(
                                            modifier =
                                                Modifier.height(4.dp)
                                        )

                                        info.pendingInvitations.forEach {
                                            invitation ->
                                            Text(invitation.invitedEmail)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                sharedListInfoListId = null
                                sharedListInfo = null
                                sharedListInfoError = null
                                sharedListInfoLoading = false
                            }
                        ) {
                            Text("Close")
                        }
                    }
                )
            }
        }


        sharingListId?.let { listId ->
            val sharingList =
                lists.firstOrNull {
                    it.id == listId
                }

            if (sharingList == null) {
                sharingListId = null
            } else {
                val sharingItems =
                    itemsByList[sharingList.id]?.toList()
                        ?: emptyList()

                AlertDialog(
                    onDismissRequest = {
                        if (
                            makingOnlineListId == null &&
                            !sendingInvitation
                        ) {
                            sharingListId = null
                            invitationEmail = ""
                        }
                    },
                    title = {
                        Text("Share list")
                    },
                    text = {
                        Column {
                            Text(
                                text = sharingList.name,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            if (signedInEmail == null) {
                                Text(
                                    "Sign in before making this list available " +
                                        "online or sharing it with someone"
                                )
                            } else if (sharingList.onlineState == "LOCAL") {
                                Text("Use this list on my other devices")

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    "Make this list available online wherever " +
                                        "$signedInEmail is signed in"
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                makeListAvailableOnline(
                                                    list = sharingList,
                                                    listItems = sharingItems
                                                )
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                    enabled = makingOnlineListId == null
                                ) {
                                    Text(
                                        if (
                                            makingOnlineListId ==
                                            sharingList.id
                                        ) {
                                            "Making available"
                                        } else {
                                            "Make available online"
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Invite someone",
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = invitationEmail,
                                    onValueChange = {
                                        invitationEmail = it
                                    },
                                    label = {
                                        Text("Email address")
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Done
                                    ),
                                    enabled = !sendingInvitation,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            sendingInvitation = true

                                            try {
                                                makeListAvailableOnline(
                                                    list = sharingList,
                                                    listItems = sharingItems
                                                )

                                                invitationRepository.sendInvitation(
                                                    listId = sharingList.id,
                                                    email = invitationEmail
                                                )

                                                Toast.makeText(
                                                    context,
                                                    "Invitation sent",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                invitationEmail = ""
                                                sharingListId = null
                                            } catch (error: Exception) {
                                                Log.e(
                                                    "JSimpleList",
                                                    "Could not send invitation",
                                                    error
                                                )

                                                Toast.makeText(
                                                    context,
                                                    "Could not send invitation",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                sendingInvitation = false
                                            }
                                        }
                                    },
                                    enabled =
                                        !sendingInvitation &&
                                        invitationEmail.trim().isNotEmpty()
                                ) {
                                    Text(
                                        if (sendingInvitation) {
                                            "Sending"
                                        } else {
                                            "Send invitation"
                                        }
                                    )
                                }
                            } else {
                                Text(
                                    "This list is available wherever " +
                                        "$signedInEmail is signed in"
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Invite someone",
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = invitationEmail,
                                    onValueChange = {
                                        invitationEmail = it
                                    },
                                    label = {
                                        Text("Email address")
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Done
                                    ),
                                    enabled = !sendingInvitation,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            sendingInvitation = true

                                            try {
                                                invitationRepository.sendInvitation(
                                                    listId = sharingList.id,
                                                    email = invitationEmail
                                                )

                                                Toast.makeText(
                                                    context,
                                                    "Invitation sent",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                invitationEmail = ""
                                                sharingListId = null
                                            } catch (error: Exception) {
                                                Log.e(
                                                    "JSimpleList",
                                                    "Could not send invitation",
                                                    error
                                                )

                                                Toast.makeText(
                                                    context,
                                                    "Could not send invitation",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } finally {
                                                sendingInvitation = false
                                            }
                                        }
                                    },
                                    enabled =
                                        !sendingInvitation &&
                                        invitationEmail.trim().isNotEmpty()
                                ) {
                                    Text(
                                        if (sendingInvitation) {
                                            "Sending"
                                        } else {
                                            "Send invitation"
                                        }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                sharingListId = null
                                invitationEmail = ""
                            },
                            enabled =
                                makingOnlineListId == null &&
                                !sendingInvitation
                        ) {
                            Text("Close")
                        }
                    }
                )
            }
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
                                createdAt = now,
                                updatedAt = now
                            )

                            coroutineScope.launch {
                                dao.insertList(list)

                                lists.add(list)
                                itemsByList[list.id] =
                                    mutableStateListOf<ItemEntity>()

                                showNewListDialog = false

                                pagerState.requestScrollToPage(
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
            val isLeavingList =
                deleteList.onlineState == "ONLINE_MEMBER"

            AlertDialog(
                onDismissRequest = {
                    deleteListId = null
                },
                title = {
                    Text(
                        if (isLeavingList) {
                            "Leave list"
                        } else {
                            "Delete list"
                        }
                    )
                },
                text = {
                    Text(
                        if (isLeavingList) {
                            "Leave \"${deleteList.name}\"?"
                        } else {
                            "Delete \"${deleteList.name}\" and all its items?"
                        }
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
                                try {
                                    when (deleteList.onlineState) {
                                        "LOCAL" -> Unit

                                        "ONLINE_OWNER" -> {
                                            listSyncRepository
                                                .deleteOnlineList(
                                                    listId =
                                                        deleteList.id,
                                                    originClientId =
                                                        clientInstanceId
                                                )
                                        }

                                        "ONLINE_MEMBER" -> {
                                            listSyncRepository
                                                .leaveOnlineList(
                                                    listId =
                                                        deleteList.id
                                                )
                                        }

                                        else -> {
                                            error(
                                                "Unknown list state: " +
                                                    deleteList.onlineState
                                            )
                                        }
                                    }

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

                                        pagerState.scrollToPage(
                                            targetIndex
                                        )
                                    }
                                } catch (exception: Exception) {
                                    Log.e(
                                        "JSimpleListSync",
                                        "List removal failed for " +
                                            "id=${deleteList.id} " +
                                            "state=${deleteList.onlineState}",
                                        exception
                                    )

                                    Toast.makeText(
                                        context,
                                        exception.message
                                            ?: if (isLeavingList) {
                                                "Could not leave list"
                                            } else {
                                                "Could not delete list"
                                            },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text(
                            if (isLeavingList) {
                                "Leave list"
                            } else {
                                "Delete"
                            }
                        )
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
                    modifier = Modifier.weight(1f)
                ) {
                    if (showListSelector) {
                        Text(
                            text = "Manage lists",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                if (currentList.onlineState != "LOCAL") {
                                    Modifier.clickable {
                                        openSharedListInfo(currentList.id)
                                    }
                                } else {
                                    Modifier
                                }
                        ) {
                            Text(
                                text = currentList.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (currentList.onlineState != "LOCAL") {
                                Spacer(modifier = Modifier.width(5.dp))

                                Icon(
                                    painter = painterResource(
                                        R.drawable.ic_online_list
                                    ),
                                    contentDescription = "Online list",
                                    tint =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .width(15.dp)
                                        .height(15.dp)
                                )
                            }
                        }
                    }

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

                if (!showListSelector) {
                    TextButton(
                        onClick = {
                            sharingListId = currentList.id
                        }
                    ) {
                        Text("Share list")
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = list.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    if (list.onlineState != "LOCAL") {
                                        Spacer(
                                            modifier = Modifier.width(5.dp)
                                        )

                                        Icon(
                                            painter = painterResource(
                                                R.drawable.ic_online_list
                                            ),
                                            contentDescription = "Online list",
                                            tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .width(15.dp)
                                                .height(15.dp)
                                        )
                                    }
                                }

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
                                Text(
                                    if (
                                        list.onlineState ==
                                        "ONLINE_MEMBER"
                                    ) {
                                        "Leave list"
                                    } else {
                                        "Delete"
                                    }
                                )
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
                            onlineState = list.onlineState,
                            currentUserId = authRepository.currentUserId(),
                            profileRepository = profileRepository,
                            items = items,
                            hasCompletedItems = hasCompletedItems,
                            onUncheckAll = uncheckAllItems,
                            isRefreshing =
                                refreshingListId == list.id,
                            onRefresh = {
                                if (refreshingListId == null) {
                                    coroutineScope.launch {
                                        refreshingListId = list.id

                                        try {
                                            if (
                                                authRepository
                                                    .currentUserId() != null
                                            ) {
                                                listSyncRepository
                                                    .discoverOnlineLists(
                                                        dao = dao
                                                    )

                                                val loadedLists =
                                                    dao.loadLists()

                                                lists.clear()
                                                itemsByList.clear()

                                                loadedLists.forEach {
                                                    loadedList ->
                                                    lists.add(loadedList)
                                                    itemsByList[
                                                        loadedList.id
                                                    ] =
                                                        mutableStateListOf<
                                                            ItemEntity
                                                        >().apply {
                                                            addAll(
                                                                dao.loadItems(
                                                                    loadedList.id
                                                                )
                                                            )
                                                        }
                                                }

                                                val refreshedIndex =
                                                    lists.indexOfFirst {
                                                        it.id == list.id
                                                    }

                                                if (
                                                    refreshedIndex >= 0 &&
                                                    lists.isNotEmpty()
                                                ) {
                                                    pagerState.scrollToPage(
                                                        refreshedIndex
                                                    )
                                                }
                                            }
                                        } catch (
                                            exception: Exception
                                        ) {
                                            Log.e(
                                                "JSimpleListSync",
                                                "Pull refresh failed",
                                                exception
                                            )

                                            Toast.makeText(
                                                context,
                                                exception.message
                                                    ?: "Could not refresh",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } finally {
                                            refreshingListId = null
                                        }
                                    }
                                }
                            },
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
                                                deletedAt = now,
                                                updatedByUserId =
                                                    authRepository.currentUserId()
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
    onlineState: String,
    currentUserId: String?,
    profileRepository: ProfileRepository,
    items: MutableList<ItemEntity>,
    hasCompletedItems: Boolean,
    onUncheckAll: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onItemAdded: (ItemEntity) -> Unit,
    onItemUpdated: (ItemEntity) -> Unit,
    onItemDeleted: (ItemEntity) -> Unit
) {
    var creatorNames by remember {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    val creatorUserIds =
        if (onlineState != "LOCAL") {
            items
                .mapNotNull { it.createdByUserId }
                .toSet()
        } else {
            emptySet()
        }

    LaunchedEffect(creatorUserIds) {
        creatorNames =
            if (creatorUserIds.isEmpty()) {
                emptyMap()
            } else {
                try {
                    profileRepository.loadProfiles(creatorUserIds)
                } catch (exception: Exception) {
                    Log.e(
                        "JSimpleListProfile",
                        "Could not load item creator profiles",
                        exception
                    )
                    emptyMap()
                }
            }
    }
    var description by remember(kind) { mutableStateOf("") }
    var quantityText by remember(kind) { mutableStateOf("1") }
    var pendingScrollItemId by remember(listId) {
        mutableStateOf<String?>(null)
    }

    val visualCompletedOverrides = remember(listId) {
        mutableStateMapOf<String, Boolean>()
    }
    val visualToggleGenerations = remember(listId) {
        mutableStateMapOf<String, Int>()
    }
    val itemAnimationScope = rememberCoroutineScope()

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
        val attributionUserId =
            if (onlineState != "LOCAL") {
                currentUserId
            } else {
                null
            }

        val item = ItemEntity(
            id = UUID.randomUUID().toString(),
            listId = listId,
            description = trimmedDescription,
            quantity = quantity,
            completed = false,
            position = (items.minOfOrNull { it.position } ?: 10) - 10,
            createdAt = now,
            updatedAt = now,
            createdByUserId = attributionUserId,
            updatedByUserId = attributionUserId
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
            remember(
                items.toList(),
                visualCompletedOverrides.toMap()
            ) {
                val orderedItems =
                    items.sortedWith(
                        compareBy<ItemEntity> { it.position }
                            .thenBy { it.createdAt }
                    )

                orderedItems.filterNot { item ->
                    visualCompletedOverrides[item.id]
                        ?: item.completed
                } +
                    orderedItems.filter { item ->
                        visualCompletedOverrides[item.id]
                            ?: item.completed
                    }
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

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
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
                    Column(
                        modifier = Modifier.animateItem()
                    ) {
                        ListItemRow(
                            item = item,
                            kind = kind,
                            creatorName =
                                if (onlineState != "LOCAL") {
                                    item.createdByUserId?.let {
                                        creatorNames[it]
                                    }
                                } else {
                                    null
                                },
                            fontScale = fontScale,
                            onUpdate = { newDescription, newQuantity ->
                                val index =
                                    items.indexOfFirst {
                                        it.id == item.id
                                    }

                                if (index >= 0) {
                                    val updatedItem = item.copy(
                                        description = newDescription,
                                        quantity = newQuantity,
                                        updatedAt = System.currentTimeMillis(),
                                        updatedByUserId =
                                            if (onlineState != "LOCAL") {
                                                currentUserId
                                            } else {
                                                null
                                            }
                                    )

                                    items[index] = updatedItem
                                    onItemUpdated(updatedItem)
                                }
                            },
                            onToggle = {
                                val index =
                                    items.indexOfFirst {
                                        it.id == item.id
                                    }

                                if (index >= 0) {
                                    val oldCompleted =
                                        visualCompletedOverrides[item.id]
                                            ?: item.completed
                                    val updatedItem = item.copy(
                                        completed = !item.completed,
                                        updatedAt = System.currentTimeMillis(),
                                        updatedByUserId =
                                            if (onlineState != "LOCAL") {
                                                currentUserId
                                            } else {
                                                null
                                            }
                                    )

                                    visualCompletedOverrides[item.id] =
                                        oldCompleted

                                    val generation =
                                        (visualToggleGenerations[item.id] ?: 0) + 1

                                    visualToggleGenerations[item.id] =
                                        generation

                                    items[index] = updatedItem
                                    onItemUpdated(updatedItem)

                                    itemAnimationScope.launch {
                                        delay(250)

                                        if (
                                            visualToggleGenerations[item.id] ==
                                            generation
                                        ) {
                                            visualCompletedOverrides.remove(
                                                item.id
                                            )
                                            visualToggleGenerations.remove(
                                                item.id
                                            )
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                visualCompletedOverrides.remove(item.id)
                                visualToggleGenerations.remove(item.id)
                                items.removeAll { it.id == item.id }
                                onItemDeleted(item)
                            }
                        )

                        HorizontalDivider()
                    }
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
    creatorName: String?,
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

    val editTextSize = (16f * fontScale).coerceAtMost(22f)
    val editFieldHeight = (
        56f + ((editTextSize - 16f) * 2f)
    ).dp
    val editQuantityWidth = (
        48f + ((editTextSize - 16f) * 1.2f)
    ).dp

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
                            fontSize = editTextSize.sp
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .width(editQuantityWidth)
                            .heightIn(min = editFieldHeight)
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
                        fontSize = editTextSize.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { saveEdit() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = editFieldHeight)
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

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        .clickable {
                            editDescription = TextFieldValue(
                                text = item.description,
                                selection = TextRange(item.description.length)
                            )
                            editing = true
                        }
                )

                if (creatorName != null) {
                    Text(
                        text = creatorName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

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
