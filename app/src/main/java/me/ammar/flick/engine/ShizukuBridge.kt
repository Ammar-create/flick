package me.ammar.flick.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuBridge {
    private const val TAG = "ShizukuBridge"

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable = _isAvailable.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
        _hasPermission.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        _hasPermission.value = (grantResult == PackageManager.PERMISSION_GRANTED)
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            checkStatus()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to register Shizuku listeners", t)
        }
    }

    fun checkStatus() {
        try {
            val ping = Shizuku.pingBinder()
            _isAvailable.value = ping
            if (ping) {
                _hasPermission.value = (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            } else {
                _hasPermission.value = false
            }
        } catch (t: Throwable) {
            _isAvailable.value = false
            _hasPermission.value = false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (_isAvailable.value && !_hasPermission.value) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to request Shizuku permission", t)
        }
    }

    suspend fun executeCommand(cmd: String): String = withContext(Dispatchers.IO) {
        if (_isAvailable.value && _hasPermission.value) {
            try {
                // Execute via Shizuku process
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                process.waitFor()
                return@withContext output.trim()
            } catch (t: Throwable) {
                Log.w(TAG, "Shizuku process failed, falling back to runtime exec", t)
            }
        }

        // Fallback to standard app runtime execution
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute shell command: $cmd", e)
            "Error: ${e.message}"
        }
    }
}
