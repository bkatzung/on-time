package com.kappacs.ontime.schedule

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TimePicker

class NonInterceptingScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var timePicker: TimePicker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (timePicker == null) {
            timePicker = findTimePicker(this)
        }

        timePicker?.let { picker ->
            if (picker.isShown) {
                val rect = Rect()
                picker.getGlobalVisibleRect(rect)
                if (rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    return false
                }
            }
        }

        return super.onInterceptTouchEvent(ev)
    }

    private fun findTimePicker(view: View): TimePicker? {
        if (view is TimePicker) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findTimePicker(child)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }
}
