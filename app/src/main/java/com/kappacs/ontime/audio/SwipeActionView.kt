package com.kappacs.ontime.audio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SwipeActionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var hapticsEnabled: Boolean = false
    var onDismissListener: (() -> Unit)? = null
    var onSnoozeListener: (() -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF") // 15% white track
        style = Paint.Style.FILL
    }

    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3FFFFFF") // 70% white
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    private val trackRect = RectF()
    private val activeRect = RectF()

    private var thumbOffset = 0f
    private var maxDragDistance = 0f
    private var isDragging = false
    private var startTouchX = 0f
    private var thresholdCrossed = false

    private var snapAnimator: ValueAnimator? = null

    companion object {
        private const val THRESHOLD_FRACTION = 0.40f
        private const val ACTION_ID_DISMISS = 1001
        private const val ACTION_ID_SNOOZE = 1002
    }

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null) // For shadow rendering

        ViewCompat.setAccessibilityDelegate(this, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = SwipeActionView::class.java.name
                info.contentDescription = "Swipe left to dismiss, or swipe right to snooze alarm."
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        ACTION_ID_DISMISS,
                        "Dismiss Alarm"
                    )
                )
                info.addAction(
                    AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        ACTION_ID_SNOOZE,
                        "Snooze Alarm"
                    )
                )
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                return when (action) {
                    ACTION_ID_DISMISS -> {
                        triggerDismiss()
                        true
                    }
                    ACTION_ID_SNOOZE -> {
                        triggerSnooze()
                        true
                    }
                    else -> super.performAccessibilityAction(host, action, args)
                }
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 8f
        trackRect.set(padding, padding, w - padding, h - padding)
        val thumbRadius = (h - padding * 2) / 2f
        maxDragDistance = (w / 2f) - thumbRadius - padding
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = trackRect.height() / 2f
        val centerX = width / 2f
        val centerY = height / 2f

        // Draw background track
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        // Draw active drag highlight
        val progress = if (maxDragDistance > 0) thumbOffset / maxDragDistance else 0f
        if (progress < 0) {
            // Dragging left towards Dismiss (Red)
            val alpha = (abs(progress) * 200).toInt().coerceIn(0, 255)
            activeTrackPaint.color = Color.argb(alpha, 229, 57, 53) // Material Red 600
            activeRect.set(centerX + thumbOffset - cornerRadius, trackRect.top, centerX, trackRect.bottom)
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, activeTrackPaint)
        } else if (progress > 0) {
            // Dragging right towards Snooze (Amber / Orange)
            val alpha = (progress * 200).toInt().coerceIn(0, 255)
            activeTrackPaint.color = Color.argb(alpha, 255, 152, 0) // Material Orange 500
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, activeTrackPaint)
        }

        // Draw labels
        val textY = centerY - (hintTextPaint.descent() + hintTextPaint.ascent()) / 2
        val leftX = width * 0.22f
        val rightX = width * 0.78f

        hintTextPaint.color = if (progress <= -THRESHOLD_FRACTION) Color.WHITE else Color.parseColor("#B3FFFFFF")
        canvas.drawText("◀ Dismiss", leftX, textY, hintTextPaint)

        hintTextPaint.color = if (progress >= THRESHOLD_FRACTION) Color.WHITE else Color.parseColor("#B3FFFFFF")
        canvas.drawText("Snooze ▶", rightX, textY, hintTextPaint)

        // Draw thumb
        val thumbX = centerX + thumbOffset
        val thumbRadius = cornerRadius - 4f
        canvas.drawCircle(thumbX, centerY, thumbRadius, thumbPaint)

        // Draw center icon or text on thumb
        textPaint.color = when {
            progress <= -THRESHOLD_FRACTION -> Color.parseColor("#E53935")
            progress >= THRESHOLD_FRACTION -> Color.parseColor("#FF9800")
            else -> Color.parseColor("#424242")
        }
        val iconText = when {
            progress < -0.1f -> "✕"
            progress > 0.1f -> "⏱"
            else -> "↔"
        }
        val iconY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(iconText, thumbX, iconY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                val centerX = width / 2f
                val thumbX = centerX + thumbOffset
                val touchRadius = height.toFloat()
                if (abs(event.x - thumbX) <= touchRadius) {
                    isDragging = true
                    startTouchX = event.x - thumbOffset
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val rawOffset = event.x - startTouchX
                    thumbOffset = max(-maxDragDistance, min(maxDragDistance, rawOffset))
                    val progress = if (maxDragDistance > 0) thumbOffset / maxDragDistance else 0f

                    val crossedNow = abs(progress) >= THRESHOLD_FRACTION
                    if (crossedNow && !thresholdCrossed) {
                        thresholdCrossed = true
                        if (hapticsEnabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            } else {
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        }
                    } else if (!crossedNow && thresholdCrossed) {
                        thresholdCrossed = false
                    }

                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    val progress = if (maxDragDistance > 0) thumbOffset / maxDragDistance else 0f

                    if (progress <= -THRESHOLD_FRACTION) {
                        triggerDismiss()
                    } else if (progress >= THRESHOLD_FRACTION) {
                        triggerSnooze()
                    } else {
                        // Snap back to center
                        snapBack()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapBack() {
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(thumbOffset, 0f).apply {
            duration = 250
            addUpdateListener { animator ->
                thumbOffset = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        thresholdCrossed = false
    }

    private fun triggerDismiss() {
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        onDismissListener?.invoke()
    }

    private fun triggerSnooze() {
        if (hapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        onSnoozeListener?.invoke()
    }
}
