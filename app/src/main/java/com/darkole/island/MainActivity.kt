
package com.darkole.island

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.draw.clip
import android.widget.Toast

private val Background = Color(0xFF101016)
private val Panel = Color(0xFF1B1B25)
private val Accent = Color(0xFFB58AFF)
private val Muted = Color(0xFF9292A3)
private val Green = Color(0xFF72E6A0)

class MainActivity : ComponentActivity() {

    private var permissionRefresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        IslandSettings.load(this)

        setContent {
            DarkoleApp(
                refreshKey = permissionRefresh,
                onRefresh = { permissionRefresh++ }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefresh++
    }
}

@Composable
fun DarkoleApp(
    refreshKey: Int,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var islandEnabled by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onRefresh()
    }

    val audioGranted = remember(refreshKey) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val overlayGranted = remember(refreshKey) {
        Settings.canDrawOverlays(context)
    }

    val notificationGranted = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    val listenerGranted = remember(refreshKey) {
        isNotificationListenerEnabled(context)
    }

    val accessibilityGranted = remember(refreshKey) {
        isAccessibilityEnabled(context)
    }

    val allGranted = overlayGranted &&
            notificationGranted &&
            listenerGranted &&
            accessibilityGranted

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = Panel,
            primary = Accent
        )
    ) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = Panel,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Text("⌂", fontSize = 21.sp) },
                        label = { Text("Главная") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = 0.15f),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Text("⚙", fontSize = 20.sp) },
                        label = { Text("Настройки") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = 0.15f),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    label = "page"
                ) { tab ->
                    if (tab == 0) {
                        HomePage(
                            islandEnabled = islandEnabled,
                            allGranted = allGranted,
                            overlayGranted = overlayGranted,
                            notificationGranted = notificationGranted,
                            listenerGranted = listenerGranted,
                            accessibilityGranted = accessibilityGranted,
                            audioGranted = audioGranted,
                            onRequestAudioPermission = {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onToggle = {
                                if (!islandEnabled) {
                                    if (!Settings.canDrawOverlays(context)) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                        return@HomePage
                                    }

                                    ContextCompat.startForegroundService(
                                        context,
                                        Intent(context, IslandService::class.java).apply {
                                            action = IslandService.ACTION_SHOW
                                        }
                                    )
                                    islandEnabled = true
                                } else {
                                    context.stopService(
                                        Intent(context, IslandService::class.java)
                                    )
                                    islandEnabled = false
                                }
                            },
                            onOpenSettings = { selectedTab = 1 },
                            onPermissionSettings = { type ->
                                when (type) {
                                    "overlay" -> context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )

                                    "listener" -> context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )

                                    "accessibility" -> context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    )

                                    "notifications" -> {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                    .setData(Uri.parse("package:${context.packageName}"))
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        SettingsPage(
                            onReset = {
                                IslandSettings.reset(context)
                                onRefresh()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomePage(
    audioGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    islandEnabled: Boolean,
    allGranted: Boolean,
    overlayGranted: Boolean,
    notificationGranted: Boolean,
    listenerGranted: Boolean,
    accessibilityGranted: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onPermissionSettings: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Accent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "D",
                    color = Background,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    "Darkole Island",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Динамический остров",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }

        // Главный переключатель
        PowerSwitch(
            enabled = islandEnabled,
            allGranted = allGranted,
            onClick = onToggle
        )

        // Предпросмотр острова
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(22.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionHeader("Предпросмотр")

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Color(0xFF292936),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .width(145.dp)
                        .height(36.dp)
                        .background(Color.Black, RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (islandEnabled) "●" else "Darkole",
                        color = if (islandEnabled) Green else Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                if (islandEnabled) "Остров включён" else "Остров выключен",
                color = if (islandEnabled) Green else Muted,
                fontSize = 13.sp
            )
        }

        // Разрешения
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader("Разрешения")

            PermissionRow(
                "Отображение поверх окон",
                overlayGranted
            ) { onPermissionSettings("overlay") }

            PermissionRow(
                "Доступ к уведомлениям",
                listenerGranted
            ) { onPermissionSettings("listener") }

            PermissionRow(
                "Специальные возможности",
                accessibilityGranted
            ) { onPermissionSettings("accessibility") }

            PermissionRow(
                "Уведомления приложения",
                notificationGranted
            ) { onPermissionSettings("notifications") }

            PermissionRow(
                "Анализ аудио для эквалайзера",
                audioGranted,
                onRequestAudioPermission
            )
        }

        // Настройки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(20.dp))
                .clickable { onOpenSettings() }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Настройки острова",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    "Размер, высота и положение",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            Text("›", color = Accent, fontSize = 28.sp)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun PowerSwitch(
    enabled: Boolean,
    allGranted: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) Color(0xFF203B30) else Panel,
        animationSpec = spring(),
        label = "switchBackground"
    )

    val accentColor by animateColorAsState(
        targetValue = if (enabled) Green else Accent,
        animationSpec = spring(),
        label = "switchAccent"
    )

    val knobOffset by animateDpAsState(
        targetValue = if (enabled) 30.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 350f
        ),
        label = "knobOffset"
    )

    val scale by animateFloatAsState(
        targetValue = if (enabled) 1.02f else 1f,
        animationSpec = spring(),
        label = "switchScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(26.dp))
            .border(
                1.dp,
                accentColor.copy(alpha = 0.35f),
                RoundedCornerShape(26.dp)
            )
            .clickable { onClick() }
            .padding(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Dynamic Island",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(5.dp))

                AnimatedContent(
                    targetState = enabled,
                    label = "switchText"
                ) { isEnabled ->
                    Text(
                        if (isEnabled) "Остров активен" else "Остров отключён",
                        color = if (isEnabled) Green else Muted,
                        fontSize = 13.sp
                    )
                }
            }

            // Переключатель ON/OFF
            Box(
                modifier = Modifier
                    .width(66.dp)
                    .height(36.dp)
                    .background(
                        accentColor.copy(alpha = 0.25f),
                        CircleShape
                    )
                    .border(1.dp, accentColor, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = knobOffset)
                        .size(26.dp)
                        .background(accentColor, CircleShape)
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(
                        if (enabled) Green else Muted,
                        CircleShape
                    )
            )

            Spacer(Modifier.width(8.dp))

            Text(
                if (enabled) "НАЖМИТЕ, ЧТОБЫ ВЫКЛЮЧИТЬ"
                else "НАЖМИТЕ, ЧТОБЫ ВКЛЮЧИТЬ",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        if (!allGranted && !enabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Для работы острова необходимо выдать основные разрешения",
                color = Color(0xFFFFC078),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    granted: Boolean,
    onClick: () -> Unit
) {

    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!granted) onClick() }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp
            )
            Text(
                if (granted) "Разрешено" else "Не разрешено",
                color = if (granted) Green else Color(0xFFFFA36C),
                fontSize = 11.sp
            )
        }

        Text(
            if (granted) "✓" else "›",
            color = if (granted) Green else Accent,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsPage(
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(IslandSettings.config) }

    fun updateConfig(newConfig: IslandConfig) {
        config = newConfig
        IslandSettings.update(context, newConfig)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            "Настройки",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        SettingsGroup("Размер острова") {
            SettingSlider(
                "Ширина в обычном состоянии",
                config.collapsedWidth,
                100f..250f
            ) {
                updateConfig(config.copy(collapsedWidth = it))
            }

            SettingSlider(
                "Ширина в раскрытом состоянии",
                config.expandedWidth,
                200f..400f
            ) {
                updateConfig(config.copy(expandedWidth = it))
            }

            SettingSlider(
                "Высота в обычном состоянии",
                config.collapsedHeight,
                30f..80f
            ) {
                updateConfig(config.copy(collapsedHeight = it))
            }

            SettingSlider(
                "Высота в раскрытом состоянии",
                config.expandedHeight,
                150f..400f
            ) {
                updateConfig(config.copy(expandedHeight = it))
            }
        }

        SettingsGroup("Внешний вид") {
            SettingSlider(
                "Скругление углов",
                config.cornerRadius,
                10f..50f
            ) {
                updateConfig(config.copy(cornerRadius = it))
            }

            SettingSlider(
                "Отступ сверху",
                config.topOffset,
                -80f..80f
            ) {
                updateConfig(config.copy(topOffset = it))
            }
        }


        SettingsGroup("Обводка островка") {
            SettingSlider(
                title = "Толщина обводки",
                value = config.borderWidth,
                range = 0f..4f,
                valueLabel = { "%.1f dp".format(it) }
            ) {
                updateConfig(config.copy(borderWidth = it))
            }

            Text(
                "Цвет обводки",
                color = Color.White,
                fontSize = 13.sp
            )

            BorderColorSelector(
                selected = config.borderColorMode,
                onSelect = {
                    updateConfig(config.copy(borderColorMode = it))
                }
            )
        }


        Button(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF353541)
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Сбросить настройки")
        }
    }
}


@Composable
fun BorderColorSelector(
    selected: IslandBorderColorMode,
    onSelect: (IslandBorderColorMode) -> Unit
) {
    val options = IslandBorderColorMode.values()

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.toList().chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { option ->
                    val isSelected = selected == option

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    Accent.copy(alpha = 0.16f)
                                } else {
                                    Color(0xFF252530)
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Accent else Color(0xFF393945),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelect(option) }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(25.dp)
                                .clip(CircleShape)
                                .background(
                                    option.color ?: Color(0xFFB58AFF)
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (option == IslandBorderColorMode.NONE) {
                                Text(
                                    "×",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (option == IslandBorderColorMode.COVER) {
                                Text(
                                    "♪",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            option.title,
                            color = if (isSelected) Accent else Color.White,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                repeat(3 - rowOptions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}


@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        SectionHeader(title)
        content()
    }
}

@Composable
fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String = { it.toInt().toString() },
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 13.sp
            )
            Text(
                valueLabel(value),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0xFF393945)
            )
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    return enabled.contains(context.packageName)
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabled.contains(context.packageName)
}
