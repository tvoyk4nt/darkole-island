
package com.darkole.island

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

class MusicNotificationListener : NotificationListenerService() {

    private val componentName: ComponentName
        get() = ComponentName(this, MusicNotificationListener::class.java)

    private var mediaSessionManager: MediaSessionManager? = null

    private val controllers = mutableListOf<MediaController>()

    private val sessionListener = MediaSessionManagerListener()

    private val controllerCallback = object : MediaController.Callback() {

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateCurrentTrack()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateCurrentTrack()
        }

        override fun onSessionDestroyed() {
            refreshSessions()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        val manager = getSystemService(MEDIA_SESSION_SERVICE)
                as MediaSessionManager

        mediaSessionManager = manager

        manager.addOnActiveSessionsChangedListener(
            sessionListener,
            componentName
        )

        refreshSessions()
    }

    private fun refreshSessions() {
        val manager = mediaSessionManager ?: return

        val activeSessions = manager.getActiveSessions(componentName)

        controllers.forEach {
            it.unregisterCallback(controllerCallback)
        }

        controllers.clear()

        activeSessions.forEach {
            it.registerCallback(controllerCallback)
            controllers.add(it)
        }

        updateCurrentTrack()
    }

    private fun extractSenderAvatar(extras: Bundle): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }

        return try {
            val messages = extras.getParcelableArray("android.messages")

            val messagePerson = messages
                ?.reversedArray()
                ?.firstNotNullOfOrNull { item ->
                    val bundle = item as? Bundle
                    bundle?.getParcelable("sender_person") as? Person
                }

            val notificationPerson =
                extras.getParcelable("android.messagingUser") as? Person

            val person = messagePerson ?: notificationPerson
            val icon = person?.icon ?: return null

            icon.loadDrawable(this)?.toBitmap(
                width = 96,
                height = 96,
                config = Bitmap.Config.ARGB_8888
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun updateCurrentTrack() {
        val controller = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull {
            it.metadata != null
        }

        if (controller == null) {
            MusicData.clear()
            return
        }

        MusicData.setController(controller)

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(
            MediaMetadata.METADATA_KEY_TITLE
        ) ?: "Неизвестная композиция"

        val artist = metadata?.getString(
            MediaMetadata.METADATA_KEY_ARTIST
        ) ?: "Неизвестный исполнитель"

        val artwork =
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        MusicData.update(
            TrackInfo(
                title = title,
                artist = artist,
                artwork = artwork,
                isPlaying = playbackState?.state ==
                        PlaybackState.STATE_PLAYING
            )
        )
    }

    private inner class MediaSessionManagerListener :
        MediaSessionManager.OnActiveSessionsChangedListener {

        override fun onActiveSessionsChanged(
            sessions: MutableList<MediaController>?
        ) {
            refreshSessions()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val debugExtras = sbn.notification.extras

        android.util.Log.e(
            "NotificationDebug",
            """
            ===== NOTIFICATION =====
            PACKAGE: ${sbn.packageName}
            TITLE: ${debugExtras.getCharSequence(Notification.EXTRA_TITLE)}
            TEXT: ${debugExtras.getCharSequence(Notification.EXTRA_TEXT)}
            BIG_TEXT: ${debugExtras.getCharSequence(Notification.EXTRA_BIG_TEXT)}
            SUB_TEXT: ${debugExtras.getCharSequence(Notification.EXTRA_SUB_TEXT)}
            TEXT_LINES: ${debugExtras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString()}
            MESSAGES: ${debugExtras.getParcelableArray("android.messages")?.contentToString()}
            CONTENT_INTENT: ${sbn.notification.contentIntent != null}
            KEYS: ${debugExtras.keySet().joinToString()}
            ========================
            """.trimIndent()
        )

        if (sbn.isOngoing || sbn.packageName == packageName) {
            return
        }

        val extras = sbn.notification.extras

        // Диагностика содержимого сообщений для VK, MAX и Telegram.
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)

        android.util.Log.e(
            "NotificationDebug",
            "MESSAGES COUNT: ${messages?.size ?: 0}"
        )

        messages?.forEachIndexed { index, item ->
            val bundle = item as? Bundle ?: return@forEachIndexed
            val person = bundle.getParcelable("sender_person") as? Person

            android.util.Log.e(
                "NotificationDebug",
                """
                MESSAGE $index
                KEYS: ${bundle.keySet()}
                SENDER: ${bundle.getCharSequence("sender")}
                SENDER_PERSON: ${person?.name}
                TEXT: ${bundle.getCharSequence("text")}
                TIME: ${bundle.getLong("time", -1L)}
                """.trimIndent()
            )
        }

        // Игнорируем уведомления управления музыкой.
        if (
            extras.containsKey("android.mediaSession") ||
            sbn.notification.category == Notification.CATEGORY_TRANSPORT
        ) {
            return
        }

// VK: игнорируем общее уведомление,
// оставляем только индивидуальные сообщения с android.messages.
        if (
            sbn.packageName == "com.vkontakte.android" &&
            messages.isNullOrEmpty() &&
            extras.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString() == "Сообщения"
        ) {
            return
        }

// Получаем последнее сообщение из MessagingStyle.
        val latestMessage = extractLatestMessage(extras)

        // Извлекаем отправителя.
        val messagePerson = latestMessage
            ?.getParcelable("sender_person") as? Person

        val senderName = messagePerson?.name?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: latestMessage
                ?.getCharSequence("sender")
                ?.toString()
                ?.takeIf { it.isNotBlank() }

        // Извлекаем текст сообщения.
        val messageText = latestMessage
            ?.getCharSequence("text")
            ?.toString()
            ?.takeIf { it.isNotBlank() }

        // Заголовок уведомления.
        val notificationTitle = extras.getCharSequence(
            Notification.EXTRA_TITLE
        )?.toString().orEmpty()

        // Не показываем общее уведомление Telegram.
        if (
            sbn.packageName == "org.telegram.messenger" &&
            notificationTitle == "Telegram"
        ) {
            return
        }

        // Приоритет отдаём имени отправителя.
        val title = senderName ?: notificationTitle

        // Приоритет отдаём тексту последнего сообщения.
        val text = messageText
            ?: extras.getCharSequence(
                Notification.EXTRA_BIG_TEXT
            )?.toString()
            ?: extras.getCharSequence(
                Notification.EXTRA_TEXT
            )?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return

        if (
            sbn.packageName == "org.telegram.messenger" &&
            title == "Telegram"
        ) {
            return
        }

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(
                    sbn.packageName,
                    0
                )
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        // Получаем иконку приложения.
        val appIcon = try {
            packageManager.getApplicationIcon(sbn.packageName)
                .toBitmap(
                    width = 96,
                    height = 96,
                    config = Bitmap.Config.ARGB_8888
                )
        } catch (e: Exception) {
            null
        }

        // Получаем изображение из уведомления.
        val contentImage =
            (extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap)
                ?: (extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG) as? Bitmap)
                ?: (extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)

        val senderAvatar = extractSenderAvatar(extras)

        // Время последнего сообщения Telegram.
        val messageTime = if (
            sbn.packageName == "org.telegram.messenger"
        ) {
            extras.getParcelableArray("android.messages")
                ?.mapNotNull { item ->
                    (item as? Bundle)
                        ?.getLong("time")
                        ?.takeIf { it > 0L }
                }
                ?.maxOrNull() ?: sbn.postTime
        } else {
            sbn.postTime
        }

        // Намерение для открытия уведомления.
        val contentIntent = sbn.notification.contentIntent

        NotificationData.update(
            IslandNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = messageTime,
                appIcon = appIcon,
                contentImage = contentImage,
                contentIntent = contentIntent,
                senderAvatar = senderAvatar
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        NotificationData.remove(sbn.key)
    }

    private fun releaseSessions() {
        controllers.forEach {
            it.unregisterCallback(controllerCallback)
        }

        controllers.clear()

        mediaSessionManager?.removeOnActiveSessionsChangedListener(
            sessionListener
        )

        mediaSessionManager = null
        MusicData.clear()
    }


    private fun extractLatestMessage(extras: Bundle): Bundle? {
        val messages = extras.getParcelableArray("android.messages")
            ?: return null

        return messages
            .mapNotNull { it as? Bundle }
            .lastOrNull { bundle ->
                val text = bundle.getCharSequence("text")
                    ?.toString()
                    ?.isNotBlank() == true

                val hasImage = bundle.containsKey("type") ||
                        bundle.containsKey("uri")

                text || hasImage
            }
            ?: messages.mapNotNull { it as? Bundle }.lastOrNull()
    }


    override fun onListenerDisconnected() {
        releaseSessions()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        releaseSessions()
        super.onDestroy()
    }
}
