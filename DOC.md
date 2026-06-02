# Manga Reader Application Documentation

## Overview

**Manga Reader** is a local manga and comic reader for files stored on the user's device. The app lets the user choose a manga folder, scans folder-based manga and ZIP/CBZ archives, builds a visual library, tracks reading progress, and provides a focused reader with scrolling, paging, zoom, and resume support.

The current codebase is a native Android application implemented with Kotlin and Jetpack Compose. This document describes the target product behavior for the native rebuild.

## Platform Support

- **Primary platform**: Android
- **Current project target**: Native Android
- **Current Android storage approach**: storage permissions plus folder picker
- **Orientation**: portrait and landscape are supported by the responsive layouts and reader behavior

## Visual Design

The app uses a dark, reading-focused theme with teal accents.

### Color Scheme

- **Background**: dark green-black `#11221F`
- **Surface areas**: dark teal `#234842`
- **Primary accent**: bright teal `#11D4B4`
- **Primary text**: white `#FFFFFF`
- **Secondary text**: light gray `#B0B0B0`
- **Reader background**: black
- **Danger actions**: red accent
- **Warnings**: amber

### UI Style

- Most app screens use a dark background with surface-colored list sections, cards, dialogs, and input fields.
- The library uses compact rounded cover cards.
- The reader uses a black full-screen canvas with a translucent black top bar.
- Icon buttons are used for navigation, sorting, filtering, search, settings, chapter movement, and reader settings.
- Bottom sheets are used for reader settings and library filters.
- Dialogs are used for scan warnings, destructive confirmations, and backup import confirmation.

---

## Screen Flow

The app has seven main screens:

1. **Splash Screen**
2. **Setup Screen**
3. **Login Screen**
4. **Library Screen**
5. **Detail Screen**
6. **Reader Screen**
7. **Settings Screen**

Routing:

- `/` - Splash
- `/setup` - PIN setup or PIN change
- `/login` - initial unlock
- `/library` - main library
- `/detail` - selected manga detail
- `/reader` - selected chapter reader
- `/settings` - app settings

The app also has two global overlays:

- **Privacy overlay**: shown immediately when the app becomes inactive to hide app-switcher previews.
- **Lock overlay**: shown when returning from the background if a PIN exists, preserving the current navigation stack.

---

## Screens

### Splash Screen

The splash screen appears on launch while the app checks whether a PIN already exists.

It shows:

- App icon/logo
- App name
- Dark themed background

Navigation:

- If no PIN exists, the app goes to **Setup Screen**.
- If a PIN exists, the app goes to **Login Screen**.

### Setup Screen

The setup screen is used both for first-time PIN creation and for changing an existing PIN.

First-time setup:

- User creates a **4-digit PIN**.
- User confirms the PIN.
- If biometrics are available, user can enable **Fingerprint Login**.
- On success, the app opens the Library Screen.

Changing PIN:

- User must first enter the current PIN.
- User then creates and confirms a new 4-digit PIN.

Visible elements:

- Lock icon
- Current step title
- PIN dots and numeric keypad
- Error message area
- Optional fingerprint toggle
- Back button during confirmation step

### Login Screen

The login screen protects access when the app starts and a PIN already exists.

It includes:

- App branding
- PIN keypad
- Biometric unlock option when enabled
- Error feedback for incorrect PIN

Successful unlock sends the user to the Library Screen.

### Library Screen

The library screen is the main browsing screen.

Top bar:

- App title: **Manga Reader**
- Sort button
- Filter button
- Search button
- Settings button

Search:

- Tapping search opens a search field below the top bar.
- Search filters manga by title.
- Closing search clears the query.

Sort options:

- Title A-Z
- Title Z-A
- Most Chapters
- Fewest Chapters
- Last Read

Filter options:

- All
- Unread
- Reading
- Completed

The filter button shows a badge when any filter other than **All** is active.

Library content:

- Recently Read horizontal row appears at the top when progress exists and search is not active.
- Manga are displayed in a responsive grid.
- Each manga card shows cover image, title, chapter count, and a **READING** badge when progress exists.

Recently Read cards show:

- Cover image
- Manga title
- Chapter number
- Page number
- Last-read time such as `5m ago`, `2h ago`, `3d ago`, or `1w ago`
- Overall progress bar

Pull-to-refresh:

- Reloads folder path and reading progress.
- Starts a fresh scan if a folder is selected.

Scan progress:

- Shows `Scanning... current / total`
- Shows a linear progress indicator
- Includes a Cancel button
- Adds discovered manga to the grid in batches while scanning

Scan warnings:

- Corrupted, password-protected, unsupported, or image-less archives are skipped.
- After scan completion, a dialog lists skipped items and messages.

Empty and error states:

- No folder selected: prompts user to open Settings.
- Folder missing/unavailable: offers **Choose folder** and **Try again**.
- Folder selected but no manga found: shows a no-manga message.
- Search/filter with no matches: shows a matching empty message.

### Detail Screen

The detail screen shows a selected manga and its chapters.

Top:

- App bar with back button and manga title
- Large cover banner
- Gradient overlay at the bottom of the cover
- Manga title over the cover

Continue Reading:

- Appears when progress exists.
- Shows the chapter title and page number.
- Opens the reader at the saved chapter and page.

Chapter area:

- Shows total chapter count.
- Includes ascending/descending chapter sort toggle.
- Lists every chapter.

Chapter rows show:

- Chapter title
- Page count
- Progress text when available
- `CONTINUE` badge for in-progress chapters
- `DONE` badge for completed chapters
- Chevron for unread chapters

Tapping a chapter opens the reader. If that chapter has progress, it opens at the saved page.

### Reader Screen

The reader screen is the core reading experience.

Supported reading modes:

- **Vertical scrolling**
- **Horizontal paging**

Reader settings are opened from the tune/settings icon in the top bar.

Top bar:

- Back button
- Current chapter title
- Current page / total pages pill
- Reader settings button
- Previous chapter button
- Next chapter button

The page counter opens a page-jump bottom sheet.

Page jump:

- User enters a page number.
- Reader jumps directly to that page.
- Works in both vertical and horizontal reading modes.

Reader settings:

- Toggle **Zoom**
- Toggle **Page numbers**
- Toggle **Lock reader controls**
- Toggle reading mode between vertical scrolling and horizontal pages
- In horizontal mode, toggle page fit between **fit page width** and **fit whole page**
- Adjust image width from **40% to 100%**

Zoom:

- Disabled by default for natural scrolling.
- When enabled, each page supports pinch-to-zoom and pan.
- Maximum zoom is 4x.
- Parent scrolling/paging is disabled while a page is zoomed in.

Page display:

- Pages are rendered from local image files.
- Broken or missing images show a broken-image placeholder.
- Optional page labels show `current / total` under each page.
- Image decoding uses width-aware caching to reduce memory use.

Reader controls:

- Top bar auto-hides after a few seconds.
- Tapping the reader toggles the top bar.
- When controls are locked, the top bar stays hidden.
- Tapping while locked reveals a temporary **Unlock** button.

System UI:

- When the top bar is visible, system overlays are shown.
- When the top bar is hidden, the status bar is hidden for a more immersive reading view.
- System UI is restored when leaving the reader.

Progress:

- Progress is saved automatically with debounce while reading.
- Progress is saved immediately when leaving the reader or changing chapters.
- Completion is recorded when the user reaches the last page of a chapter.

Chapter navigation:

- Previous/next chapter buttons move between chapters.
- Current chapter progress is saved before switching.
- The next chapter is preloaded near the end of the current chapter.

Reader errors:

- Missing source folder/archive
- Unreadable source
- Corrupted/password-protected/unsupported archive
- No supported images

Errors show a centered state with an icon, title, message, **Go back**, and **Try again**.

### Settings Screen

The settings screen is organized into sections.

#### Library

- **Manga Folder**
  - Shows selected folder path or `No folder selected`.
  - Opens a folder picker.
  - New folder is saved to preferences.
  - User returns to Library and refreshes/rescans.

#### Security

- **Fingerprint Login**
  - Visible only when biometrics are available.
  - Enables/disables biometric unlock.
- **Change PIN**
  - Opens Setup Screen.
  - Requires current PIN before setting a new one.

#### Storage

- **Thumbnail Cache**
  - Shows current thumbnail cache size.
- **Extracted Chapters**
  - Shows size of temporary extracted archive pages.
- **Clear Thumbnail Cache**
  - Deletes cached thumbnails only.
- **Clear Extracted Chapters**
  - Deletes temporary files created when reading archive chapters.
- **Clear All Storage Cache**
  - Deletes thumbnails and extracted chapter files.
  - Does not delete manga files or reading progress.

#### Backup

- **Export Backup**
  - Saves a JSON backup file.
  - Includes reading progress and reader preferences.
  - Default filename format: `manga_reader_backup_YYYYMMDD.json`
- **Import Backup**
  - Lets user choose a JSON backup.
  - Validates that the backup belongs to this app.
  - Restores reader preferences.
  - Merges reading progress.
  - If duplicate progress exists, newer progress wins.
  - Completion state is preserved when either copy marks a chapter completed.

#### Danger Zone

- **Reset App**
  - Requires confirmation.
  - Clears PIN/authentication state.
  - Clears reading progress.
  - Clears thumbnail cache.
  - Clears extracted chapter cache.
  - Sends user back to Setup Screen.

---

## Features Summary

### Authentication and Privacy

- 4-digit PIN setup
- PIN verification on launch
- PIN change flow with current PIN verification
- Optional fingerprint login
- App-switcher privacy overlay when app becomes inactive
- Lock overlay when returning from background
- Lock overlay preserves the current screen instead of resetting navigation

### Library Management

- User-selected manga folder
- Folder picker from Settings
- Automatic scan when no cache exists
- Pull-to-refresh scanning
- Scan progress display
- Cancelable scanning
- Real-time batched library population during scan
- Cached library metadata for faster startup
- Search by title
- Sort by title, chapter count, or last-read time
- Filter by all, unread, reading, or completed
- Recently Read section
- Scan warning dialog for skipped archives

### Reading Experience

- Vertical scrolling mode
- Horizontal paging mode
- Reader settings bottom sheet
- Page jump
- Optional page labels
- Optional pinch-to-zoom and pan
- Zoom max 4x
- Image width control from 40% to 100%
- Horizontal page fit controls
- Auto-hiding reader top bar
- Lockable reader controls
- Temporary unlock button
- Previous/next chapter navigation
- Preload next chapter near chapter end
- Progress auto-save
- Broken-image placeholders

### File Support

- Folder-based manga
- ZIP archives
- CBZ archives
- Supported image extensions:
  - JPG
  - JPEG
  - PNG
  - WEBP
- Natural sorting for manga, chapters, and pages
- Explicit cover file detection
- First image fallback cover detection

### Storage and Cache

- Thumbnail cache in app cache directory
- Thumbnail cache max size: 500 MB
- LRU eviction for thumbnails
- Thumbnail invalidation when source modification time changes
- Archive chapter extraction to temporary files
- Manual clearing of thumbnails
- Manual clearing of extracted chapter files
- Manual clearing of both storage caches

### Backup and Restore

- JSON backup export
- JSON backup import
- Backup schema versioning
- App identity validation
- Reader settings backup
- Reading progress backup
- Merge-based restore

---

## Data Storage

### PIN and Biometrics

- PIN is stored in secure storage.
- On Android, secure storage uses encrypted shared preferences.
- Biometric enabled state is stored in shared preferences.

### App Preferences

Stored preferences include:

- Manga folder path
- Reading direction
- Horizontal page fit
- Reader image width
- Reader controls locked state

### Reading Progress

Reading progress is stored in a hidden JSON file inside the selected manga folder:

```text
.manga_reader_progress.json
```

Progress entries use paths relative to the selected manga root. This makes backups and folder moves safer than storing absolute paths only.

Progress records include:

- Manga ID
- Manga title
- Cover path
- Cover archive metadata
- Chapter index
- Page index
- Total chapters
- Completion flag
- Last-read timestamp

### Library Cache

The app stores scanned manga metadata to avoid rescanning on every launch. The cache is cleared when the selected folder changes.

### Thumbnail Cache

Thumbnails are generated from cover images and saved to disk. Archive covers are read from ZIP/CBZ entries when needed.

### Extracted Archive Pages

Archive chapters are extracted to temporary files before reading. Extracted chapters are cached during use and can be cleared from Settings.

---

## File Organization Rules

### Folder-Based Manga

Style 1 - chapters as subfolders:

```text
Your Manga Folder/
  Manga Title/
    cover.jpg
    Chapter 1/
      001.jpg
      002.jpg
    Chapter 2/
      001.jpg
```

Style 2 - single chapter with images directly in manga folder:

```text
Your Manga Folder/
  Manga Title/
    001.jpg
    002.jpg
    003.jpg
```

Rules:

- Immediate subfolders are treated as chapters.
- If a manga folder contains subfolders, loose root images are ignored except for cover detection.
- If a manga folder has no subfolders, root images become a single chapter.
- The scanner only scans one chapter level deep.
- Deeper nested folders are ignored.

### Archive-Based Manga

Supported archive extensions:

- `.zip`
- `.cbz`

Archive with chapter folders:

```text
manga_title.cbz
  Chapter 1/
    001.jpg
    002.jpg
  Chapter 2/
    001.jpg
```

Flat archive:

```text
manga_title.zip
  001.jpg
  002.jpg
  003.jpg
```

Rules:

- Image entries are natural-sorted.
- Top-level archive folders are treated as chapters.
- Flat archives become one chapter.
- Corrupted, password-protected, unsupported, or image-less archives are skipped and reported as warnings.
- Archive reader extraction ignores files deeper than one level below a chapter prefix.

### Cover Detection

The app looks for cover-like files such as `cover.jpg`, `cover.png`, etc.

Fallbacks:

- Folder manga with chapters: first image of first chapter.
- Single-folder manga: first non-cover image, or first image if every image is cover-like.
- Archive manga: root cover entry if present, otherwise first image of first chapter or flat archive.

---

## Error Handling

- Missing selected folder shows a library unavailable state.
- Storage permission failure prevents scanning or folder selection.
- Unreadable folders show a scan error.
- Corrupted/password-protected/unsupported archives are skipped and listed after scan.
- Archives with no supported images are skipped and listed after scan.
- Missing chapter folder/archive shows a reader error.
- Unreadable chapter source shows a reader error.
- Broken individual page images show a placeholder while the rest of the chapter remains usable.
- Invalid backup files show a format error.
- Backups from another app are rejected.
- Backups from a newer schema version are rejected with an update message.

---

## Performance Notes

### Library

- Scanning runs on a background coroutine dispatcher.
- Results are streamed back as scanner events.
- UI updates are batched to avoid rebuilding for every discovered manga item.
- Sorting is delayed until scan completion.
- Grid rendering is virtualized through Compose lazy grids.
- Covers are loaded lazily.
- Thumbnail cache reduces repeated cover decoding.

### Reader

- Folder chapters are loaded by listing natural-sorted image paths.
- Archive chapters are extracted once to temp storage, then read from extracted files.
- Image dimensions are read before display to preserve aspect ratio.
- Images use cache-width hints based on target display width.
- Each page is wrapped in a repaint boundary.
- Next chapter archive extraction is preloaded near the end of the current chapter.
- Archive temp files can be released per chapter or all at once.

---

## Native Android/Kotlin Rebuild Notes

The app can be fully recreated as a native Android app.

Recommended native equivalents:

- **UI**: Kotlin + Jetpack Compose
- **Navigation**: Navigation Compose
- **Preferences**: DataStore
- **Secure PIN storage**: Jetpack Security / EncryptedSharedPreferences
- **Biometrics**: Android BiometricPrompt
- **Image loading**: Coil
- **ZIP/CBZ**: Zip4j or Java/Kotlin ZIP APIs
- **Background work**: Kotlin coroutines with `Dispatchers.IO`
- **Library lists**: LazyVerticalGrid and LazyRow
- **Reader paging**: HorizontalPager or Compose pager equivalent
- **Folder access**: Storage Access Framework or all-files access depending on distribution needs

Important migration consideration:

- The current app uses broad storage access patterns. A Play Store-friendly Kotlin rebuild should strongly consider Android's Storage Access Framework unless all-files access is truly required.

---

## Kotlin Rebuild Architecture

The rebuild uses a **single Gradle module** with a **layered MVVM** structure:

```
com.example.mangareader/
├── MainActivity.kt
├── MangaReaderApp.kt            # Application class, owns AppContainer
├── di/
│   └── AppContainer.kt          # manual dependency injection
├── core/
│   ├── natural/                 # NaturalSort
│   └── io/                      # path helpers, image extension checks
├── data/
│   ├── preferences/             # DataStore repository (folder path, reader settings)
│   ├── auth/                    # PinRepository (EncryptedSharedPreferences), BiometricManager wrapper
│   ├── progress/                # .manga_reader_progress.json models + repository
│   ├── backup/                  # export/import, schema validation, merge logic
│   └── cache/                   # ThumbnailCache (500 MB LRU), ArchiveExtractCache
├── domain/
│   └── model/                   # MangaItem, ChapterItem, ProgressEntry, ScanWarning
├── scanner/
│   ├── MangaScanner.kt          # Flow<ScanEvent> background scan (folders + archives)
│   ├── ArchiveSource.kt         # Zip4j wrapper: list/extract/skip detection
│   └── CoverDetector.kt         # explicit cover + fallback rules
└── ui/
    ├── theme/
    ├── navigation/
    ├── components/              # shared widgets (MangaCard, PinKeypad, badges)
    └── screens/
        ├── splash/  ├── setup/  ├── login/
        ├── library/ ├── detail/ ├── reader/  └── settings/
        # each screen folder: XScreen.kt + XViewModel.kt (+ private sub-composables)
```

### Patterns

- **MVVM**: one ViewModel per screen exposing `StateFlow<UiState>`; composables collect state with `collectAsStateWithLifecycle()`.
- **Repositories**: ViewModels only talk to repositories; repositories own all file/storage access.
- **Scanner as Flow**: `MangaScanner` emits batched results, progress events, warnings, and supports cancellation through coroutine scopes.
- **Manual DI**: `AppContainer` is constructed in `MangaReaderApp` and passed into ViewModel factories. No Hilt/Koin.

---

## Development Task List for Rebuild

### Phase 1 - Native Foundation

1. Create clean native Android project with Kotlin and Jetpack Compose.
2. Set up app theme with current colors.
3. Add navigation routes for all screens.
4. Add app icon resources from `icon.png`.
5. Add storage, biometric, image loading, archive, and security dependencies.

### Phase 2 - Data and Services

1. Implement preferences storage.
2. Implement secure PIN storage.
3. Implement biometric service.
4. Implement reading progress JSON storage in selected manga folder.
5. Implement backup export/import.
6. Implement library metadata cache.
7. Implement thumbnail cache with 500 MB LRU eviction.
8. Implement extracted archive page cache.

### Phase 3 - Library

1. Implement folder picker and permission flow.
2. Implement folder/archive scanner.
3. Implement natural sorting.
4. Implement scan progress and cancel.
5. Implement scan warning handling.
6. Implement Library Screen grid.
7. Implement Recently Read row.
8. Implement search, sort, and status filters.

### Phase 4 - Reader

1. Implement Detail Screen.
2. Implement vertical scrolling reader.
3. Implement horizontal paging reader.
4. Implement page jump.
5. Implement zoom and pan.
6. Implement reader settings.
7. Implement auto-hiding and lockable controls.
8. Implement progress auto-save.
9. Implement chapter navigation and next-chapter preload.

### Phase 5 - Settings and Polish

1. Implement Settings sections.
2. Implement cache size display and clearing actions.
3. Implement backup import/export UI.
4. Implement reset flow.
5. Implement empty states and reader errors.
6. Verify portrait and landscape layouts.
7. Test large libraries and large archive chapters.
