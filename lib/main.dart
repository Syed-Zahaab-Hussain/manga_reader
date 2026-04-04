import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'screens/splash_screen.dart';
import 'screens/setup_screen.dart';
import 'screens/login_screen.dart';
import 'screens/library_screen.dart';
import 'screens/detail_screen.dart';
import 'screens/reader_screen.dart';
import 'screens/settings_screen.dart';
import 'services/auth_service.dart';
import 'widgets/unlock_panel.dart';

class AppLock {
  static bool suppressNext = false;
}

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

class MangaReaderApp extends StatefulWidget {
  const MangaReaderApp({super.key});

  @override
  State<MangaReaderApp> createState() => _MangaReaderAppState();
}

class _MangaReaderAppState extends State<MangaReaderApp>
    with WidgetsBindingObserver {
  bool _wasInBackground = false;
  // Shown immediately on inactive — covers app-switcher preview
  bool _showPrivacyOverlay = false;
  // Shown instead of navigating to /login — preserves nav stack
  bool _showLockOverlay = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.inactive:
        // Fires before paused AND when returning from background (before resumed).
        // Show privacy overlay immediately so app-switcher screenshot is blocked.
        if (!_showLockOverlay) {
          setState(() => _showPrivacyOverlay = true);
        }
      case AppLifecycleState.paused:
        _wasInBackground = true;
      case AppLifecycleState.resumed:
        if (_wasInBackground) {
          _wasInBackground = false;
          _lockIfNeeded();
        } else {
          // Returning from something minor (notification shade, etc.) — just
          // hide the privacy overlay without requiring authentication.
          setState(() => _showPrivacyOverlay = false);
        }
      default:
        break;
    }
  }

  Future<void> _lockIfNeeded() async {
    if (AppLock.suppressNext) {
      AppLock.suppressNext = false;
      setState(() => _showPrivacyOverlay = false);
      return;
    }
    final hasPin = await AuthService.hasPin();
    if (!mounted) return;
    if (hasPin) {
      // Show lock overlay over whatever screen the user was on.
      // Privacy overlay is no longer needed once the lock is visible.
      setState(() {
        _showLockOverlay = true;
        _showPrivacyOverlay = false;
      });
    } else {
      setState(() => _showPrivacyOverlay = false);
    }
  }

  void _onUnlocked() {
    setState(() => _showLockOverlay = false);
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      textDirection: TextDirection.ltr,
      children: [
        MaterialApp.router(
          title: 'Manga Reader',
          debugShowCheckedModeBanner: false,
          routerConfig: _router,
          theme: AppTheme.dark,
        ),
        if (_showPrivacyOverlay)
          const _PrivacyOverlay(),
        if (_showLockOverlay)
          _LockOverlay(onUnlocked: _onUnlocked),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Privacy overlay — blank screen shown immediately when app goes inactive
// ---------------------------------------------------------------------------

class _PrivacyOverlay extends StatelessWidget {
  const _PrivacyOverlay();

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Container(
        color: AppTheme.background,
        alignment: Alignment.center,
        child: const Icon(
          Icons.menu_book_rounded,
          size: 64,
          color: AppTheme.primary,
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Lock overlay — PIN + biometric, dismisses in place without nav stack reset
// ---------------------------------------------------------------------------

class _LockOverlay extends StatelessWidget {
  final VoidCallback onUnlocked;
  const _LockOverlay({required this.onUnlocked});

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Container(
        color: AppTheme.background,
        child: SafeArea(
          child: UnlockPanel(onUnlocked: onUnlocked),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------

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
