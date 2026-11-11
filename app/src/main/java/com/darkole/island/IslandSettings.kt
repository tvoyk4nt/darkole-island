
package com.darkole.island

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class IslandBorderColorMode(
    val title: String,
    val color: Color?
) {
    DARK("Тёмный", Color(0xFF33333D)),
    WHITE("Белый", Color.White),
    BLACK("Чёрный", Color.Black),
    GRAY("Серый", Color(0xFF9292A3)),
    PURPLE("Фиолетовый", Color(0xFFB58AFF)),
    BLUE("Синий", Color(0xFF579BFF)),
    RED("Красный", Color(0xFFFF6262)),
    GREEN("Зелёный", Color(0xFF72E6A0)),
    COVER("Цвет обложки", null),
    NONE("Без обводки", null)
}

data class IslandConfig(
    val collapsedWidth: Float = 150f,
    val expandedWidth: Float = 310f,
    val collapsedHeight: Float = 52f,
    val expandedHeight: Float = 285f,
    val cornerRadius: Float = 26f,
    val topOffset: Float = 24f,
    val borderWidth: Float = 1f,
    val borderColorMode: IslandBorderColorMode = IslandBorderColorMode.DARK
)

object IslandSettings {

    var config by mutableStateOf(IslandConfig())
        private set

    private const val PREFS_NAME = "island_settings"

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val savedBorderMode = prefs.getString(
            "borderColorMode",
            IslandBorderColorMode.DARK.name
        )

        config = IslandConfig(
            collapsedWidth = prefs.getFloat("collapsedWidth", 150f),
            expandedWidth = prefs.getFloat("expandedWidth", 310f),
            collapsedHeight = prefs.getFloat("collapsedHeight", 52f),
            expandedHeight = prefs.getFloat("expandedHeight", 285f),
            cornerRadius = prefs.getFloat("cornerRadius", 26f),
            topOffset = prefs.getFloat("topOffset", 24f),
            borderWidth = prefs.getFloat("borderWidth", 1f),
            borderColorMode = runCatching {
                IslandBorderColorMode.valueOf(
                    savedBorderMode ?: IslandBorderColorMode.DARK.name
                )
            }.getOrDefault(IslandBorderColorMode.DARK)
        )
    }

    fun update(
        context: Context,
        newConfig: IslandConfig
    ) {
        config = newConfig

        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putFloat("collapsedWidth", newConfig.collapsedWidth)
            .putFloat("expandedWidth", newConfig.expandedWidth)
            .putFloat("collapsedHeight", newConfig.collapsedHeight)
            .putFloat("expandedHeight", newConfig.expandedHeight)
            .putFloat("cornerRadius", newConfig.cornerRadius)
            .putFloat("topOffset", newConfig.topOffset)
            .putFloat("borderWidth", newConfig.borderWidth)
            .putString("borderColorMode", newConfig.borderColorMode.name)
            .apply()

        IslandOverlayManager.updatePosition()
    }

    fun reset(context: Context) {
        update(context, IslandConfig())
    }
}
