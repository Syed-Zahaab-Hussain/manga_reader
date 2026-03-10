import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'screens/splash_screen.dart';
import 'screens/setup_screen.dart';
import 'screens/login_screen.dart';
import 'screens/library_screen.dart';
import 'screens/detail_screen.dart';
import 'screens/reader_screen.dart';
import 'screens/settings_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MangaReaderApp());
}

final _router = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(path: '/', builder: (context, state) => const SplashScreen()),
    GoRoute(path: '/setup', builder: (context, state) => const SetupScreen()),
    GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
    GoRoute(path: '/library', builder: (context, state) => const LibraryScreen()),
    GoRoute(
      path: '/detail',
      builder: (context, state) => DetailScreen(extra: state.extra),
    ),
    GoRoute(
      path: '/reader',
      builder: (context, state) => ReaderScreen(extra: state.extra),
    ),
    GoRoute(path: '/settings', builder: (context, state) => const SettingsScreen()),
  ],
);

class MangaReaderApp extends StatelessWidget {
  const MangaReaderApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Manga Reader',
      debugShowCheckedModeBanner: false,
      routerConfig: _router,
      theme: AppTheme.dark,
    );
  }
}

class AppTheme {
  static const Color background = Color(0xFF11221F);
  static const Color surface = Color(0xFF234842);
  static const Color primary = Color(0xFF11D4B4);
  static const Color onPrimary = Color(0xFF000000);
  static const Color onBackground = Color(0xFFFFFFFF);
  static const Color onSurface = Color(0xFFFFFFFF);
  static const Color textSecondary = Color(0xFFB0B0B0);

  static final ThemeData dark = ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: background,
    colorScheme: const ColorScheme.dark(
      surface: surface,
      primary: primary,
      onPrimary: onPrimary,
      onSurface: onSurface,
      surfaceContainerHighest: surface,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: background,
      foregroundColor: onBackground,
      elevation: 0,
      centerTitle: true,
    ),
    textTheme: const TextTheme(
      bodyLarge: TextStyle(color: onBackground),
      bodyMedium: TextStyle(color: onBackground),
      bodySmall: TextStyle(color: textSecondary),
      titleLarge: TextStyle(color: onBackground, fontWeight: FontWeight.bold),
      titleMedium: TextStyle(color: onBackground, fontWeight: FontWeight.w600),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: primary,
        foregroundColor: onPrimary,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: surface,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide.none,
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: primary, width: 2),
      ),
      hintStyle: const TextStyle(color: textSecondary),
    ),
    iconTheme: const IconThemeData(color: onBackground),
    dividerColor: surface,
    cardTheme: CardThemeData(
      color: surface,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      elevation: 0,
    ),
  );
}
