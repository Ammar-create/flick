package me.ammar.flick.service.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class FloatingSnipBarView(
    context: Context,
    private val bitmap: Bitmap,
    private val onCropClicked: () -> Unit,
    private val onCopyClicked: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private var countDownTimer: CountDownTimer? = null

    init {
        // Container styling
        val padH = (16 * density).toInt()
        val padV = (10 * density).toInt()

        val rootCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1F1820")) // Plum surface container
                cornerRadius = 24 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#4ED8B4D8"))
            }
            elevation = 12 * density
        }

        // Thumbnail
        val thumbView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (48 * density).toInt()).apply {
                marginEnd = (12 * density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 8 * density
                setStroke((1 * density).toInt(), Color.parseColor("#D8B4D8"))
            }
            clipToOutline = true
        }
        rootCard.addView(thumbView)

        // Labels
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(context).apply {
            text = "Screen Captured"
            setTextColor(Color.parseColor("#F5EEF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            paint.isFakeBoldText = true
        }

        val countdownTv = TextView(context).apply {
            text = "Auto-copying in 5s..."
            setTextColor(Color.parseColor("#A898A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        textLayout.addView(titleTv)
        textLayout.addView(countdownTv)
        rootCard.addView(textLayout)

        // Crop Button
        val cropBtn = TextView(context).apply {
            text = "Crop"
            setTextColor(Color.parseColor("#3A2338"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint.isFakeBoldText = true
            val btnPadH = (14 * density).toInt()
            val btnPadV = (8 * density).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D8B4D8")) // Lilac accent
                cornerRadius = 100 * density
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = (8 * density).toInt()
            }
            setOnClickListener {
                countDownTimer?.cancel()
                onCropClicked()
            }
        }
        rootCard.addView(cropBtn)

        // Copy Now Button
        val copyBtn = TextView(context).apply {
            text = "Copy"
            setTextColor(Color.parseColor("#F5EEF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint.isFakeBoldText = true
            val btnPadH = (12 * density).toInt()
            val btnPadV = (8 * density).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#382C36")) // Surface highest
                cornerRadius = 100 * density
            }
            setOnClickListener {
                countDownTimer?.cancel()
                onCopyClicked()
            }
        }
        rootCard.addView(copyBtn)

        val lp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (48 * density).toInt()
        }
        addView(rootCard, lp)

        // Start 5 second countdown
        countDownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                countdownTv.text = "Auto-copying in ${seconds}s..."
            }

            override fun onFinish() {
                onCopyClicked()
            }
        }.start()
    }

    fun cancelTimer() {
        countDownTimer?.cancel()
    }
}
