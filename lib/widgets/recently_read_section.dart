import 'package:flutter/material.dart';

import '../main.dart';
import '../models/reading_progress.dart';
import 'cover_image.dart';

// ---------------------------------------------------------------------------
// Time-ago helper
// ---------------------------------------------------------------------------

String _timeAgo(DateTime date) {
  final diff = DateTime.now().difference(date);
  if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
  if (diff.inHours < 24) return '${diff.inHours}h ago';
  if (diff.inDays < 7) return '${diff.inDays}d ago';
  return '${(diff.inDays / 7).floor()}w ago';
}

// ---------------------------------------------------------------------------
// RecentlyReadSection
// ---------------------------------------------------------------------------

class RecentlyReadSection extends StatelessWidget {
  final List<ReadingProgress> items;
  final void Function(ReadingProgress) onTap;

  const RecentlyReadSection({
    super.key,
    required this.items,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(left: 16, top: 16, bottom: 8),
          child: Text(
            'Recently Read',
            style: TextStyle(
              color: AppTheme.primary,
              fontSize: 14,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        SizedBox(
          height: 220,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: items.length,
            separatorBuilder: (context, index) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              final progress = items[index];
              return _RecentlyReadCard(
                progress: progress,
                onTap: () => onTap(progress),
              );
            },
          ),
        ),
        const SizedBox(height: 8),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Inner card
// ---------------------------------------------------------------------------

class _RecentlyReadCard extends StatelessWidget {
  final ReadingProgress progress;
  final VoidCallback onTap;

  const _RecentlyReadCard({
    required this.progress,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 140,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Cover image
              Expanded(
                child: progress.coverImagePath != null
                    ? CoverImage(
                        sourcePath: progress.coverImagePath!,
                        archiveEntry: progress.coverArchiveEntry,
                        isArchive: progress.coverIsInArchive,
                        iconSize: 32,
                      )
                    : Container(
                        color: AppTheme.surface,
                        child: Center(
                          child: Icon(
                            Icons.menu_book,
                            size: 32,
                            color:
                                AppTheme.onBackground.withValues(alpha: 0.3),
                          ),
                        ),
                      ),
              ),
              // Info area
              Container(
                color: AppTheme.surface,
                padding: const EdgeInsets.fromLTRB(6, 4, 6, 6),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      progress.mangaTitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: AppTheme.onBackground,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Ch. ${progress.chapterIndex + 1} · Pg. ${progress.pageIndex + 1}',
                      style: const TextStyle(
                        color: AppTheme.textSecondary,
                        fontSize: 10,
                      ),
                    ),
                    Text(
                      _timeAgo(progress.lastRead),
                      style: const TextStyle(
                        color: AppTheme.textSecondary,
                        fontSize: 10,
                      ),
                    ),
                    const SizedBox(height: 4),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(2),
                      child: LinearProgressIndicator(
                        value: progress.overallProgress,
                        backgroundColor: AppTheme.background,
                        color: AppTheme.primary,
                        minHeight: 3,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
