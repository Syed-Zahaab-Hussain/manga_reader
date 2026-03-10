import 'package:flutter/material.dart';

class ReaderScreen extends StatelessWidget {
  final Object? extra;
  const ReaderScreen({super.key, this.extra});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(child: Text('Reader')),
    );
  }
}
