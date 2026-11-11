
package com.darkole.island

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class IslandAccessibilityService : AccessibilityService() {

    companion object {
        var instance: IslandAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this
        IslandOverlayManager.onAccessibilityServiceChanged(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Содержимое окон не читаем.
    }

    override fun onInterrupt() {
        // Дополнительная обработка не требуется.
    }

    fun dispatchTap(
        x: Float,
        y: Float,
        onFinished: () -> Unit
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    80
                )
            )
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    onFinished()
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    onFinished()
                }
            },
            null
        )

        if (!dispatched) {
            onFinished()
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) {
            instance = null
            IslandOverlayManager.onAccessibilityServiceChanged(null)
        }

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            IslandOverlayManager.onAccessibilityServiceChanged(null)
        }

        super.onDestroy()
    }
}
