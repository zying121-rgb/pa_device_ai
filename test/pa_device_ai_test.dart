import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pa_device_ai/pa_device_ai.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('ai.pa/pa_device_ai');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  setUp(() {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
  });
  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
    debugDefaultTargetPlatformOverride = null;
  });

  test(
    'generation sends only the supplied prompt and returns native text',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'generateText');
        expect(call.arguments, {'prompt': 'Exactly this prompt'});
        return {
          'status': 'ready',
          'available': true,
          'provider': 'gemini_nano',
          'text': 'Local answer',
        };
      });
      final result = await PaDeviceAi.generateText('Exactly this prompt');
      expect(result['text'], 'Local answer');
    },
  );

  test('download errors stay errors and do not invoke generation', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      return {
        'status': 'error',
        'available': false,
        'provider': 'gemini_nano',
        'errorCode': 'download_failed',
      };
    });
    expect((await PaDeviceAi.downloadModel())['errorCode'], 'download_failed');
    expect(calls, ['downloadModel']);
  });

  test('an unsupported platform never calls the native channel', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => fail('Unexpected native call'),
    );
    expect((await PaDeviceAi.checkAvailability())['status'], 'unavailable');
  });

  test(
    'platform error does not expose a potentially private native message',
    () async {
      messenger.setMockMethodCallHandler(channel, (_) async {
        throw PlatformException(code: 'failure', message: 'PRIVATE PROMPT');
      });
      final result = await PaDeviceAi.generateText('PRIVATE PROMPT');
      expect(result['status'], 'error');
      expect(result.toString(), isNot(contains('PRIVATE PROMPT')));
    },
  );
}
