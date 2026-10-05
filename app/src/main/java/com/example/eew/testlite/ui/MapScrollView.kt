package com.example.eew.testlite.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView

class MapScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    var mapView: View? = null
    private var touchOnMap = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val map = mapView
                touchOnMap = if (map != null && map.isShown) {
                    val rect = Rect()
                    map.getGlobalVisibleRect(rect)
                    val screen = IntArray(2)
                    getLocationOnScreen(screen)
                    rect.contains((screen[0] + ev.x).toInt(), (screen[1] + ev.y).toInt())
                } else {
                    false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchOnMap = false
        }
        return if (touchOnMap) false else super.onInterceptTouchEvent(ev)
    }
}
