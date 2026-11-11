
package com.darkole.island

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class IslandNotification(
    val key: String = "",
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val appIcon: Bitmap? = null,
    val contentImage: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val senderAvatar: Bitmap? = null
)

object NotificationData {

    var notifications by mutableStateOf<List<IslandNotification>>(emptyList())
        private set

    val currentNotification: IslandNotification?
        get() = notifications.maxByOrNull { it.timestamp }

    fun update(notification: IslandNotification) {
        notifications = notifications
            .filterNot { it.key == notification.key }
            .plus(notification)
    }

    fun remove(key: String) {
        notifications = notifications.filterNot { it.key == key }
    }

    fun clear() {
        notifications = emptyList()
    }
}
