import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../main.dart';
import '../services/app_preferences.dart';
import '../services/auth_service.dart';
import '../services/backup_service.dart';
import '../services/page_loader_service.dart';
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
  int _extractedPagesSizeBytes = 0;
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
      PageLoaderService.instance.getExtractedPagesSizeBytes(),
    ]);
    if (!mounted) return;
    setState(() {
      _folderPath = results[0] as String?;
      _biometricAvailable = results[1] as bool;
      _biometricEnabled = results[2] as bool;
      _cacheSizeBytes = results[3] as int;
      _extractedPagesSizeBytes = results[4] as int;
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
    AppLock.suppressNext = false;
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
    await _refreshStorageSizes();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Thumbnail cache cleared.')),
    );
  }

  Future<void> _clearExtractedPages() async {
    await PageLoaderService.instance.clearExtractedPages();
    if (!mounted) return;
    await _refreshStorageSizes();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Extracted chapter files cleared.')),
    );
  }

  Future<void> _clearStorageCache() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text(
          'Clear storage cache?',
          style: TextStyle(color: AppTheme.onBackground),
        ),
        content: const Text(
          'This clears thumbnails and temporary extracted chapter files. Your '
          'manga files and reading progress will not be deleted.',
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
            child: const Text('Clear'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    await Future.wait([
      ThumbnailService.instance.clearCache(),
      PageLoaderService.instance.clearExtractedPages(),
    ]);
    if (!mounted) return;
    await _refreshStorageSizes();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Storage cache cleared.')),
    );
  }

  Future<void> _exportBackup() async {
    try {
      final bytes = await BackupService.createBackupBytes();
      final now = DateTime.now();
      final fileName =
          'manga_reader_backup_${now.year}${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}.json';

      AppLock.suppressNext = true;
      final outputPath = await FilePicker.platform.saveFile(
        dialogTitle: 'Export Backup',
        fileName: fileName,
        type: FileType.custom,
        allowedExtensions: const ['json'],
        bytes: bytes,
      );
      AppLock.suppressNext = false;

      if (outputPath == null || !mounted) return;

      try {
        final file = File(outputPath);
        if (!file.existsSync() || file.lengthSync() == 0) {
          await file.writeAsBytes(bytes);
        }
      } catch (_) {}

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Backup exported.')),
      );
    } catch (_) {
      AppLock.suppressNext = false;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not export backup.')),
      );
    }
  }

  Future<void> _importBackup() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppTheme.surface,
        title: const Text(
          'Import backup?',
          style: TextStyle(color: AppTheme.onBackground),
        ),
        content: const Text(
          'This will merge reading progress and restore reader preferences. '
          'Your manga folder will not be changed. If the same chapter exists '
          'in both places, the newer progress wins.',
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
            child: const Text('Choose File'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      AppLock.suppressNext = true;
      final result = await FilePicker.platform.pickFiles(
        dialogTitle: 'Import Backup',
        type: FileType.custom,
        allowedExtensions: const ['json'],
        withData: true,
      );
      AppLock.suppressNext = false;

      final file = result?.files.single;
      final bytes = file?.bytes ??
          (file?.path != null ? await File(file!.path!).readAsBytes() : null);
      if (bytes == null) return;

      final importResult = await BackupService.importBackupBytes(bytes);
      if (!mounted) return;

      await _loadSettings();
      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Backup imported. Updated ${importResult.importedProgressEntries} progress entries.',
          ),
        ),
      );
    } on FormatException catch (error) {
      AppLock.suppressNext = false;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message)),
      );
    } catch (_) {
      AppLock.suppressNext = false;
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not import backup.')),
      );
    }
  }

  Future<void> _refreshStorageSizes() async {
    final results = await Future.wait([
      ThumbnailService.instance.getCacheSizeBytes(),
      PageLoaderService.instance.getExtractedPagesSizeBytes(),
    ]);
    if (!mounted) return;
    setState(() {
      _cacheSizeBytes = results[0];
      _extractedPagesSizeBytes = results[1];
      _isLoadingCache = false;
    });
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
          'and extracted chapter caches. This cannot be undone.',
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
      PageLoaderService.instance.clearExtractedPages(),
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
            _buildInfoTile(
              icon: Icons.inventory_2_outlined,
              title: 'Extracted Chapters',
              subtitle: _isLoadingCache
                  ? 'Calculating...'
                  : _formatBytes(_extractedPagesSizeBytes),
            ),
            _buildDivider(),
            _buildActionTile(
              icon: Icons.delete_sweep_outlined,
              title: 'Clear Thumbnail Cache',
              onTap: _clearCache,
            ),
            _buildDivider(),
            _buildActionTile(
              icon: Icons.cleaning_services_outlined,
              title: 'Clear Extracted Chapters',
              subtitle: 'Temporary files from archive reading',
              onTap: _clearExtractedPages,
            ),
            _buildDivider(),
            _buildActionTile(
              icon: Icons.auto_delete_outlined,
              title: 'Clear All Storage Cache',
              subtitle: 'Thumbnails and extracted chapters',
              onTap: _clearStorageCache,
            ),
          ]),
          const SizedBox(height: 20),
          _buildSection('Backup', [
            _buildActionTile(
              icon: Icons.upload_file_outlined,
              title: 'Export Backup',
              subtitle: 'Reading progress and preferences',
              onTap: _exportBackup,
            ),
            _buildDivider(),
            _buildActionTile(
              icon: Icons.download_for_offline_outlined,
              title: 'Import Backup',
              subtitle: 'Safely merge progress from a JSON backup',
              onTap: _importBackup,
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
