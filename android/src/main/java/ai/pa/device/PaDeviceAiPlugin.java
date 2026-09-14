package ai.pa.device;

import androidx.annotation.NonNull;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.Looper;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

/**
 * PA on-device AI bridge for Android.
 *
 * Uses ML Kit Prompt API / AICore / Gemini Nano only.
 * No cloud LLM, API key, profile lookup or network AI fallback.
 */
public final class PaDeviceAiPlugin
        implements FlutterPlugin, MethodChannel.MethodCallHandler {

    private MethodChannel channel;
    private Handler mainHandler;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final AtomicBoolean operationBusy =
            new AtomicBoolean(false);

    private volatile GenerativeModelFutures model;

    @Override
    public void onAttachedToEngine(
            @NonNull FlutterPlugin.FlutterPluginBinding binding) {

        mainHandler = new Handler(Looper.getMainLooper());

        channel = new MethodChannel(
                binding.getBinaryMessenger(),
                "ai.pa/pa_device_ai"
        );

        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(
            @NonNull FlutterPlugin.FlutterPluginBinding binding) {

        if (channel != null) {
            channel.setMethodCallHandler(null);
        }

        executor.shutdownNow();
    }

    @Override
    public void onMethodCall(
            @NonNull MethodCall call,
            @NonNull MethodChannel.Result result) {

        if (!call.method.equals("checkAvailability")
                && !call.method.equals("downloadModel")
                && !call.method.equals("generateText")) {

            result.notImplemented();
            return;
        }

        String prompt = null;

        if (call.arguments instanceof Map<?, ?>) {
            Object value =
                    ((Map<?, ?>) call.arguments).get("prompt");

            if (value instanceof String) {
                prompt = (String) value;
            }
        }

        final String finalPrompt = prompt;

        executor.execute(() -> {

            Map<String, Object> response;

            boolean needsExclusiveLock =
                    !call.method.equals("checkAvailability");

            if (needsExclusiveLock
                    && !operationBusy.compareAndSet(false, true)) {

                response = error(
                        "busy",
                        "Another model operation is running."
                );

                sendResult(result, response);
                return;
            }

            try {

                switch (call.method) {

                    case "checkAvailability":
                        response = checkAvailability();
                        break;

                    case "downloadModel":
                        response = downloadModel();
                        break;

                    case "generateText":
                        response = generateText(finalPrompt);
                        break;

                    default:
                        response = error(
                                "unknown_method",
                                "Unsupported method."
                        );
                }

            } catch (TimeoutException e) {

                response = error(
                        "timeout",
                        "The operation timed out. Check availability before retrying."
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                response = error(
                        "interrupted",
                        "The local AI operation was interrupted."
                );

            } catch (ExecutionException e) {

                // Do not forward exception strings because SDK errors may
                // contain user supplied content.
                response = error(
                        "native_failure",
                        "AICore could not complete the operation."
                );

            } catch (LinkageError e) {

                response = error(
                        "native_linkage_error",
                        "The installed native AI libraries could not be loaded."
                );

            } catch (Exception e) {

                response = error(
                        "native_failure",
                        "AICore could not complete the operation."
                );

            } finally {

                if (needsExclusiveLock) {
                    operationBusy.set(false);
                }
            }

            sendResult(result, response);
        });
    }

    private synchronized GenerativeModelFutures client() {

        if (model == null) {

            model = GenerativeModelFutures.from(
                    Generation.INSTANCE.getClient()
            );
        }

        return model;
    }

    private Map<String, Object> checkAvailability()
            throws Exception {

        int nativeStatus =
                client()
                        .checkStatus()
                        .get(30, TimeUnit.SECONDS);

        return status(nativeStatus);
    }

    private Map<String, Object> downloadModel()
            throws Exception {

        GenerativeModelFutures nativeModel = client();

        int before =
                nativeModel
                        .checkStatus()
                        .get(30, TimeUnit.SECONDS);

        if (before != FeatureStatus.DOWNLOADABLE) {
            return status(before);
        }

        nativeModel.download(new DownloadCallback() {

            @Override
            public void onDownloadStarted(long bytesToDownload) {
                // No user data is logged.
            }

            @Override
            public void onDownloadProgress(long totalBytesDownloaded) {
                // Progress UI can be added later.
            }

            @Override
            public void onDownloadCompleted() {
                // Future completion below is authoritative.
            }

            @Override
            public void onDownloadFailed(
                    @NonNull GenAiException e) {
                // Do not forward SDK exception details.
            }

        }).get(15, TimeUnit.MINUTES);

        int after =
                nativeModel
                        .checkStatus()
                        .get(30, TimeUnit.SECONDS);

        return status(after);
    }

    private Map<String, Object> generateText(String prompt)
            throws Exception {

        if (prompt == null || prompt.trim().isEmpty()) {

            return error(
                    "invalid_prompt",
                    "Supply a non-empty prompt."
            );
        }

        /*
         * Google's current Prompt API requires input below
         * the model token limit. This character guard is only
         * a defensive upper bound; token counting can be added later.
         */
        if (prompt.length() > 16000) {

            return error(
                    "prompt_too_long",
                    "Shorten the prompt before retrying."
            );
        }

        GenerativeModelFutures nativeModel = client();

        int availability =
                nativeModel
                        .checkStatus()
                        .get(30, TimeUnit.SECONDS);

        if (availability != FeatureStatus.AVAILABLE) {
            return status(availability);
        }

        GenerateContentResponse response =
                nativeModel
                        .generateContent(prompt)
                        .get(120, TimeUnit.SECONDS);

        List<Candidate> candidates =
                response.getCandidates();

        if (candidates == null || candidates.isEmpty()) {

            return error(
                    "empty_response",
                    "The model returned no text."
            );
        }

        String text =
                candidates.get(0).getText();

        if (text == null || text.trim().isEmpty()) {

            return error(
                    "empty_response",
                    "The model returned no text."
            );
        }

        Map<String, Object> output =
                status(FeatureStatus.AVAILABLE);

        output.put("text", text);

        return output;
    }

    private Map<String, Object> status(int nativeStatus) {

        String value;

        switch (nativeStatus) {

            case FeatureStatus.AVAILABLE:
                value = "ready";
                break;

            case FeatureStatus.DOWNLOADABLE:
                value = "download_required";
                break;

            case FeatureStatus.DOWNLOADING:
                value = "downloading";
                break;

            case FeatureStatus.UNAVAILABLE:
                value = "unavailable";
                break;

            default:
                return error(
                        "unknown_status",
                        "AICore returned an unrecognized capability status."
                );
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("status", value);
        result.put("available", value.equals("ready"));
        result.put("provider", "gemini_nano");

        return result;
    }

    private Map<String, Object> error(
            String code,
            String message) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("status", "error");
        result.put("available", false);
        result.put("provider", "gemini_nano");
        result.put("errorCode", code);
        result.put("message", message);

        return result;
    }

    private void sendResult(
            MethodChannel.Result result,
            Map<String, Object> response) {

        if (mainHandler != null) {

            mainHandler.post(
                    () -> result.success(response)
            );
        }
    }
}
