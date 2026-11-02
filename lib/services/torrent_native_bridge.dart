import 'package:flutter/services.dart';

class TorrentNativeBridge {
  static const MethodChannel _channel = MethodChannel('com.desiflix/torrent_engine');

  /// Native Kotlin engine ko magnet bhej kar buffered video path lena
  static Future<String?> startBuffering(String magnetUri) async {
    try {
      final res = await _channel.invokeMethod('startStream', {
        'magnet': magnetUri,
      });

      if (res != null && res is Map && res['status'] == 'ready') {
        return res['filePath'] as String?;
      }
    } on PlatformException catch (_) {
      return null;
    } catch (_) {
      return null;
    }
    return null;
  }

  /// App close ya cancel par native torrent session stop karna
  static Future<void> stop() async {
    try {
      await _channel.invokeMethod('stopStream');
    } catch (_) {}
  }
}
