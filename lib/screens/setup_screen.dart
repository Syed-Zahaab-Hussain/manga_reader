import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../main.dart';
import '../services/auth_service.dart';
import '../widgets/pin_keypad.dart';

enum _SetupStep { enterPin, confirmPin }

class SetupScreen extends StatefulWidget {
  const SetupScreen({super.key});

  @override
  State<SetupScreen> createState() => _SetupScreenState();
}

class _SetupScreenState extends State<SetupScreen> {
  _SetupStep _step = _SetupStep.enterPin;
  String _firstPin = '';
  String _errorMessage = '';
  bool _biometricAvailable = false;
  bool _biometricEnabled = false;


  final GlobalKey<PinKeypadState> _keypadKey = GlobalKey();

  @override
  void initState() {
    super.initState();
    _checkBiometric();
  }

  Future<void> _checkBiometric() async {
    final available = await AuthService.isBiometricAvailable();
    if (mounted) setState(() => _biometricAvailable = available);
  }

  void _onPinComplete(String pin) async {
    if (_step == _SetupStep.enterPin) {
      setState(() {
        _firstPin = pin;
        _step = _SetupStep.confirmPin;
        _errorMessage = '';
      });
      _keypadKey.currentState?.clear();
    } else if (_step == _SetupStep.confirmPin) {
      if (pin == _firstPin) {
        await AuthService.savePin(pin);
        if (_biometricEnabled) {
          await AuthService.setBiometricEnabled(true);
        }
        if (mounted) context.go('/library');
      } else {
        setState(() {
          _errorMessage = 'PINs do not match. Try again.';
          _step = _SetupStep.enterPin;
          _firstPin = '';
        });
        _keypadKey.currentState?.clear();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            children: [
              const SizedBox(height: 48),
              const Icon(Icons.lock_rounded, size: 48, color: AppTheme.primary),
              const SizedBox(height: 16),
              const Text(
                'Set Up Your PIN',
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.onBackground,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _step == _SetupStep.enterPin
                    ? 'Create a 4–6 digit PIN to protect your library'
                    : 'Enter your PIN again to confirm',
                textAlign: TextAlign.center,
                style: const TextStyle(color: AppTheme.textSecondary),
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
                minLength: 4,
                maxLength: 6,
                onComplete: _onPinComplete,
              ),
              const Spacer(),
              if (_biometricAvailable) ...[
                _BiometricToggle(
                  value: _biometricEnabled,
                  onChanged: (v) => setState(() => _biometricEnabled = v),
                ),
                const SizedBox(height: 24),
              ],
              if (_step == _SetupStep.confirmPin)
                TextButton(
                  onPressed: () {
                    setState(() {
                      _step = _SetupStep.enterPin;
                      _firstPin = '';
                      _errorMessage = '';
                    });
                    _keypadKey.currentState?.clear();
                  },
                  child: const Text(
                    'Back',
                    style: TextStyle(color: AppTheme.textSecondary),
                  ),
                ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );
  }
}

class _BiometricToggle extends StatelessWidget {
  final bool value;
  final ValueChanged<bool> onChanged;

  const _BiometricToggle({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppTheme.surface,
        borderRadius: BorderRadius.circular(12),
      ),
      child: SwitchListTile(
        value: value,
        onChanged: onChanged,
        activeThumbColor: AppTheme.primary,
        activeTrackColor: AppTheme.primary.withAlpha(120),
        title: const Text(
          'Enable Fingerprint Login',
          style: TextStyle(color: AppTheme.onBackground),
        ),
        subtitle: const Text(
          'Use your fingerprint for quick access',
          style: TextStyle(color: AppTheme.textSecondary, fontSize: 12),
        ),
        secondary: const Icon(Icons.fingerprint, color: AppTheme.primary),
      ),
    );
  }
}
