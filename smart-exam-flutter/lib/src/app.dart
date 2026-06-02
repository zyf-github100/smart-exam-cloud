import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import 'config/app_config.dart';
import 'features/app/smart_exam_controller.dart';
import 'features/auth/login_page.dart';
import 'features/home/home_page.dart';

class SmartExamApp extends StatefulWidget {
  const SmartExamApp({super.key});

  @override
  State<SmartExamApp> createState() => _SmartExamAppState();
}

class _SmartExamAppState extends State<SmartExamApp> {
  late final SmartExamController _controller;

  @override
  void initState() {
    super.initState();
    _controller = SmartExamController(config: AppConfig.current)..bootstrap();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme =
        ColorScheme.fromSeed(
          seedColor: const Color(0xFF19B7A6),
          brightness: Brightness.light,
        ).copyWith(
          primary: const Color(0xFF19B7A6),
          onPrimary: Colors.white,
          primaryContainer: const Color(0xFFD9F7F3),
          secondary: const Color(0xFF102A43),
          onSecondary: Colors.white,
          tertiary: const Color(0xFFF59E0B),
          surface: const Color(0xFFF8FBFD),
          onSurface: const Color(0xFF10212B),
          onSurfaceVariant: const Color(0xFF5A6B79),
          outline: const Color(0xFFD9E2EC),
          surfaceContainerHighest: const Color(0xFFE7EEF4),
        );

    final baseTextTheme = ThemeData(brightness: Brightness.light).textTheme;
    final textTheme = GoogleFonts.manropeTextTheme(baseTextTheme).apply(
      bodyColor: colorScheme.onSurface,
      displayColor: colorScheme.onSurface,
    );

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        return MaterialApp(
          title: '智能考试',
          debugShowCheckedModeBanner: false,
          theme: ThemeData(
            useMaterial3: true,
            colorScheme: colorScheme,
            scaffoldBackgroundColor: const Color(0xFFF2F6F9),
            textTheme: textTheme.copyWith(
              displayLarge: GoogleFonts.spaceGrotesk(
                textStyle: textTheme.displayLarge,
                fontWeight: FontWeight.w700,
                letterSpacing: -1.8,
              ),
              displayMedium: GoogleFonts.spaceGrotesk(
                textStyle: textTheme.displayMedium,
                fontWeight: FontWeight.w700,
                letterSpacing: -1.2,
              ),
              headlineLarge: GoogleFonts.spaceGrotesk(
                textStyle: textTheme.headlineLarge,
                fontWeight: FontWeight.w700,
                letterSpacing: -1.0,
              ),
              headlineMedium: GoogleFonts.spaceGrotesk(
                textStyle: textTheme.headlineMedium,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.8,
              ),
              titleLarge: textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.w800,
                letterSpacing: -0.5,
              ),
              titleMedium: textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
                letterSpacing: -0.2,
              ),
              bodyLarge: textTheme.bodyLarge?.copyWith(height: 1.5),
              bodyMedium: textTheme.bodyMedium?.copyWith(height: 1.5),
            ),
            appBarTheme: AppBarTheme(
              backgroundColor: Colors.transparent,
              foregroundColor: colorScheme.onSurface,
              elevation: 0,
              scrolledUnderElevation: 0,
            ),
            dividerTheme: DividerThemeData(
              color: colorScheme.outline.withValues(alpha: 0.6),
              thickness: 1,
              space: 1,
            ),
            snackBarTheme: SnackBarThemeData(
              backgroundColor: colorScheme.secondary,
              behavior: SnackBarBehavior.floating,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(18),
              ),
              contentTextStyle: textTheme.bodyMedium?.copyWith(
                color: Colors.white,
                fontWeight: FontWeight.w600,
              ),
            ),
            filledButtonTheme: FilledButtonThemeData(
              style: FilledButton.styleFrom(
                backgroundColor: colorScheme.primary,
                foregroundColor: colorScheme.onPrimary,
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                  vertical: 16,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                ),
                textStyle: textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            outlinedButtonTheme: OutlinedButtonThemeData(
              style: OutlinedButton.styleFrom(
                foregroundColor: colorScheme.onSurface,
                side: BorderSide(color: colorScheme.outline),
                padding: const EdgeInsets.symmetric(
                  horizontal: 18,
                  vertical: 16,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(18),
                ),
                textStyle: textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            inputDecorationTheme: InputDecorationTheme(
              filled: true,
              fillColor: Colors.white.withValues(alpha: 0.88),
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 18,
                vertical: 18,
              ),
              labelStyle: TextStyle(color: colorScheme.onSurfaceVariant),
              hintStyle: TextStyle(color: colorScheme.onSurfaceVariant),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: BorderSide(color: colorScheme.outline),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: BorderSide(color: colorScheme.outline),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: BorderSide(color: colorScheme.primary, width: 1.5),
              ),
              errorBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: const BorderSide(color: Color(0xFFCB3A31)),
              ),
              focusedErrorBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(20),
                borderSide: const BorderSide(
                  color: Color(0xFFCB3A31),
                  width: 1.5,
                ),
              ),
            ),
          ),
          home: _resolveHome(),
        );
      },
    );
  }

  Widget _resolveHome() {
    if (_controller.isBootstrapping) {
      return const _BootSplash();
    }
    if (!_controller.isAuthenticated) {
      return LoginPage(controller: _controller);
    }
    return HomePage(controller: _controller);
  }
}

class _BootSplash extends StatelessWidget {
  const _BootSplash();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFF8FBFD), Color(0xFFEAF4F7), Color(0xFFE7EEF4)],
          ),
        ),
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 86,
                height: 86,
                decoration: BoxDecoration(
                  color: const Color(0xFF102A43),
                  borderRadius: BorderRadius.circular(28),
                ),
                alignment: Alignment.center,
                child: const CircularProgressIndicator(
                  color: Color(0xFF8DEADF),
                  strokeWidth: 2.6,
                ),
              ),
              const SizedBox(height: 24),
              Text('正在恢复登录状态', style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                '正在同步账户信息与考试安排',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
