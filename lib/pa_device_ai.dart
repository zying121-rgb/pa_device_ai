import 'package:flutter/foundation.dart';

import 'package:flutter/services.dart';

class PaDeviceAi {
  static const MethodChannel _channel = MethodChannel('ai.pa/pa_device_ai');

  static Future<Map<String, dynamic>> checkAvailability() =>
      _invoke('checkAvailability');

  static Future<Map<String, dynamic>> downloadModel() =>
      _invoke('downloadModel');

  static Future<Map<String, dynamic>> generateText(String prompt) =>
      _invoke('generateText', <String, Object?>{'prompt': prompt});

  static Future<Map<String, dynamic>> _invoke(
    String method, [

    Map<String, Object?>? arguments,
  ]) async {
    if (kIsWeb ||
        (defaultTargetPlatform != TargetPlatform.android &&
            defaultTargetPlatform != TargetPlatform.iOS)) {
      return <String, dynamic>{
        'status': 'unavailable',

        'available': false,

        'provider': 'unsupported',

        'message':
            'On-device AI is currently supported on Android and iOS only.',
      };
    }

    try {
      final response = await _channel.invokeMapMethod<String, dynamic>(
        method,

        arguments,
      );

      if (response == null) {
        return _error('invalid_response');
      }

      return Map<String, dynamic>.from(response);
    } on MissingPluginException {
      return _error('plugin_not_registered');
    } on PlatformException catch (e) {
      return _error(e.code);
    } catch (_) {
      return _error('unknown_error');
    }
  }

  static String get _provider {
    if (!kIsWeb) {
      if (defaultTargetPlatform == TargetPlatform.android) {
        return 'gemini_nano';
      }

      if (defaultTargetPlatform == TargetPlatform.iOS) {
        return 'apple_foundation_models';
      }
    }

    return 'unsupported';
  }

  static Map<String, dynamic> _error(String code) => <String, dynamic>{
    'status': 'error',

    'available': false,

    'provider': _provider,

    'errorCode': code,

    'message': 'The on-device AI adapter could not complete this request.',
  };
}
