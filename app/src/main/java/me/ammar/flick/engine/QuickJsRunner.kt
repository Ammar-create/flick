package me.ammar.flick.engine

import android.util.Log
import app.cash.quickjs.QuickJs
import kotlinx.serialization.json.JsonObject

class QuickJsRunner(private val bridge: HostBridgeInterface) {
    companion object {
        private const val TAG = "QuickJsRunner"
    }

    fun executeScript(code: String, args: JsonObject? = null): Any? {
        val quickJs = QuickJs.create()
        return try {
            quickJs.set("__bridge", HostBridgeInterface::class.java, bridge)

            val preamble = """
                const shell = (cmd) => __bridge.shell(cmd);
                const launch = (comp) => __bridge.launch(comp);
                const sleep = (ms) => __bridge.sleep(ms);
                const clipboard = {
                    text: () => __bridge.getClipboard(),
                    setText: (txt) => __bridge.setClipboard(txt)
                };
                const ui = {
                    toast: (msg) => __bridge.toast(msg),
                    find: (query) => {
                        const res = __bridge.findNode(JSON.stringify(query));
                        return res ? JSON.parse(res) : null;
                    },
                    tap: (node) => __bridge.tapNode(JSON.stringify(node))
                };
                const http = {
                    get: (url) => __bridge.httpGet(url),
                    post: (url, body) => __bridge.httpPost(url, typeof body === 'string' ? body : JSON.stringify(body))
                };
                const capture = {
                    region: () => __bridge.captureSnip(),
                    snip: () => __bridge.captureSnip()
                };
                const log = (msg) => __bridge.toast(String(msg));
            """.trimIndent()

            val argsJson = args?.toString() ?: "{}"
            val wrappedScript = """
                $preamble
                const args = $argsJson;
                (function() {
                    $code
                })();
            """.trimIndent()

            quickJs.evaluate(wrappedScript)
        } catch (t: Throwable) {
            Log.e(TAG, "QuickJS execution error", t)
            bridge.toast("Script error: ${t.message}")
            null
        } finally {
            quickJs.close()
        }
    }
}
