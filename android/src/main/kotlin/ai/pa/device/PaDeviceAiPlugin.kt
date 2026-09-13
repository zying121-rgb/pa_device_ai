package ai.pa.device

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** One model client per Flutter engine. No reflection, cloud client or API key. */
class PaDeviceAiPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var scope: CoroutineScope
    private val operationLock = Mutex()
    @Volatile private var model: GenerativeModel? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val lifetime = SupervisorJob()
        scope = CoroutineScope(lifetime + Dispatchers.IO)
        lifetime.invokeOnCompletion {
            // Completion runs after cancelled child operations have unwound.
            val released = synchronized(this) { model.also { model = null } }
            CoroutineScope(Dispatchers.IO).launch {
                try { released?.close() } catch (_: Exception) { /* No payload logging. */ }
            }
        }
        channel = MethodChannel(binding.binaryMessenger, "ai.pa/pa_device_ai")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        scope.cancel()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method !in setOf("checkAvailability", "downloadModel", "generateText")) {
            result.notImplemented()
            return
        }
        val prompt = (call.arguments as? Map<*, *>)?.get("prompt") as? String
        scope.launch {
            val response = try {
                when (call.method) {
                    "checkAvailability" -> withTimeout(30_000) { checkAvailability() }
                    "downloadModel" -> exclusive {
                        withTimeout(15 * 60_000L) { downloadModel() }
                    }
                    else -> exclusive {
                        withTimeout(120_000) { generateText(prompt) }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                error("timeout", "The operation timed out. Check availability before retrying.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // SDK exception strings can contain user inputs. Do not log/forward them.
                error("native_failure", "AICore could not complete ${call.method}. Retry after checking availability.")
            } catch (_: LinkageError) {
                error("native_linkage_error", "The installed native AI libraries could not be loaded.")
            }
            withContext(Dispatchers.Main) { result.success(response) }
        }
    }

    private fun client(): GenerativeModel = synchronized(this) {
        model ?: Generation.getClient().also { model = it }
    }

    private suspend fun exclusive(block: suspend () -> Map<String, Any>): Map<String, Any> {
        if (!operationLock.tryLock()) return error("busy", "Another model operation is running.")
        return try { block() } finally { operationLock.unlock() }
    }

    private suspend fun checkAvailability(): Map<String, Any> =
        status(client().checkStatus())

    private suspend fun downloadModel(): Map<String, Any> {
        val native = client()
        val before = native.checkStatus()
        if (before != FeatureStatus.DOWNLOADABLE) return status(before)
        // first() cancels collection after a terminal event. Progress can be
        // observed by calling checkAvailability on the native runtime separately.
        val terminal = native.download().first {
            it is DownloadStatus.DownloadFailed || it is DownloadStatus.DownloadCompleted
        }
        if (terminal is DownloadStatus.DownloadFailed) {
            return error("download_failed", "AICore model download failed. Check the connection and retry.")
        }
        // A download callback alone is not proof that inference is ready.
        return status(native.checkStatus())
    }

    private suspend fun generateText(prompt: String?): Map<String, Any> {
        if (prompt.isNullOrBlank()) return error("invalid_prompt", "Supply a non-empty prompt.")
        if (prompt.length > 16_000) return error("prompt_too_long", "Shorten the prompt to at most 16,000 characters.")
        val native = client()
        val availability = native.checkStatus()
        if (availability != FeatureStatus.AVAILABLE) return status(availability)
        // Exactly the supplied text; no profile lookup, appended history or fallback.
        val response = native.generateContent(prompt)
        val text = response.candidates.firstOrNull()?.text
        if (text.isNullOrBlank()) return error("empty_response", "The model returned no text.")
        return status(FeatureStatus.AVAILABLE) + ("text" to text)
    }

    private fun status(nativeStatus: Int): Map<String, Any> {
        val value = when (nativeStatus) {
            FeatureStatus.AVAILABLE -> "ready"
            FeatureStatus.DOWNLOADABLE -> "download_required"
            FeatureStatus.DOWNLOADING -> "downloading"
            FeatureStatus.UNAVAILABLE -> "unavailable"
            else -> return error("unknown_status", "AICore returned an unrecognized capability status.")
        }
        return mapOf("status" to value, "available" to (value == "ready"), "provider" to "gemini_nano")
    }

    private fun error(code: String, message: String): Map<String, Any> = mapOf(
        "status" to "error", "available" to false, "provider" to "gemini_nano",
        "errorCode" to code, "message" to message,
    )
}
