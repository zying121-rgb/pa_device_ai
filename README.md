# pa_device_ai

Private Android Flutter plugin for on-device ML Kit Prompt through AICore / Gemini Nano.
This repository contains plugin source only. No application project, harness,
SDK installation, generated outputs, credentials, or cloud AI client is included.
`publish_to: none` prevents publication to pub.dev.

## Public API

```dart
import 'package:pa_device_ai/pa_device_ai.dart';

await PaDeviceAi.checkAvailability();
await PaDeviceAi.downloadModel();
await PaDeviceAi.generateText('Explain in two sentences why market research matters.');
```

MethodChannel: `ai.pa/pa_device_ai`.
Results contain `status`, `available`, and `provider` (`gemini_nano`).
Successful generation also returns `text`; failures include a sanitized error code.

| Native status | Plugin status | available |
| --- | --- | --- |
| AVAILABLE | ready | true |
| DOWNLOADABLE | download_required | false |
| DOWNLOADING | downloading | false |
| UNAVAILABLE | unavailable | false |
| Failure | error | false |

Availability comes from native `checkStatus()`, including immediately before
`generateContent(prompt)`. Android version alone never establishes availability.
Downloads use the native download API; setup requires network access. Inference
has no API key, cloud endpoint, or fallback. Only the supplied prompt is passed
into inference. The plugin contains no mock inference implementation.

## Build requirements and registration

- Dart >=3.8.0 <4.0.0; Flutter >=3.32.0.
- Android minimum SDK 26, compile SDK 36, build tools 36.0.0.
- Consumer application target SDK: 36. Target SDK belongs to the host application.
- Java 17, Android Gradle Plugin 8.11.1, Kotlin Gradle Plugin 2.3.21.
- Native dependency: `com.google.mlkit:genai-prompt:1.0.0-beta4`.
- Coroutines: `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`.

Flutter registers `ai.pa.device.PaDeviceAiPlugin` using the Android plugin entry
in pubspec.yaml. The host Flutter application supplies the Gradle wrapper and
Android application configuration. No wrapper binary or local.properties is
needed in this plugin repository. The host must provide internet permission for
model setup/download. No iOS adapter is included.

## Validation

The unchanged native source previously compiled successfully against the real
Google SDK and Flutter embedding in an independent Gradle 8.13 build. This does
not establish FlutterFlow hosted compiler compatibility or physical inference.
Dart files were parsed by dart format. Flutter analysis, tests and the standalone
APK build were blocked by subprocess access denial in the development environment.
The included four MethodChannel tests are mock contract tests, not device tests.

On a functioning Flutter environment, run `flutter pub get`, `flutter analyze`,
and `flutter test`. Then consume this plugin from an isolated Android Flutter
application and build its APK. Verify actual availability, download, returned
prompt text and offline inference on supported physical hardware. Mock/emulator
results are never proof of Gemini Nano availability. Unsupported devices must
remain unavailable without a cloud fallback.

## Import approval

Do not import into PA until the user approves the exact commit and dependency
configuration. Private GitHub access and any token creation require separate
approval. Pin the reviewed commit in the Git dependency; never include credentials
in its URL. Do not connect production AskPage before physical inference is verified.

## Official references

- https://developers.google.com/ml-kit/genai/prompt/android/get-started
- https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/prompt/GenerativeModel

See FILE_MANIFEST.txt for the complete source upload list.
