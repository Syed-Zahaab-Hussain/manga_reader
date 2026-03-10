import 'dart:io';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../main.dart';

/// Requests storage permission on Android.
/// Returns `true` if granted (or not Android).
/// Shows a snackbar on denial if [context] is provided.
Future<bool> requestStoragePermission([BuildContext? context]) async {
  if (!Platform.isAndroid) return true;

  if (await Permission.manageExternalStorage.isGranted) return true;
  if (await Permission.storage.isGranted) return true;

  AppLock.suppressNext = true;
  final manageStatus = await Permission.manageExternalStorage.request();
  if (manageStatus.isGranted) return true;

  AppLock.suppressNext = true;
  final storageStatus = await Permission.storage.request();
  if (storageStatus.isGranted) return true;

  AppLock.suppressNext = false;

  if (context != null && context.mounted) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content:
            const Text('Storage permission is required to read manga files.'),
        backgroundColor: Colors.red.shade700,
        action: SnackBarAction(
          label: 'Settings',
          onPressed: openAppSettings,
        ),
      ),
    );
  }
  return false;
}
