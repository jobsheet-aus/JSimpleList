package au.com.jobsheet.jsimplelist

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class SharingNotificationManager(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "sharing_updates"
        const val CHANNEL_NAME = "Sharing updates"

        const val EXTRA_EVENT_TYPE =
            "jsimplelist_sharing_event_type"

        const val EXTRA_RECIPIENT_USER_ID =
            "jsimplelist_sharing_recipient_user_id"

        const val EXTRA_LIST_ID =
            "jsimplelist_sharing_list_id"

        const val EXTRA_INVITATION_ID =
            "jsimplelist_sharing_invitation_id"

        const val EVENT_LIST_INVITATION =
            "list_invitation"

        const val EVENT_INVITATION_ACCEPTED =
            "invitation_accepted"

        const val EVENT_INVITATION_DECLINED =
            "invitation_declined"
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager =
            context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                "Updates when people join or change your shared lists"
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun showInvitation(
        invitationId: String,
        recipientUserId: String,
        listId: String,
        listName: String,
        actorDisplayName: String
    ) {
        showMessage(
            notificationId = invitationId,
            eventType = EVENT_LIST_INVITATION,
            recipientUserId = recipientUserId,
            listId = listId,
            invitationId = invitationId,
            message =
                "$actorDisplayName invited you to join $listName"
        )
    }

    fun showJoined(
        notificationId: String,
        recipientUserId: String,
        listId: String,
        listName: String,
        actorDisplayName: String
    ) {
        showMessage(
            notificationId = notificationId,
            eventType = EVENT_INVITATION_ACCEPTED,
            recipientUserId = recipientUserId,
            listId = listId,
            message =
                "$actorDisplayName joined $listName"
        )
    }

    fun showDeclined(
        notificationId: String,
        recipientUserId: String,
        listId: String,
        listName: String,
        actorDisplayName: String
    ) {
        showMessage(
            notificationId = notificationId,
            eventType = EVENT_INVITATION_DECLINED,
            recipientUserId = recipientUserId,
            listId = listId,
            message =
                "$actorDisplayName declined your invitation to $listName"
        )
    }

    private fun showMessage(
        notificationId: String,
        eventType: String,
        recipientUserId: String,
        listId: String,
        invitationId: String? = null,
        message: String
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(
                    EXTRA_EVENT_TYPE,
                    eventType
                )

                putExtra(
                    EXTRA_RECIPIENT_USER_ID,
                    recipientUserId
                )

                putExtra(
                    EXTRA_LIST_ID,
                    listId
                )

                if (invitationId != null) {
                    putExtra(
                        EXTRA_INVITATION_ID,
                        invitationId
                    )
                }
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("JSimpleList")
                .setContentText(message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

        NotificationManagerCompat
            .from(context)
            .notify(
                notificationId.hashCode(),
                builder.build()
            )
    }
}
