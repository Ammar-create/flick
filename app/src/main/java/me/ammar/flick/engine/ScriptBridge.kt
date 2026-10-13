package me.ammar.flick.engine

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

interface HostBridgeInterface {
    fun shell(cmd: String): String
    fun launch(component: String)
    fun sleep(ms: Long)
    fun getClipboard(): String
    fun setClipboard(text: String)
    fun toast(msg: String)
    fun findNode(queryJson: String): String?
    fun tapNode(nodeJson: String): Boolean
    fun httpGet(url: String): String
    fun httpPost(url: String, body: String): String
    fun captureSnip(): Boolean
}

class ScriptBridge(
    private val context: Context,
    private val accessibilityServiceSupplier: () -> AccessibilityService?
) : HostBridgeInterface {

    companion object {
        private const val TAG = "ScriptBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun shell(cmd: String): String {
        Log.i(TAG, "shell: $cmd")
        return runBlocking {
            ShizukuBridge.executeCommand(cmd)
        }
    }

    override fun launch(component: String) {
        Log.i(TAG, "launch: $component")
        try {
            val parts = component.split("/")
            if (parts.size == 2) {
                val pkg = parts[0]
                val cls = if (parts[1].startsWith(".")) pkg + parts[1] else parts[1]
                val intent = Intent().apply {
                    setClassName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Intent launch failed, attempting via Shizuku shell", t)
            shell("am start -n $component")
        }
    }

    override fun sleep(ms: Long) {
        try {
            Thread.sleep(ms.coerceAtLeast(0L))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    override fun getClipboard(): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString() ?: ""
            } else ""
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read clipboard", t)
            ""
        }
    }

    override fun setClipboard(text: String) {
        mainHandler.post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("flick", text)
                cm.setPrimaryClip(clip)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to write clipboard", t)
            }
        }
    }

    override fun toast(msg: String) {
        mainHandler.post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun findNode(queryJson: String): String? {
        val service = accessibilityServiceSupplier() ?: return null
        val root = service.rootInActiveWindow ?: return null

        try {
            val query = Json.parseToJsonElement(queryJson).jsonObject
            val targetText = query["text"]?.jsonPrimitive?.content
            val targetId = query["id"]?.jsonPrimitive?.content
            val targetDesc = query["desc"]?.jsonPrimitive?.content

            val node = searchNode(root, targetText, targetId, targetDesc)
            return if (node != null) {
                """{"found": true, "text": "${node.text ?: ""}"}"""
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "findNode error", t)
            return null
        }
    }

    override fun tapNode(nodeJson: String): Boolean {
        val service = accessibilityServiceSupplier() ?: return false
        val root = service.rootInActiveWindow ?: return false

        try {
            val query = Json.parseToJsonElement(nodeJson).jsonObject
            val targetText = query["text"]?.jsonPrimitive?.content
            val targetId = query["id"]?.jsonPrimitive?.content
            val targetDesc = query["desc"]?.jsonPrimitive?.content

            val node = searchNode(root, targetText, targetId, targetDesc)
            return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "tapNode error", t)
            return false
        }
    }

    override fun httpGet(url: String): String {
        return try {
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            resp.body?.string() ?: ""
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun httpPost(url: String, body: String): String {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val reqBody = body.toRequestBody(mediaType)
            val req = Request.Builder().url(url).post(reqBody).build()
            val resp = httpClient.newCall(req).execute()
            resp.body?.string() ?: ""
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    override fun captureSnip(): Boolean {
        val service = accessibilityServiceSupplier() ?: return false
        mainHandler.post {
            me.ammar.flick.service.screenshot.ScreenshotManager.triggerSnipWorkflow(service)
        }
        return true
    }

    private fun searchNode(
        node: AccessibilityNodeInfo,
        text: String?,
        id: String?,
        desc: String?
    ): AccessibilityNodeInfo? {
        if (text != null && node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        if (id != null && node.viewIdResourceName?.contains(id, ignoreCase = true) == true) {
            return node
        }
        if (desc != null && node.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchNode(child, text, id, desc)
            if (found != null) return found
        }
        return null
    }
}
