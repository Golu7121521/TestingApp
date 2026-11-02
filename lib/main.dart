import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'models/movie_model.dart';
import 'screens/home_screen.dart';
import 'screens/search_screen.dart';
import 'screens/downloads_screen.dart';
import 'screens/my_list_screen.dart';

class CineFlixHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(context)
      ..badCertificateCallback = (X509Certificate cert, String host, int port) => true;
  }
}

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  HttpOverrides.global = CineFlixHttpOverrides();

  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  runApp(const CineFlixApp());
}

class CineFlixApp extends StatelessWidget {
  const CineFlixApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CineFlix',
      theme: ThemeData.dark().copyWith(
        scaffoldBackgroundColor: const Color(0xFF0F0F0F),
        primaryColor: const Color(0xFFE50914),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFE50914),
          surface: Color(0xFF141414),
        ),
      ),
      home: const MainNavigationScreen(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _currentIndex = 0;
  final List<Movie> _savedList = [];

  void _addToList(Movie movie) {
    if (!_savedList.any((m) => m.id == movie.id)) {
      setState(() => _savedList.insert(0, movie));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${movie.title} added to My List'),
          backgroundColor: const Color(0xFFE50914),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  void _removeFromList(int index) {
    if (index >= 0 && index < _savedList.length) {
      setState(() => _savedList.removeAt(index));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: [
          HomeScreen(
            onSearchTap: () => setState(() => _currentIndex = 1),
            onAddToList: _addToList,
          ),
          SearchScreen(
            onAddToList: _addToList,
          ),
          const DownloadsScreen(),
          MyListScreen(
            myList: _savedList,
            onRemove: _removeFromList,
          ),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (i) => setState(() => _currentIndex = i),
        backgroundColor: const Color(0xFF121212),
        selectedItemColor: const Color(0xFFE50914),
        unselectedItemColor: Colors.white54,
        type: BottomNavigationBarType.fixed,
        selectedFontSize: 11,
        unselectedFontSize: 11,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home_filled),
            label: 'Home',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.search),
            label: 'Search',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.download_for_offline_outlined),
            label: 'Downloads',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.video_library_outlined),
            label: 'My List',
          ),
        ],
      ),
    );
  }
}
