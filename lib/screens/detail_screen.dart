import 'package:flutter/material.dart';

class DetailScreen extends StatelessWidget {
  final Object? extra;
  const DetailScreen({super.key, this.extra});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(child: Text('Detail')),
    );
  }
}
