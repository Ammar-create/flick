package me.ammar.flick.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.ammar.flick.model.PluginEntry
import me.ammar.flick.model.RepoManifest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val message: String, val pluginsCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class RepoSyncManager(private val context: Context) {
    companion object {
        private const val TAG = "RepoSyncManager"
        const val DEFAULT_REPO_URL = "http://127.0.0.1:8090"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    private val _manifest = MutableStateFlow<RepoManifest?>(null)
    val manifest = _manifest.asStateFlow()

    private val _installedPlugins = MutableStateFlow<List<PluginEntry>>(emptyList())
    val installedPlugins = _installedPlugins.asStateFlow()

    private val extensionsDir: File
        get() = File(context.filesDir, "extensions").apply { mkdirs() }

    init {
        initializeBundledPlugins()
        loadCachedManifest()
    }

    private fun initializeBundledPlugins() {
        try {
            val manifestFile = File(context.filesDir, "repo.json")
            if (!manifestFile.exists()) {
                val bundledManifest = context.assets.open("repo.json").bufferedReader().use { it.readText() }
                manifestFile.writeText(bundledManifest)
            }
            val assetList = context.assets.list("extensions") ?: emptyArray()
            for (assetName in assetList) {
                val targetFile = File(extensionsDir, assetName)
                if (!targetFile.exists()) {
                    context.assets.open("extensions/$assetName").use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to unpack bundled plugins", t)
        }
    }

    fun getExtensionFile(pluginId: String): File? {
        val file = File(extensionsDir, "$pluginId.js")
        return if (file.exists()) file else null
    }

    private fun loadCachedManifest() {
        try {
            val manifestFile = File(context.filesDir, "repo.json")
            if (manifestFile.exists()) {
                val manifestObj = json.decodeFromString<RepoManifest>(manifestFile.readText())
                _manifest.value = manifestObj
                _installedPlugins.value = manifestObj.plugins.filter { plugin ->
                    val scriptFile = File(extensionsDir, "${plugin.id}.js")
                    if (!scriptFile.exists()) return@filter false
                    val hash = "sha256:" + computeSha256(scriptFile.readBytes())
                    hash == plugin.hash
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load cached manifest", t)
        }
    }

    suspend fun syncRepo(baseUrl: String = DEFAULT_REPO_URL): Result<Int> = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing
        try {
            val manifestUrl = "$baseUrl/repo.json"
            val req = Request.Builder().url(manifestUrl).build()
            val resp = client.newCall(req).execute()

            if (!resp.isSuccessful) {
                val err = "HTTP ${resp.code}: ${resp.message}"
                _syncState.value = SyncState.Error(err)
                return@withContext Result.failure(Exception(err))
            }

            val body = resp.body?.string() ?: throw Exception("Empty manifest response")
            val manifestObj = json.decodeFromString<RepoManifest>(body)

            // Save manifest locally
            File(context.filesDir, "repo.json").writeText(body)
            _manifest.value = manifestObj

            var updatedCount = 0
            val validPlugins = mutableListOf<PluginEntry>()

            for (plugin in manifestObj.plugins) {
                val pluginFile = File(extensionsDir, "${plugin.id}.js")
                val needsDownload = if (pluginFile.exists()) {
                    val currentHash = "sha256:" + computeSha256(pluginFile.readBytes())
                    currentHash != plugin.hash
                } else {
                    true
                }

                if (needsDownload) {
                    val scriptUrl = "$baseUrl/${plugin.file}"
                    val scriptReq = Request.Builder().url(scriptUrl).build()
                    val scriptResp = client.newCall(scriptReq).execute()

                    if (!scriptResp.isSuccessful) {
                        Log.e(TAG, "Failed to download ${plugin.id} from $scriptUrl: ${scriptResp.code}")
                        continue
                    }

                    val scriptBytes = scriptResp.body?.bytes() ?: continue
                    val downloadedHash = "sha256:" + computeSha256(scriptBytes)

                    // Strict security check: must match hash declared in repo.json
                    if (downloadedHash != plugin.hash) {
                        Log.e(TAG, "Hash mismatch for ${plugin.id}! Expected ${plugin.hash}, got $downloadedHash")
                        continue
                    }

                    pluginFile.writeBytes(scriptBytes)
                    updatedCount++
                    validPlugins.add(plugin)
                } else {
                    validPlugins.add(plugin)
                }
            }

            _installedPlugins.value = validPlugins
            val successMsg = "Synced ${validPlugins.size} plugin(s), $updatedCount updated"
            _syncState.value = SyncState.Success(successMsg, validPlugins.size)
            Result.success(validPlugins.size)
        } catch (t: Throwable) {
            val err = t.message ?: "Failed to sync repo"
            Log.e(TAG, "Sync error", t)
            _syncState.value = SyncState.Error(err)
            Result.failure(t)
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
