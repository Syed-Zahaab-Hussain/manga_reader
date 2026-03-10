import 'package:flutter/material.dart';
import '../main.dart';

/// Reusable numeric keypad + PIN dot display.
/// Calls [onComplete] when [pinLength] digits have been entered.
/// Calls [onChanged] on every keystroke (optional).
class PinKeypad extends StatefulWidget {
  final int minLength;
  final int maxLength;
  final void Function(String pin) onComplete;
  final void Function(String pin)? onChanged;

  const PinKeypad({
    super.key,
    this.minLength = 4,
    this.maxLength = 6,
    required this.onComplete,
    this.onChanged,
  });

  @override
  State<PinKeypad> createState() => PinKeypadState();
}

class PinKeypadState extends State<PinKeypad> {
  String _pin = '';

  String get currentPin => _pin;

  void clear() => setState(() => _pin = '');

  void _press(String digit) {
    if (_pin.length >= widget.maxLength) return;
    setState(() => _pin += digit);
    widget.onChanged?.call(_pin);
    if (_pin.length >= widget.minLength) {
      widget.onComplete(_pin);
    }
  }

  void _backspace() {
    if (_pin.isEmpty) return;
    setState(() => _pin = _pin.substring(0, _pin.length - 1));
    widget.onChanged?.call(_pin);
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _PinDots(entered: _pin.length, max: widget.maxLength),
        const SizedBox(height: 32),
        _buildGrid(),
      ],
    );
  }

  Widget _buildGrid() {
    const keys = [
      ['1', '2', '3'],
      ['4', '5', '6'],
      ['7', '8', '9'],
      ['', '0', '⌫'],
    ];

    return Column(
      children: keys.map((row) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: row.map((k) {
              if (k.isEmpty) return const SizedBox(width: 80, height: 80);
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: _KeyButton(
                  label: k,
                  onTap: k == '⌫' ? _backspace : () => _press(k),
                  isBackspace: k == '⌫',
                ),
              );
            }).toList(),
          ),
        );
      }).toList(),
    );
  }
}

class _PinDots extends StatelessWidget {
  final int entered;
  final int max;

  const _PinDots({required this.entered, required this.max});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: List.generate(max, (i) {
        final filled = i < entered;
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 150),
            width: 16,
            height: 16,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: filled ? AppTheme.primary : Colors.transparent,
              border: Border.all(
                color: filled ? AppTheme.primary : AppTheme.textSecondary,
                width: 2,
              ),
            ),
          ),
        );
      }),
    );
  }
}

class _KeyButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final bool isBackspace;

  const _KeyButton({
    required this.label,
    required this.onTap,
    this.isBackspace = false,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(40),
        splashColor: AppTheme.primary.withAlpha(60),
        child: Container(
          width: 80,
          height: 80,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: AppTheme.surface,
          ),
          child: isBackspace
              ? const Icon(Icons.backspace_outlined, color: AppTheme.onBackground, size: 24)
              : Text(
                  label,
                  style: const TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w500,
                    color: AppTheme.onBackground,
                  ),
                ),
        ),
      ),
    );
  }
}
