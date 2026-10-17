import 'package:flutter/material.dart';
import 'library_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const VibeApp());
}

class VibeApp extends StatelessWidget {
  const VibeApp({super.key});

  @override
  Widget build(BuildContext context) {
    const violet = Color(0xFF8B5CF6);
    final scheme = const ColorScheme.dark(
      primary: violet,
      surface: Color(0xFF1B1B23),
      onSurface: Color(0xFFF5F5F7),
    );
    return MaterialApp(
      title: 'Vibe Player',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: scheme,
        scaffoldBackgroundColor: const Color(0xFF101014),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF101014),
          elevation: 0,
        ),
        cardTheme: CardTheme(
          color: const Color(0xFF1B1B23),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        ),
      ),
      home: const LibraryPage(),
    );
  }
}
