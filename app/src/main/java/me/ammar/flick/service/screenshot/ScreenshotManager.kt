package me.ammar.flick.service.screenshot

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object ScreenshotManager {
    private const val TAG = "ScreenshotManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var activeFloatingBar: FloatingSnipBarView? = null
    private var activeCropOverlay: SnipCropOverlayView? = null

    fun triggerSnipWorkflow(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(service, "Screenshot requires Android 11+", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        val colorSpace = screenshot.colorSpace
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()

                        if (bitmap != null) {
                            mainHandler.post {
                                showFloatingBar(service, bitmap)
                            }
                        } else {
                            Log.e(TAG, "Failed to wrap hardware buffer into bitmap")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Accessibility takeScreenshot failed with code: $errorCode, trying Shizuku fallback")
                        tryShizukuFallback(service)
                    }
                }
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to take screenshot via Accessibility, trying Shizuku fallback", t)
            tryShizukuFallback(service)
        }
    }

    private fun tryShizukuFallback(service: AccessibilityService) {
        executor.execute {
            try {
                val tempFile = File(service.cacheDir, "shizuku_snip.png")
                val cmd = "screencap -p ${tempFile.absolutePath}"
                kotlinx.coroutines.runBlocking {
                    me.ammar.flick.engine.ShizukuBridge.executeCommand(cmd)
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
                    tempFile.delete()
                    if (bitmap != null) {
                        mainHandler.post {
                            showFloatingBar(service, bitmap)
                        }
                        return@execute
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Shizuku fallback screencap failed", t)
            }
            mainHandler.post {
                Toast.makeText(service, "Failed to capture screen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFloatingBar(service: AccessibilityService, bitmap: Bitmap) {
        dismissAllOverlays(service)

        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val barView = FloatingSnipBarView(
            context = service,
            bitmap = bitmap,
            onCropClicked = {
                dismissFloatingBar(service)
                showCropOverlay(service, bitmap)
            },
            onCopyClicked = {
                dismissFloatingBar(service)
                saveAndCopyToClipboard(service, bitmap, isCropped = false)
            }
        )

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        try {
            windowManager.addView(barView, params)
            activeFloatingBar = barView
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show floating snip bar", t)
        }
    }

    private fun showCropOverlay(service: AccessibilityService, bitmap: Bitmap) {
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val cropView = SnipCropOverlayView(
            context = service,
            fullBitmap = bitmap,
            onCropped = { croppedBitmap ->
                dismissCropOverlay(service)
                saveAndCopyToClipboard(service, croppedBitmap, isCropped = true)
            },
            onCancelled = {
                dismissCropOverlay(service)
            }
        )

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        try {
            windowManager.addView(cropView, params)
            activeCropOverlay = cropView
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show crop overlay", t)
        }
    }

    private fun saveAndCopyToClipboard(context: Context, bitmap: Bitmap, isCropped: Boolean) {
        executor.execute {
            try {
                // Ensure private cache directory exists (never touches gallery or public storage)
                val snipDir = File(context.cacheDir, "snip").apply { mkdirs() }
                // Clean older files
                snipDir.listFiles()?.forEach { it.delete() }

                val file = File(snipDir, "clipboard_snip.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                mainHandler.post {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newUri(context.contentResolver, "Screenshot", uri)
                    clipboard.setPrimaryClip(clip)

                    val toastMsg = if (isCropped) "Cropped snip copied to clipboard" else "Screenshot copied to clipboard"
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to copy screenshot to clipboard", t)
                mainHandler.post {
                    Toast.makeText(context, "Error copying to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun dismissFloatingBar(context: Context) {
        val bar = activeFloatingBar ?: return
        activeFloatingBar = null
        bar.cancelTimer()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(bar)
        } catch (t: Throwable) {
            // Ignored
        }
    }

    private fun dismissCropOverlay(context: Context) {
        val overlay = activeCropOverlay ?: return
        activeCropOverlay = null
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(overlay)
        } catch (t: Throwable) {
            // Ignored
        }
    }

    fun dismissAllOverlays(context: Context) {
        dismissFloatingBar(context)
        dismissCropOverlay(context)
    }
}
