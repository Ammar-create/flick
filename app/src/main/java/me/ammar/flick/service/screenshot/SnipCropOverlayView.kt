package me.ammar.flick.service.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SnipCropOverlayView(
    context: Context,
    private val fullBitmap: Bitmap,
    private val onCropped: (Bitmap) -> Unit,
    private val onCancelled: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private var cropRect = RectF()
    private var isDraggingRect = false
    private var activeCorner = -1 // 0: TopLeft, 1: TopRight, 2: BottomLeft, 3: BottomRight, 4: Center Move
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000") // 60% black scrim
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8B4D8") // Lilac crop border
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8B4D8")
        style = Paint.Style.FILL
    }

    private val handleRadius = 14f * density

    init {
        setWillNotDraw(false)

        // Bottom Action Bar
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val padH = (20 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1F1820"))
                cornerRadius = 100 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#4ED8B4D8"))
            }
        }

        val cancelBtn = TextView(context).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#F5EEF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val btnPadH = (16 * density).toInt()
            val btnPadV = (8 * density).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#382C36"))
                cornerRadius = 100 * density
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (16 * density).toInt() }
            setOnClickListener { onCancelled() }
        }
        bottomBar.addView(cancelBtn)

        val doneBtn = TextView(context).apply {
            text = "Done (Copy)"
            setTextColor(Color.parseColor("#3A2338"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            paint.isFakeBoldText = true
            val btnPadH = (20 * density).toInt()
            val btnPadV = (8 * density).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D8B4D8"))
                cornerRadius = 100 * density
            }
            setOnClickListener {
                val cropped = cropBitmap()
                onCropped(cropped)
            }
        }
        bottomBar.addView(doneBtn)

        val lp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (40 * density).toInt()
        }
        addView(bottomBar, lp)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cropRect.isEmpty && w > 0 && h > 0) {
            // Default crop rectangle centered: 70% width, 50% height
            val rectW = w * 0.75f
            val rectH = h * 0.55f
            val left = (w - rectW) / 2f
            val top = (h - rectH) / 2f
            cropRect.set(left, top, left + rectW, top + rectH)
        }
    }

    override fun onDraw(canvas: Canvas) {
        // Draw the background screenshot
        val src = Rect(0, 0, fullBitmap.width, fullBitmap.height)
        val dst = Rect(0, 0, width, height)
        canvas.drawBitmap(fullBitmap, src, dst, null)

        // Draw scrim outside crop rectangle
        val w = width.toFloat()
        val h = height.toFloat()

        // Top scrim
        canvas.drawRect(0f, 0f, w, cropRect.top, scrimPaint)
        // Bottom scrim
        canvas.drawRect(0f, cropRect.bottom, w, h, scrimPaint)
        // Left scrim
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint)
        // Right scrim
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, scrimPaint)

        // Draw crop rectangle border
        canvas.drawRect(cropRect, borderPaint)

        // Draw corner handles
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)

        super.onDraw(canvas)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                activeCorner = getTouchTarget(x, y)
                if (activeCorner != -1) {
                    isDraggingRect = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingRect) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    updateCropRect(dx, dy)
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingRect = false
                activeCorner = -1
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getTouchTarget(x: Float, y: Float): Int {
        val touchTolerance = 28f * density
        if (distance(x, y, cropRect.left, cropRect.top) < touchTolerance) return 0
        if (distance(x, y, cropRect.right, cropRect.top) < touchTolerance) return 1
        if (distance(x, y, cropRect.left, cropRect.bottom) < touchTolerance) return 2
        if (distance(x, y, cropRect.right, cropRect.bottom) < touchTolerance) return 3
        if (cropRect.contains(x, y)) return 4 // Center move
        return -1
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun updateCropRect(dx: Float, dy: Float) {
        val minSize = 40f * density
        when (activeCorner) {
            0 -> { // Top-Left
                cropRect.left = min(cropRect.left + dx, cropRect.right - minSize).coerceAtLeast(0f)
                cropRect.top = min(cropRect.top + dy, cropRect.bottom - minSize).coerceAtLeast(0f)
            }
            1 -> { // Top-Right
                cropRect.right = max(cropRect.right + dx, cropRect.left + minSize).coerceAtMost(width.toFloat())
                cropRect.top = min(cropRect.top + dy, cropRect.bottom - minSize).coerceAtLeast(0f)
            }
            2 -> { // Bottom-Left
                cropRect.left = min(cropRect.left + dx, cropRect.right - minSize).coerceAtLeast(0f)
                cropRect.bottom = max(cropRect.bottom + dy, cropRect.top + minSize).coerceAtMost(height.toFloat())
            }
            3 -> { // Bottom-Right
                cropRect.right = max(cropRect.right + dx, cropRect.left + minSize).coerceAtMost(width.toFloat())
                cropRect.bottom = max(cropRect.bottom + dy, cropRect.top + minSize).coerceAtMost(height.toFloat())
            }
            4 -> { // Center Move
                val rectW = cropRect.width()
                val rectH = cropRect.height()
                var newLeft = cropRect.left + dx
                var newTop = cropRect.top + dy

                newLeft = newLeft.coerceIn(0f, width - rectW)
                newTop = newTop.coerceIn(0f, height - rectH)

                cropRect.set(newLeft, newTop, newLeft + rectW, newTop + rectH)
            }
        }
    }

    private fun cropBitmap(): Bitmap {
        if (width <= 0 || height <= 0) return fullBitmap

        val scaleX = fullBitmap.width.toFloat() / width.toFloat()
        val scaleY = fullBitmap.height.toFloat() / height.toFloat()

        val startX = (cropRect.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 1)
        val startY = (cropRect.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 1)
        val cropW = (cropRect.width() * scaleX).toInt().coerceIn(1, fullBitmap.width - startX)
        val cropH = (cropRect.height() * scaleY).toInt().coerceIn(1, fullBitmap.height - startY)

        return try {
            Bitmap.createBitmap(fullBitmap, startX, startY, cropW, cropH)
        } catch (t: Throwable) {
            fullBitmap
        }
    }
}
