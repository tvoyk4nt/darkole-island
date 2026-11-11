
package com.darkole.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.animateFloatAsState

private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

class IslandOverlayManager(private val context: Context) {

    private var overlayContext: Context = context

    private var windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    companion object {
        private var activeManager: IslandOverlayManager? = null

        fun updatePosition() {
            activeManager?.applyPosition()
        }

        fun onAccessibilityServiceChanged(
            service: IslandAccessibilityService?
        ) {
            activeManager?.switchOverlayContext(service)
        }

        fun setExpanded(expanded: Boolean) {
            activeManager?.updateWindowSize(expanded)
        }

        fun passTapThrough(x: Float, y: Float) {
            activeManager?.sendTapToUnderlyingApp(x, y)
        }
    }

    init {
        activeManager = this
        switchOverlayContext(IslandAccessibilityService.instance)
    }

    private fun updateWindowSize(expanded: Boolean) {
        val params = overlayParams ?: return
        val view = overlayView ?: return

        params.width = if (expanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        params.height = if (expanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendTapToUnderlyingApp(x: Float, y: Float) {
        val service = IslandAccessibilityService.instance ?: return
        val params = overlayParams ?: return
        val view = overlayView ?: return

        // Уменьшаем окно и временно отключаем перехват касаний.
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        service.dispatchTap(x, y) {
            // Возвращаем возможность взаимодействия с островом.
            params.flags = params.flags and
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()

            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun switchOverlayContext(
        service: IslandAccessibilityService?
    ) {
        val wasVisible = overlayView != null
        if (wasVisible) hide()

        overlayContext = service ?: context

        windowManager =
            overlayContext.getSystemService(Context.WINDOW_SERVICE)
                    as WindowManager

        if (wasVisible) show()
    }

    private fun applyPosition() {
        val params = overlayParams ?: return
        val view = overlayView ?: return

        val density = overlayContext.resources.displayMetrics.density
        params.y = 0

        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun show() {
        if (overlayView != null) return

        val owner = OverlayLifecycleOwner()
        owner.start()

        val view = ComposeView(overlayContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { FloatingIsland() }
        }

        val windowType =
            if (overlayContext is IslandAccessibilityService) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            overlayParams = params
            lifecycleOwner = owner
        } catch (e: Exception) {
            owner.stop()
            throw e
        }
    }

    fun hide() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            overlayView = null
            overlayParams = null
            lifecycleOwner?.stop()
            lifecycleOwner = null
        }
    }
}

private val AccentPurple = Color(0xFFB58AFF)
private val SoftWhite = Color(0xFFF5F3FA)

@Composable
private fun FloatingIsland() {
    var playerExpanded by remember { mutableStateOf(false) }
    var notificationExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Отслеживаем выключение экрана
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    playerExpanded = false
                    notificationExpanded = false

                    // Возвращаем окну компактный размер
                    IslandOverlayManager.setExpanded(false)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val isExpanded = playerExpanded || notificationExpanded

    LaunchedEffect(isExpanded) {
        IslandOverlayManager.setExpanded(isExpanded)
    }

    val track = MusicData.currentTrack
    val notification = NotificationData.currentNotification
    val config = IslandSettings.config

    val islandBorderColor = when (config.borderColorMode) {
        IslandBorderColorMode.COVER -> extractCoverColor(track.artwork)
        IslandBorderColorMode.NONE -> Color.Transparent
        else -> config.borderColorMode.color ?: Color(0xFF33333D)
    }

    val islandBorderModifier: (RoundedCornerShape) -> Modifier = { shape ->
        if (
            config.borderWidth > 0f &&
            config.borderColorMode != IslandBorderColorMode.NONE
        ) {
            Modifier.border(
                width = config.borderWidth.dp,
                color = islandBorderColor,
                shape = shape
            )
        } else {
            Modifier
        }
    }

    val hasMusic =
        track.title.isNotBlank() &&
                track.title != "Нет воспроизведения"

    LaunchedEffect(hasMusic) {
        if (!hasMusic) {
            playerExpanded = false
        }
    }

    LaunchedEffect(notification?.key) {
        notificationExpanded = false
    }

    LaunchedEffect(playerExpanded) {
        if (playerExpanded) {
            notificationExpanded = false
        }
    }

    val screenWidth =
        LocalConfiguration.current.screenWidthDp.dp

    val playerTransition = updateTransition(
        targetState = playerExpanded,
        label = "Player transition"
    )

    val playerWidth by playerTransition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = 0.72f,
                stiffness = 380f
            )
        },
        label = "Player width"
    ) {
        if (it) config.expandedWidth.dp
        else config.collapsedWidth.dp
    }

    val playerCornerRadius by playerTransition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = 0.72f,
                stiffness = 380f
            )
        },
        label = "Player corner radius"
    ) {
        if (it) 28.dp
        else config.cornerRadius.dp
    }

    val bubbleSize = config.collapsedHeight.dp
    val gap = 8.dp

    val notificationCardWidth = config.expandedWidth.dp
        .coerceAtMost(screenWidth - 16.dp)



    Box(
        modifier = if (isExpanded) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .width(screenWidth)
                .wrapContentHeight()
        }
    ) {
        // Прозрачный слой для нажатий вне острова
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isExpanded) {
                        detectTapGestures { offset ->
                            playerExpanded = false
                            notificationExpanded = false
                            IslandOverlayManager.passTapThrough(
                                offset.x,
                                offset.y
                            )
                        }
                    }
            )
        }

        if (hasMusic || notification != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(screenWidth)
                    .wrapContentHeight()
                    .padding(top = config.topOffset.dp),
                contentAlignment = Alignment.Center
            ) {

            // МУЗЫКАЛЬНЫЙ ПЛЕЕР

            if (hasMusic) {
                val playerShape =
                    RoundedCornerShape(playerCornerRadius)

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(playerWidth)
                        .height(
                            if (playerExpanded) {
                                config.expandedHeight.dp
                            } else {
                                config.collapsedHeight.dp
                            }
                        )
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = 380f
                            )
                        )
                        .clip(playerShape)
                        .background(
                            if (playerExpanded) Color(0xFF101016)
                            else Color.Black
                        )
                        .then(islandBorderModifier(playerShape))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    playerExpanded = !playerExpanded
                                    notificationExpanded = false
                                },
                                onLongPress = {
                                    MusicData.openMusicApp(context)
                                }
                            )
                        }
                ) {
                    if (playerExpanded) {
                        ArtworkWaveBackground(
                            artwork = track.artwork,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = if (playerExpanded) 16.dp else 12.dp,
                                vertical = if (playerExpanded) 13.dp else 0.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // СВЁРНУТЫЙ ПЛЕЕР

                        if (!playerExpanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(config.collapsedHeight.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val coverSize =
                                    (config.collapsedHeight - 12f)
                                        .coerceAtLeast(20f).dp

                                if (track.artwork != null) {
                                    RotatingVinyl(
                                        artwork = track.artwork,
                                        size = coverSize,
                                        isPlaying = track.isPlaying
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(coverSize)
                                            .clip(CircleShape)
                                            .background(Color(0xFF292936)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "♫",
                                            color = SoftWhite,
                                            fontSize = 19.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.weight(1f))

                                MusicEqualizer(
                                    isPlaying = track.isPlaying,
                                    artwork = track.artwork
                                )
                            }
                        }

                        // РАСКРЫТЫЙ ПЛЕЕР

                        AnimatedVisibility(
                            visible = playerExpanded,
                            enter = fadeIn(tween(220)) +
                                    scaleIn(
                                        tween(250),
                                        initialScale = 0.94f
                                    ),
                            exit = fadeOut(tween(130)) +
                                    scaleOut(
                                        tween(150),
                                        targetScale = 0.96f
                                    )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                // ВЕРХНЯЯ ПАНЕЛЬ

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(22.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "СЕЙЧАС ИГРАЕТ",
                                        color = Color(0xFFAAA2B8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                Spacer(Modifier.height(10.dp))

                                // ОБЛОЖКА

                                // ОБЛОЖКА

                                Box(
                                    modifier = Modifier
                                        .size(112.dp)
                                        .shadow(
                                            elevation = 14.dp,
                                            shape = RoundedCornerShape(26.dp),
                                            ambientColor = AccentPurple.copy(alpha = 0.25f),
                                            spotColor = AccentPurple.copy(alpha = 0.25f)
                                        )
                                        .clip(RoundedCornerShape(26.dp))
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(Color(0xFF292631)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (track.artwork != null) {
                                        Image(
                                            bitmap = track.artwork.asImageBitmap(),
                                            contentDescription = "Обложка альбома",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            "♫",
                                            color = SoftWhite,
                                            fontSize = 48.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // НАЗВАНИЕ И ИСПОЛНИТЕЛЬ

                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(3.dp))

                                Text(
                                    text = track.artist.ifBlank {
                                        "Неизвестный исполнитель"
                                    },
                                    color = Color(0xFFAAA6B2),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(15.dp))

                                // КНОПКИ УПРАВЛЕНИЯ

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 13.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    // НАЗАД

                                    Box(
                                        modifier = Modifier
                                            .size(43.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF302B38))
                                            .clickable {
                                                MusicData.previousTrack()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "⏮",
                                            color = SoftWhite,
                                            fontSize = 21.sp
                                        )
                                    }

                                    // PLAY / PAUSE

                                    Box(
                                        modifier = Modifier
                                            .size(51.dp)
                                            .shadow(
                                                8.dp,
                                                CircleShape,
                                                ambientColor = AccentPurple.copy(alpha = 0.35f),
                                                spotColor = AccentPurple.copy(alpha = 0.35f)
                                            )
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFFD6B5FF),
                                                        Color(0xFF9D69E8)
                                                    )
                                                )
                                            )
                                            .clickable {
                                                MusicData.togglePlayback()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (track.isPlaying) "Ⅱ" else "▶",
                                            color = Color(0xFF17121E),
                                            fontSize = if (track.isPlaying) 23.sp else 19.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // ВПЕРЁД

                                    Box(
                                        modifier = Modifier
                                            .size(43.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF302B38))
                                            .clickable {
                                                MusicData.nextTrack()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "⏭",
                                            color = SoftWhite,
                                            fontSize = 21.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.height(5.dp))
                            }
                        }
                    }
                }
            }

            // УВЕДОМЛЕНИЯ

            AnimatedVisibility(
                visible = notification != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                enter = slideInHorizontally(tween(350)) { it } +
                        fadeIn(tween(250)) +
                        scaleIn(tween(350), initialScale = 0.5f),
                exit = slideOutHorizontally(tween(250)) { it } +
                        fadeOut(tween(180)) +
                        scaleOut(tween(250), targetScale = 0.5f)
            ) {
                Box(Modifier.fillMaxWidth()) {

                    // КАРТОЧКА СООБЩЕНИЯ

                    AnimatedVisibility(
                        visible = notificationExpanded &&
                                notification != null &&
                                !playerExpanded,
                        modifier = Modifier.align(Alignment.Center),
                        enter = slideInHorizontally(tween(250)) { it / 2 } +
                                fadeIn(tween(200)) +
                                scaleIn(tween(250), initialScale = 0.9f),
                        exit = slideOutHorizontally(tween(180)) { it / 2 } +
                                fadeOut(tween(150))
                    ) {
                        Column(
                            modifier = Modifier
                                .width(notificationCardWidth)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF17171D))
                                .then(
                                    islandBorderModifier(RoundedCornerShape(20.dp))
                                )
                                .pointerInput(notification?.key) {
                                    detectTapGestures(
                                        onTap = {
                                            notificationExpanded = false
                                        },
                                        onLongPress = {
                                            MusicData.openNotificationTarget(context, notification)
                                        }
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                notification?.appName.orEmpty(),
                                color = AccentPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(5.dp))

                            Text(
                                notification?.title.orEmpty(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(3.dp))

                            Text(
                                notification?.text.orEmpty(),
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }


// ИКОНКА УВЕДОМЛЕНИЯ

                    if (!playerExpanded && !notificationExpanded) {

                        if (hasMusic) {
                            // Если музыка играет — оставляем прежнюю круглую иконку.
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(
                                        x = playerWidth / 2 +
                                                gap +
                                                bubbleSize / 2
                                    )
                                    .size(bubbleSize)
                                    .clip(CircleShape)
                                    .background(Color.Black)
                                    .then(islandBorderModifier(CircleShape))
                                    .pointerInput(notification?.packageName) {
                                        detectTapGestures(
                                            onTap = {
                                                notificationExpanded = !notificationExpanded
                                            },
                                            onLongPress = {
                                                MusicData.openNotificationTarget(context, notification)
                                            }
                                        )
                                    }
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val image = notification?.contentImage
                                    ?: notification?.appIcon

                                if (image != null) {
                                    Image(
                                        bitmap = image.asImageBitmap(),
                                        contentDescription = "Уведомление",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        notification?.appName
                                            ?.take(1)
                                            ?.uppercase() ?: "•",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                        } else {
                            // Если музыки нет — уведомление занимает место плеера.

                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(config.collapsedWidth.dp)
                                    .height(bubbleSize)
                                    .clip(RoundedCornerShape(config.cornerRadius.dp))
                                    .background(Color.Black)
                                    .then(
                                        islandBorderModifier(
                                            RoundedCornerShape(config.cornerRadius.dp)
                                        )
                                    )
                                    .pointerInput(notification?.key) {
                                        detectTapGestures(
                                            onTap = {
                                                notificationExpanded = true
                                            },
                                            onLongPress = {
                                                MusicData.openNotificationTarget(context, notification)
                                            }
                                        )
                                    }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                // КРУГЛАЯ ИКОНКА ПРИЛОЖЕНИЯ

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF292936)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    notification?.appIcon?.let { icon ->
                                        Image(
                                            bitmap = icon.asImageBitmap(),
                                            contentDescription = "Иконка приложения",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } ?: Text(
                                        notification?.appName?.take(1)?.uppercase() ?: "•",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // КРУГЛЫЙ АВАТАР ОТПРАВИТЕЛЯ

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF302B38)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val avatar = notification?.senderAvatar

                                    if (avatar != null) {
                                        Image(
                                            bitmap = avatar.asImageBitmap(),
                                            contentDescription = "Аватар отправителя",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text("👤", fontSize = 18.sp)
                                    }
                                }
                            }

                        }
                    }

                }}
            }
        }
    }
}


@Composable
private fun RotatingVinyl(
    artwork: Bitmap,
    size: Dp,
    isPlaying: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(
        label = "Vinyl rotation"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "Vinyl spin"
    )

    Box(
        modifier = Modifier
            .size(size)
            .rotate(if (isPlaying) rotation else 0f)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Круглая обложка без рамок и отступов
        Image(
            bitmap = artwork.asImageBitmap(),
            contentDescription = "Виниловая обложка",
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Центральное отверстие пластинки
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.Black)
        )
    }
}





@Composable
private fun MusicEqualizer(
    isPlaying: Boolean,
    artwork: Bitmap?
) {
    val context = LocalContext.current
    val levels by AudioVisualizer.levels.collectAsState()

    // Получаем один основной цвет обложки.
    val coverColor = remember(artwork) {
        extractCoverColor(artwork)
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            AudioVisualizer.start(context.applicationContext)
        } else {
            AudioVisualizer.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioVisualizer.stop()
        }
    }

    val maxHeights = listOf(8f, 16f, 11f, 19f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(24.dp)
    ) {
        maxHeights.forEachIndexed { index, maxHeight ->

            val targetHeight = if (isPlaying) {
                4f + levels[index] * (maxHeight - 4f)
            } else {
                4f
            }

            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = tween(100),
                label = "Equalizer bar $index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(coverColor)
            )
        }
    }
}

private fun extractCoverColor(artwork: Bitmap?): Color {
    if (artwork == null) return AccentPurple

    return try {
        val bitmap = if (artwork.config == Bitmap.Config.HARDWARE) {
            artwork.copy(Bitmap.Config.ARGB_8888, false)
                ?: return AccentPurple
        } else {
            artwork
        }

        val stepX = (bitmap.width / 20).coerceAtLeast(1)
        val stepY = (bitmap.height / 20).coerceAtLeast(1)

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)

                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(pixel, hsv)

                // Учитываем преимущественно насыщенные цвета.
                if (
                    hsv[1] > 0.25f &&
                    hsv[2] > 0.2f &&
                    android.graphics.Color.alpha(pixel) > 128
                ) {
                    red += android.graphics.Color.red(pixel)
                    green += android.graphics.Color.green(pixel)
                    blue += android.graphics.Color.blue(pixel)
                    count++
                }
            }
        }

        if (bitmap !== artwork) {
            bitmap.recycle()
        }

        if (count == 0) {
            AccentPurple
        } else {
            Color(
                android.graphics.Color.rgb(
                    (red / count).toInt(),
                    (green / count).toInt(),
                    (blue / count).toInt()
                )
            )
        }
    } catch (e: Exception) {
        AccentPurple
    }
}




@Composable
private fun ArtworkWaveBackground(
    artwork: Bitmap?,
    modifier: Modifier = Modifier
) {
    val colors = remember(artwork) {
        if (artwork == null) {
            listOf(
                Color(0xFF9B59B6),
                Color(0xFF5145A0),
                Color(0xFF252044)
            )
        } else {
            val candidates = mutableListOf<Pair<Color, Float>>()

            val stepX = (artwork.width / 12).coerceAtLeast(1)
            val stepY = (artwork.height / 12).coerceAtLeast(1)

            for (x in 0 until artwork.width step stepX) {
                for (y in 0 until artwork.height step stepY) {
                    val pixel = artwork.getPixel(x, y)

                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(pixel, hsv)

                    if (hsv[1] > 0.25f && hsv[2] > 0.2f) {
                        candidates.add(
                            Color(pixel) to hsv[1]
                        )
                    }
                }
            }

            val selected = candidates
                .sortedByDescending { it.second }
                .map { it.first }
                .distinct()
                .take(3)

            when {
                selected.size >= 3 -> selected
                selected.isNotEmpty() -> selected + listOf(
                    Color(0xFF5145A0),
                    Color(0xFF292333)
                ).take(3 - selected.size)
                else -> listOf(
                    Color(0xFF9B59B6),
                    Color(0xFF5145A0),
                    Color(0xFF252044)
                )
            }
        }
    }

    val transition = rememberInfiniteTransition(
        label = "Artwork wave"
    )

    val animation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 7000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Wave movement"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val positions = listOf(
            Offset(
                w * (0.2f + animation * 0.25f),
                h * 0.25f
            ),
            Offset(
                w * (0.8f - animation * 0.2f),
                h * 0.55f
            ),
            Offset(
                w * 0.45f,
                h * (0.85f - animation * 0.2f)
            )
        )

        colors.forEachIndexed { index, color ->
            val center = positions[index]

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.65f),
                        color.copy(alpha = 0.25f),
                        color.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = center
            )
        }

        // Затемнение для читаемости текста
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.05f),
                    Color.Black.copy(alpha = 0.28f),
                    Color.Black.copy(alpha = 0.48f)
                )
            )
        )
    }
}
