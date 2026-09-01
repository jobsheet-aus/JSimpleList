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
        listName: String,
        actorDisplayName: String
    ) {
        showMessage(
            notificationId = invitationId,
            message =
                "$actorDisplayName invited you to join $listName"
        )
    }

    fun showJoined(
        notificationId: String,
        listName: String,
        actorDisplayName: String
    ) {
        showMessage(
            notificationId = notificationId,
            message =
                "$actorDisplayName joined $listName"
        )
    }

    fun showDeclined(
        notificationId: String,
        listName: String,
        actorDisplayName: String
    ) {
        showMessage(
            notificationId = notificationId,
            message =
                "$actorDisplayName declined your invitation to $listName"
        )
    }


    private fun showMessage(
        notificationId: String,
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
