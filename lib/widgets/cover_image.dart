import 'dart:io';

import 'package:flutter/material.dart';

import '../main.dart';
import '../models/manga_item.dart';
import '../services/thumbnail_service.dart';

/// Loads and displays a manga cover thumbnail.
///
/// Accepts either a [MangaItem] or raw paths via [sourcePath]/[archiveEntry].
class CoverImage extends StatefulWidget {
  final MangaItem? manga;
  final String? sourcePath;
  final String? archiveEntry;
  final bool isArchive;
  final double iconSize;

  const CoverImage({
    super.key,
    this.manga,
    this.sourcePath,
    this.archiveEntry,
    this.isArchive = false,
    this.iconSize = 36,
  }) : assert(manga != null || sourcePath != null,
            'Provide either manga or sourcePath');

  @override
  State<CoverImage> createState() => _CoverImageState();
}

class _CoverImageState extends State<CoverImage> {
  Future<File?>? _thumbFuture;

  @override
  void initState() {
    super.initState();
    _loadThumbnail();
  }

  @override
  void didUpdateWidget(CoverImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    final oldId = oldWidget.manga?.id ?? oldWidget.sourcePath;
    final newId = widget.manga?.id ?? widget.sourcePath;
    if (oldId != newId) _loadThumbnail();
  }

  void _loadThumbnail() {
    if (widget.manga != null) {
      _thumbFuture =
          ThumbnailService.instance.getThumbnailFile(widget.manga!);
    } else {
      _thumbFuture = ThumbnailService.instance.getThumbnailFileFromPaths(
        sourcePath: widget.sourcePath!,
        archiveEntry: widget.archiveEntry,
        isArchive: widget.isArchive,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<File?>(
      future: _thumbFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.done &&
            snapshot.data != null) {
          return Image.file(snapshot.data!, fit: BoxFit.cover);
        }
        return Container(
          color: AppTheme.surface,
          child: Center(
            child: Icon(
              Icons.menu_book,
              size: widget.iconSize,
              color: AppTheme.onBackground.withValues(alpha: 0.3),
            ),
          ),
        );
      },
    );
  }
}
