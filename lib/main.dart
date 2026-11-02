import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'models/movie_model.dart';
import 'services/download_service.dart';
import 'screens/home_screen.dart';
import 'screens/search_screen.dart';
import 'screens/downloads_screen.dart';
import 'screens/my_list_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await DownloadManager.instance.init();

  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));

  runApp(const CineFlixApp());
}

class CineFlixApp extends StatelessWidget {
  const CineFlixApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CineFlix',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F0F0F),
        primaryColor: const Color(0xFFE50914),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFE50914),
          surface: Color(0xFF141414),
        ),
        useMaterial3: true,
      ),
      home: const MainNavigationScreen(),
    );
  }
}

class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  final List<Movie> _myList = [];

  void _addToList(Movie movie) {
    setState(() {
      if (!_myList.any((m) => m.id == movie.id)) {
        _myList.add(movie);
      }
    });
  }

  void _removeFromList(int movieId) {
    setState(() {
      _myList.removeWhere((m) => m.id == movieId);
    });
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: RootNav.index,
      builder: (context, currentIndex, _) {
        final List<Widget> screens = [
          HomeScreen(
            onAddToList: _addToList,
            onSearchTap: () => RootNav.index.value = 1,
          ),
          SearchScreen(onAddToList: _addToList),
          const DownloadsScreen(),
          MyListScreen(
            myList: _myList,
            onRemove: _removeFromList,
          ),
        ];

        return Scaffold(
          body: IndexedStack(
            index: currentIndex,
            children: screens,
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: currentIndex,
            backgroundColor: const Color(0xFF121212),
            indicatorColor: const Color(0xFFE50914).withOpacity(0.2),
            onDestinationSelected: (idx) => RootNav.index.value = idx,
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.home_outlined, color: Colors.white70),
                selectedIcon: Icon(Icons.home_filled, color: Color(0xFFE50914)),
                label: 'Home',
              ),
              NavigationDestination(
                icon: Icon(Icons.search_outlined, color: Colors.white70),
                selectedIcon: Icon(Icons.search, color: Color(0xFFE50914)),
                label: 'Search',
              ),
              NavigationDestination(
                icon: Icon(Icons.download_outlined, color: Colors.white70),
                selectedIcon: Icon(Icons.download_done_rounded, color: Color(0xFFE50914)),
                label: 'Downloads',
              ),
              NavigationDestination(
                icon: Icon(Icons.bookmark_outline_rounded, color: Colors.white70),
                selectedIcon: Icon(Icons.bookmark_rounded, color: Color(0xFFE50914)),
                label: 'My List',
              ),
            ],
          ),
        );
      },
    );
  }
}
