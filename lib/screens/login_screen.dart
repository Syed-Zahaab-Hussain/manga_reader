import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../main.dart';
import '../services/auth_service.dart';
import '../widgets/pin_keypad.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final GlobalKey<PinKeypadState> _keypadKey = GlobalKey();
  String _errorMessage = '';
  bool _biometricEnabled = false;
  bool _biometricAvailable = false;

  @override
  void initState() {
    super.initState();
    _initBiometric();
  }

  Future<void> _initBiometric() async {
    final available = await AuthService.isBiometricAvailable();
    final enabled = await AuthService.isBiometricEnabled();
    if (!mounted) return;
    setState(() {
      _biometricAvailable = available;
      _biometricEnabled = enabled;
    });
    if (available && enabled) {
      // Small delay so the screen renders first
      await Future.delayed(const Duration(milliseconds: 300));
      if (mounted) _tryBiometric();
    }
  }

  Future<void> _tryBiometric() async {
    final success = await AuthService.authenticateWithBiometric();
    if (!mounted) return;
    if (success) {
      context.go('/library');
    }
  }

  Future<void> _onPinComplete(String pin) async {
    final valid = await AuthService.verifyPin(pin);
    if (!mounted) return;
    if (valid) {
      context.go('/library');
    } else {
      setState(() => _errorMessage = 'Incorrect PIN. Try again.');
      _keypadKey.currentState?.clear();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            children: [
              const SizedBox(height: 48),
              // Logo placeholder
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: AppTheme.surface,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: const Icon(
                  Icons.menu_book_rounded,
                  size: 44,
                  color: AppTheme.primary,
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Manga Reader',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.onBackground,
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                'Enter your PIN',
                style: TextStyle(color: AppTheme.textSecondary),
              ),
              const SizedBox(height: 8),
              AnimatedOpacity(
                opacity: _errorMessage.isNotEmpty ? 1.0 : 0.0,
                duration: const Duration(milliseconds: 200),
                child: Text(
                  _errorMessage,
                  style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                ),
              ),
              const SizedBox(height: 32),
              PinKeypad(
                key: _keypadKey,
                pinLength: 4,
                onComplete: _onPinComplete,
              ),
              const SizedBox(height: 32),
              if (_biometricAvailable && _biometricEnabled)
                _BiometricButton(onTap: _tryBiometric),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}

class _BiometricButton extends StatelessWidget {
  final VoidCallback onTap;
  const _BiometricButton({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: AppTheme.surface,
              shape: BoxShape.circle,
              border: Border.all(color: AppTheme.primary, width: 2),
            ),
            child: const Icon(Icons.fingerprint, size: 36, color: AppTheme.primary),
          ),
          const SizedBox(height: 8),
          const Text(
            'Use Fingerprint',
            style: TextStyle(color: AppTheme.textSecondary, fontSize: 13),
          ),
        ],
      ),
    );
  }
}
