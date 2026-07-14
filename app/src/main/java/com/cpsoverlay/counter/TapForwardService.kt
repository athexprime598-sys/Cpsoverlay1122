package com.cpsoverlay.counter

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class TapForwardService : AccessibilityService() {

    companion object {
        var instance: TapForwardService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_W -> OverlayService.instance?.onWasdKey('W', down)
            KeyEvent.KEYCODE_A -> OverlayService.instance?.onWasdKey('A', down)
            KeyEvent.KEYCODE_S -> OverlayService.instance?.onWasdKey('S', down)
            KeyEvent.KEYCODE_D -> OverlayService.instance?.onWasdKey('D', down)
            KeyEvent.KEYCODE_F8 -> if (down) OverlayService.instance?.toggleVisibility()
        }
        return false
    }

    fun dispatchTapAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
