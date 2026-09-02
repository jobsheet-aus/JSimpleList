package au.com.jobsheet.jsimplelist

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JSimpleListFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onRegistered(
        installationId: String
    ) {
        super.onRegistered(installationId)

        val cleanedInstallationId =
            installationId.trim()

        if (cleanedInstallationId.isEmpty()) {
            return
        }

        val store =
            SimpleListStore(applicationContext)

        store.saveFirebaseInstallationId(
            cleanedInstallationId
        )

        val authRepository = AuthRepository()

        if (authRepository.currentUserId() == null) {
            return
        }

        val clientInstanceId =
            store.loadOrCreateClientInstanceId()

        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        ).launch {
            try {
                PushDeviceRepository().register(
                    clientInstanceId = clientInstanceId,
                    firebaseInstallationId =
                        cleanedInstallationId
                )
            } catch (exception: Exception) {
                Log.w(
                    "JSimpleListPush",
                    "FID registration failed after Firebase registration",
                    exception
                )
            }
        }
    }

    override fun onUnregistered(
        installationId: String
    ) {
        super.onUnregistered(installationId)

        SimpleListStore(applicationContext)
            .clearFirebaseInstallationId()
    }

    override fun onMessageReceived(
        message: RemoteMessage
    ) {
        super.onMessageReceived(message)

        val data = message.data

        val recipientUserId =
            data["recipient_user_id"]
                ?.trim()
                .orEmpty()

        if (recipientUserId.isEmpty()) {
            Log.w(
                "JSimpleListPush",
                "Ignoring push without recipient account"
            )
            return
        }

        val activeUserId =
            SimpleListStore(applicationContext)
                .loadActivePushUserId()

        if (activeUserId != recipientUserId) {
            Log.i(
                "JSimpleListPush",
                "Ignoring push for inactive account"
            )
            return
        }

        when (data["event_type"]) {
            "list_invitation" ->
                handleInvitationPush(
                    data = data,
                    recipientUserId = recipientUserId
                )

            "invitation_accepted" ->
                handleInvitationAcceptedPush(
                    data = data,
                    recipientUserId = recipientUserId
                )

            "invitation_declined" ->
                handleInvitationDeclinedPush(
                    data = data,
                    recipientUserId = recipientUserId
                )

            else ->
                Log.i(
                    "JSimpleListPush",
                    "Ignoring unsupported push event"
                )
        }
    }

    private fun handleInvitationPush(
        data: Map<String, String>,
        recipientUserId: String
    ) {
        val invitationId =
            data["invitation_id"]
                ?.trim()
                .orEmpty()

        val listId =
            data["list_id"]
                ?.trim()
                .orEmpty()

        val listName =
            data["list_name"]
                ?.trim()
                .orEmpty()

        val actorDisplayName =
            data["actor_display_name"]
                ?.trim()
                .orEmpty()

        if (
            invitationId.isEmpty() ||
            listId.isEmpty() ||
            listName.isEmpty() ||
            actorDisplayName.isEmpty()
        ) {
            Log.w(
                "JSimpleListPush",
                "Ignoring incomplete invitation push"
            )
            return
        }

        Log.i(
            "JSimpleListPush",
            "Invitation push received for active account"
        )

        SharingNotificationManager(applicationContext)
            .showInvitation(
                invitationId = invitationId,
                recipientUserId = recipientUserId,
                listId = listId,
                listName = listName,
                actorDisplayName = actorDisplayName
            )
    }

    private fun handleInvitationAcceptedPush(
        data: Map<String, String>,
        recipientUserId: String
    ) {
        val notificationId =
            data["notification_id"]
                ?.trim()
                .orEmpty()

        val listId =
            data["list_id"]
                ?.trim()
                .orEmpty()

        val listName =
            data["list_name"]
                ?.trim()
                .orEmpty()

        val actorDisplayName =
            data["actor_display_name"]
                ?.trim()
                .orEmpty()

        if (
            notificationId.isEmpty() ||
            listId.isEmpty() ||
            listName.isEmpty() ||
            actorDisplayName.isEmpty()
        ) {
            Log.w(
                "JSimpleListPush",
                "Ignoring incomplete acceptance push"
            )
            return
        }

        Log.i(
            "JSimpleListPush",
            "Acceptance push received for active account"
        )

        SharingNotificationManager(applicationContext)
            .showJoined(
                notificationId = notificationId,
                recipientUserId = recipientUserId,
                listId = listId,
                listName = listName,
                actorDisplayName = actorDisplayName
            )
    }

    private fun handleInvitationDeclinedPush(
        data: Map<String, String>,
        recipientUserId: String
    ) {
        val notificationId =
            data["notification_id"]
                ?.trim()
                .orEmpty()

        val listId =
            data["list_id"]
                ?.trim()
                .orEmpty()

        val listName =
            data["list_name"]
                ?.trim()
                .orEmpty()

        val actorDisplayName =
            data["actor_display_name"]
                ?.trim()
                .orEmpty()

        if (
            notificationId.isEmpty() ||
            listId.isEmpty() ||
            listName.isEmpty() ||
            actorDisplayName.isEmpty()
        ) {
            Log.w(
                "JSimpleListPush",
                "Ignoring incomplete decline push"
            )
            return
        }

        Log.i(
            "JSimpleListPush",
            "Decline push received for active account"
        )

        SharingNotificationManager(applicationContext)
            .showDeclined(
                notificationId = notificationId,
                recipientUserId = recipientUserId,
                listId = listId,
                listName = listName,
                actorDisplayName = actorDisplayName
            )
    }
}
