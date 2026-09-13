import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Android-only, stateless public interface. No network client or API key.
class PaDeviceAi {
  static const _channel = MethodChannel('ai.pa/pa_device_ai');

  static Future<Map<String, dynamic>> checkAvailability() =>
      _invoke('checkAvailability');

  /// Waits for native download completion/failure. Call checkAvailability
  /// separately to observe a download already in progress.
  static Future<Map<String, dynamic>> downloadModel() =>
      _invoke('downloadModel');

  /// Sends exactly [prompt] to the native on-device model.
  static Future<Map<String, dynamic>> generateText(String prompt) =>
      _invoke('generateText', {'prompt': prompt});

  static Future<Map<String, dynamic>> _invoke(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      return {
        'status': 'unavailable',
        'available': false,
        'provider': 'gemini_nano',
        'message': 'This adapter supports Android only.',
      };
    }
    try {
      final response = await _channel.invokeMapMethod<String, dynamic>(
        method,
        arguments,
      );
      if (response == null) return _error('invalid_response');
      return response;
    } on MissingPluginException {
      return _error('plugin_not_registered');
    } on PlatformException catch (e) {
      // Never surface a native message that might contain supplied prompt text.
      return _error(e.code);
    }
  }

  static Map<String, dynamic> _error(String code) => {
    'status': 'error',
    'available': false,
    'provider': 'gemini_nano',
    'errorCode': code,
    'message': 'The Android AI adapter could not complete this request.',
  };
}
