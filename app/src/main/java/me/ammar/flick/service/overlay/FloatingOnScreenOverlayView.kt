package me.ammar.flick.service.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OverlayMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val action: String? = null
)

@Serializable
data class OverlayMenuConfig(
    val title: String,
    val subtitle: String? = null,
    val items: List<OverlayMenuItem> = emptyList()
)

class FloatingOnScreenOverlayView(
    context: Context,
    private val config: OverlayMenuConfig,
    private val onItemClicked: (OverlayMenuItem) -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density

    init {
        // Scrim background: tapping outside dismisses the overlay
        setBackgroundColor(Color.parseColor("#77000000"))
        setOnClickListener { onDismiss() }

        val cardWidth = (320 * density).toInt()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1F1822")) // Plum surface container
                cornerRadius = 24 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#4ED8B4D8"))
            }
            elevation = 16 * density
            // Prevent taps inside card from closing
            setOnClickListener { /* Consume click */ }
        }

        // Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (14 * density).toInt()
            }
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(context).apply {
            text = config.title
            setTextColor(Color.parseColor("#F5EEF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            paint.isFakeBoldText = true
        }
        titleCol.addView(titleTv)

        if (!config.subtitle.isNullOrBlank()) {
            val subTv = TextView(context).apply {
                text = config.subtitle
                setTextColor(Color.parseColor("#A898A6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            titleCol.addView(subTv)
        }
        headerRow.addView(titleCol)

        // Close button (X)
        val closeBtn = TextView(context).apply {
            text = "X"
            setTextColor(Color.parseColor("#D8B4D8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            val btnSize = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#342735"))
                cornerRadius = 16 * density
            }
            setOnClickListener { onDismiss() }
        }
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // Action Item Buttons
        for (item in config.items) {
            val itemBtn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val itemPadH = (16 * density).toInt()
                val itemPadV = (12 * density).toInt()
                setPadding(itemPadH, itemPadV, itemPadH, itemPadV)
                val itemBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2E2330"))
                    cornerRadius = 14 * density
                    setStroke((1 * density).toInt(), Color.parseColor("#3D323E"))
                }
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#33D8B4D8")),
                    itemBg,
                    null
                )
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * density).toInt()
                }

                setOnClickListener {
                    onItemClicked(item)
                }
            }

            val itemTitleTv = TextView(context).apply {
                text = item.title
                setTextColor(Color.parseColor("#F5EEF5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                paint.isFakeBoldText = true
            }
            itemBtn.addView(itemTitleTv)

            if (!item.subtitle.isNullOrBlank()) {
                val itemSubTv = TextView(context).apply {
                    text = item.subtitle
                    setTextColor(Color.parseColor("#A898A6"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(0, (2 * density).toInt(), 0, 0)
                }
                itemBtn.addView(itemSubTv)
            }

            card.addView(itemBtn)
        }

        val cardLp = LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
        addView(card, cardLp)
    }
}

object FloatingOnScreenOverlayManager {
    private const val TAG = "FloatingOverlayMgr"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeOverlayView: FloatingOnScreenOverlayView? = null

    fun show(
        service: AccessibilityService,
        config: OverlayMenuConfig,
        onItemClicked: (OverlayMenuItem) -> Unit
    ) {
        mainHandler.post {
            dismiss(service)

            val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlay = FloatingOnScreenOverlayView(
                context = service,
                config = config,
                onItemClicked = { item ->
                    dismiss(service)
                    onItemClicked(item)
                },
                onDismiss = {
                    dismiss(service)
                }
            )

            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(overlay, params)
                activeOverlayView = overlay
                Log.i(TAG, "Showing on-screen overlay: ${config.title}")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to show on-screen overlay", t)
            }
        }
    }

    fun dismiss(service: Context) {
        mainHandler.post {
            val view = activeOverlayView ?: return@post
            activeOverlayView = null
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                wm.removeView(view)
                Log.i(TAG, "Dismissed on-screen overlay")
            } catch (t: Throwable) {
                // Ignored
            }
        }
    }
}
