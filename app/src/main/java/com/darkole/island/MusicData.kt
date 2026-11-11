package com.darkole.island

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.ActivityOptions
import android.os.Build

data class TrackInfo(
    val title: String = "Нет воспроизведения",
    val artist: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false
)

object MusicData {

    private const val TAG = "DarkoleIsland"

    var currentTrack by mutableStateOf(TrackInfo())
        private set

    var activePackageName by mutableStateOf<String?>(null)
        private set

    private var activeController: MediaController? = null

    fun update(track: TrackInfo) {
        currentTrack = track
    }

    fun setController(controller: MediaController?) {
        activeController = controller
        activePackageName = controller?.packageName
    }

    fun openMusicApp(context: Context) {
        val packageName = activePackageName ?: return
        openApp(context, packageName)
    }

    fun openApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "Открыто приложение: $packageName")
            } else {
                Log.e(TAG, "Не найден LaunchIntent для: $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия приложения: $packageName", e)
        }
    }


    fun openNotificationTarget(
        context: Context,
        notification: IslandNotification?
    ) {
        if (notification == null) return

        val packageName = notification.packageName
        val pendingIntent = notification.contentIntent

        Log.d(
            TAG,
            "Открытие уведомления: package=$packageName, " +
                    "hasPendingIntent=${pendingIntent != null}"
        )

        if (pendingIntent == null) {
            openApp(context, packageName)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()

                pendingIntent.send(
                    context,
                    0,
                    null,
                    null,
                    null,
                    null,
                    options
                )
            } else {
                pendingIntent.send()
            }
            Log.d(TAG, "PendingIntent отправлен: $packageName")

        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "PendingIntent отменён", e)
            openApp(context, packageName)

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия уведомления", e)
            openApp(context, packageName)
        }
    }


    fun previousTrack() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun nextTrack() {
        activeController?.transportControls?.skipToNext()
    }

    fun togglePlayback() {
        val controller = activeController ?: return

        if (controller.playbackState?.state ==
            PlaybackState.STATE_PLAYING
        ) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun clear() {
        activeController = null
        activePackageName = null
        currentTrack = TrackInfo()
    }
}
