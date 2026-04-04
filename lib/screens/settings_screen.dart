import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../main.dart';
import '../services/app_preferences.dart';
import '../services/auth_service.dart';
import '../services/progress_service.dart';
import '../services/thumbnail_service.dart';
import '../utils/storage_permission.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  String? _folderPath;
  bool _biometricAvailable = false;
  bool _biometricEnabled = false;
  int _cacheSizeBytes = 0;
  bool _isLoadingCache = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final results = await Future.wait([
      AppPreferences.getMangaFolderPath(),
      AuthService.isBiometricAvailable(),
      AuthService.isBiometricEnabled(),
      ThumbnailService.instance.getCacheSizeBytes(),
    ]);
    if (!mounted) return;
    setState(() {
      _folderPath = results[0] as String?;
      _biometricAvailable = results[1] as bool;
      _biometricEnabled = results[2] as bool;
      _cacheSizeBytes = results[3] as int;
      _isLoadingCache = false;
    });
  }

  // -------------------------------------------------------------------------
  // Actions
  // -------------------------------------------------------------------------

  Future<void> _changeFolder() async {
    if (!await requestStoragePermission(context)) return;

    AppLock.suppressNext = true;
    final result = await FilePicker.platform.getDirectoryPath(
      dialogTitle: 'Select Manga Folder',
    );
    if (result == null) AppLock.suppressNext = false;
    if (result == null || !mounted) return;

    await AppPreferences.setMangaFolderPath(result);
    if (!mounted) return;

    setState(() => _folderPath = result);
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Folder updated. Pull to refresh the library.'),
      ),
    );
  }

  Future<void> _toggleBiometric(bool value) async {
    await AuthService.setBiometricEnabled(value);
    if (!mounted) return;
    setState(() => _biometricEnabled = value);
  }

  Future<void> _clearCache() async {
    await ThumbnailService.instance.clearCache();
    if (!mounted) return;
    final size = await ThumbnailService.instance.getCacheSizeBytes();
    if (!mounted) return;
    setState(() => _cacheSizeBytes = size);
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Thumbnail cache cleared.')),
    );
  }

  Future<void> _confirmReset() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text(
          'Reset App',
          style: TextStyle(color: AppTheme.onBackground),
        ),
        content: const Text(
          'This will delete your PIN, all reading progress, and the thumbnail '
          'cache. This cannot be undone.',
          style: TextStyle(color: AppTheme.textSecondary),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text(
              'Cancel',
              style: TextStyle(color: AppTheme.textSecondary),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text(
              'Reset',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    await Future.wait([
      AuthService.clearAuth(),
      ProgressService.clearAll(),
      ThumbnailService.instance.clearCache(),
    ]);

    if (!mounted) return;
    context.go('/setup');
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  String _formatBytes(int bytes) {
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  // -------------------------------------------------------------------------
  // Build
  // -------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        title: const Text('Settings'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          _buildSection('Library', [
            _buildActionTile(
              icon: Icons.folder_outlined,
              title: 'Manga Folder',
              subtitle: _folderPath ?? 'No folder selected',
              onTap: _changeFolder,
            ),
          ]),
          const SizedBox(height: 20),
          _buildSection('Security', [
            if (_biometricAvailable) ...[
              _buildSwitchTile(
                icon: Icons.fingerprint,
                title: 'Fingerprint Login',
                value: _biometricEnabled,
                onChanged: _toggleBiometric,
              ),
              _buildDivider(),
            ],
            _buildActionTile(
              icon: Icons.lock_reset,
              title: 'Change PIN',
              onTap: () => context.push('/setup'),
            ),
          ]),
          const SizedBox(height: 20),
          _buildSection('Storage', [
            _buildInfoTile(
              icon: Icons.image_outlined,
              title: 'Thumbnail Cache',
              subtitle: _isLoadingCache
                  ? 'Calculating...'
                  : _formatBytes(_cacheSizeBytes),
            ),
            _buildDivider(),
            _buildActionTile(
              icon: Icons.delete_sweep_outlined,
              title: 'Clear Thumbnail Cache',
              onTap: _clearCache,
            ),
          ]),
          const SizedBox(height: 20),
          _buildSection('Danger Zone', [
            _buildActionTile(
              icon: Icons.restore,
              title: 'Reset App',
              subtitle: 'Clears PIN, reading progress & cache',
              textColor: Colors.redAccent,
              iconColor: Colors.redAccent,
              onTap: _confirmReset,
            ),
          ]),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Text(
            title.toUpperCase(),
            style: const TextStyle(
              color: AppTheme.primary,
              fontSize: 11,
              fontWeight: FontWeight.w700,
              letterSpacing: 1.2,
            ),
          ),
        ),
        Container(
          decoration: BoxDecoration(
            color: AppTheme.surface,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Column(children: children),
        ),
      ],
    );
  }

  Widget _buildDivider() => const Divider(
        height: 1,
        indent: 52,
        color: Color(0x22FFFFFF),
      );

  Widget _buildInfoTile({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return ListTile(
      leading: Icon(icon, color: AppTheme.textSecondary, size: 22),
      title: Text(
        title,
        style: const TextStyle(color: AppTheme.onBackground, fontSize: 15),
      ),
      subtitle: Text(
        subtitle,
        style: const TextStyle(color: AppTheme.textSecondary, fontSize: 12),
        overflow: TextOverflow.ellipsis,
        maxLines: 1,
      ),
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required String title,
    String? subtitle,
    Color? textColor,
    Color? iconColor,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: Icon(icon, color: iconColor ?? AppTheme.onBackground, size: 22),
      title: Text(
        title,
        style:
            TextStyle(color: textColor ?? AppTheme.onBackground, fontSize: 15),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle,
              style: const TextStyle(
                  color: AppTheme.textSecondary, fontSize: 12),
            )
          : null,
      trailing: const Icon(Icons.chevron_right,
          color: AppTheme.textSecondary, size: 20),
      onTap: onTap,
    );
  }

  Widget _buildSwitchTile({
    required IconData icon,
    required String title,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return SwitchListTile(
      secondary: Icon(icon, color: AppTheme.onBackground, size: 22),
      title: Text(
        title,
        style: const TextStyle(color: AppTheme.onBackground, fontSize: 15),
      ),
      value: value,
      onChanged: onChanged,
      activeThumbColor: AppTheme.primary,
      activeTrackColor: AppTheme.primary.withAlpha(120),
    );
  }
}
