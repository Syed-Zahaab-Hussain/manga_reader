# Manga Reader Application Documentation

## Overview

**Manga Reader** is a mobile application designed for reading manga and comics stored locally on your device. The app scans your device's storage to find manga folders and comic archives, organizes them into a library, and provides a smooth reading experience with vertical scroll viewing.

## Application Purpose

The main purpose of this application is to provide a clean, organized way to read manga and comics that you have downloaded and stored on your device. Instead of manually browsing through folders, the app automatically scans and organizes your collection, making it easy to find and read your favorite titles.

## Platform Support

- **Android**: Android 10 (API 29) and above
- **iOS**: Supported
- **Orientation**: Both portrait and landscape orientations are fully supported across all screens

## User Interface

### Visual Design

The application uses a dark theme with teal/turquoise accent colors to reduce eye strain during reading sessions. The background is a dark greenish-black color, while interactive elements use a bright teal color to stand out clearly.

### Color Scheme

- **Background**: Dark greenish-black (#11221F)
- **Surface Areas**: Dark teal (#234842)
- **Primary Accent**: Bright teal/turquoise (#11D4B4)
- **Text**: White and light gray tones

### Screen Flow

The application consists of the following main screens:

1. **Splash Screen** - Brief branded loading screen shown on app launch
2. **Setup Screen** - First-time setup for new users
3. **Login Screen** - Authentication gate to access the app
4. **Library Screen** - Main screen showing your manga collection
5. **Detail Screen** - Detailed view of a selected manga with chapter list
6. **Reader Screen** - The actual manga reading interface
7. **Settings Screen** - App configuration, authentication settings, and folder management

---

## Screens Overview

### Splash Screen

The splash screen is the first thing you see when launching the app. It displays for 1-2 seconds while the app checks your authentication state and loads saved library data in the background.

It shows:

- App logo/icon centered on the dark background
- App name below the logo

No user interaction is required. The app automatically navigates to:

- **Setup Screen** — if this is the first launch and no PIN has been created
- **Login Screen** — if a PIN already exists

### Setup Screen

When you first install and launch the app, you will be greeted with a setup screen that guides you through securing your manga library. This screen appears only once when the app is used for the first time.

On this screen, you will:

- Create a 4-6 digit PIN to protect your manga library
- Confirm your PIN by entering it again
- Optionally enable biometric authentication (fingerprint only) for quick access

The setup process is straightforward: enter your chosen PIN, confirm it, and decide whether to enable biometric login. This ensures that your reading history and library remain private.

### Login Screen

Every time you open the app, you will be presented with a login screen to protect your content. This is your gateway to the application.

On this screen, you will:

- Enter your 6-digit PIN to access your library
- Use biometric authentication (if enabled) for quick one-touch access
- See the app icon and title while authenticating

The login screen provides a secure barrier, ensuring only authorized users can access your manga collection and reading progress.

### Library Screen

The library screen is the main hub of the application. It displays your entire manga collection in an organized grid layout. This is where you spend most of your time browsing and selecting what to read.

**Top Bar** (left to right):

- **App name** - Displayed on the left
- **Sort** - Sort your library by title (A-Z or Z-A) or by number of chapters (most or least)
- **Search** - Tap to search for specific manga by title
- **Settings** - Tap to navigate to the settings screen

**Refreshing the Library**

Pull down on the library screen to trigger a reload/rescan of your manga collection, similar to pull-to-refresh in a browser. This re-reads your currently selected folder and updates the library with any new or removed content.

**Scan Progress**

While a scan is running (on pull-to-refresh or after selecting a new folder in Settings), a progress indicator is shown below the top bar displaying:

- **Scanned count** — e.g., "Scanning... 12 / 47"
- **Cancel button** — stops the scan immediately; the library shows whatever was found up to that point

The library grid populates in real time as each manga is discovered, so you do not need to wait for the full scan to finish before browsing.

Below the header, your manga collection is displayed in a grid format. Each manga item shows:

- **Cover image** - The manga's cover artwork (or the first image if no cover is found)
- **Title** - The name of the manga
- **Chapter count** - How many chapters are available
- **Reading status** - A "Reading" badge if you have started reading but not finished

A special **Recently Read** section appears at the top of the library if you have started reading any manga. This section shows up to 8 recently read items with:

- Cover image
- Title
- Chapter and page you left off on
- Time since you last read (shown as "5m ago", "2h ago", etc.)
- A progress bar showing how far you are through the manga

When you tap on any manga in the library, you will navigate to its detail screen.

### Detail Screen

The detail screen shows comprehensive information about a selected manga. It provides access to all chapters and your reading progress.

At the top of this screen, you will see:

- **Full-screen cover image** - A large banner-style cover image with a gradient overlay
- **Manga title** - The name of the manga prominently displayed

Below the cover area, you will find:

- **Continue Reading button** - If you have previously started reading this manga, a prominent button appears that lets you jump directly to where you left off, showing the exact chapter and page
- **Chapter count** - Total number of chapters available
- **Sort toggle** - A button to change the chapter order (ascending or descending)

The chapter list displays all chapters available for the manga. Each chapter item shows:

- **Chapter title** - The name or number of the chapter
- **Page count** - How many pages are in that chapter
- **Reading indicator** - If you are currently reading that chapter, it is highlighted with a special badge saying "READING"
- **Last read page** - If you have read part of the chapter but not finished, it shows which page you left off on

Tapping any chapter will open the reader screen starting from that chapter.

### Reader Screen

The reader screen is where the actual reading happens. This is the core feature of the application, designed to provide the best possible reading experience.

**Reading Mode**

The reader uses **vertical scroll only**. Pages are stacked vertically and you scroll up and down to move through them. This works well for all manga formats and both portrait and landscape orientations.

**Top Bar**

The top bar contains (left to right):

- **Back icon** - Returns to the detail screen
- **Current page / Total pages** - Displayed in the center (e.g., "5 / 120"). Tap this to open the page jump modal
- **Prev/Next chapter buttons** - Navigate to the previous or next chapter, displayed on the right

**Page Jump**

Tap the current page / total pages indicator in the center of the top bar to open a modal. Type the page number you want to go to and confirm. The reader will scroll to that page immediately.

**Zoom and Pan**

- **Pinch to zoom** - Use two fingers to zoom in or out
- **Pan** - Drag around when zoomed in
- Maximum zoom level is 4x
- No double-tap zoom

**Auto-Hiding Top Bar**

The top bar automatically hides after a few seconds of inactivity for an immersive reading experience. Tap anywhere on the screen to bring it back.

**Progress Auto-Save**

Reading progress (chapter and page position) is saved automatically as you read, so you can always resume exactly where you left off.

**Chapter Navigation**

Use the previous and next chapter buttons in the top bar to move between chapters. Progress is saved automatically when switching chapters.

### Settings Screen

The settings screen allows you to manage your manga folder, authentication, and app preferences.

**Library Folder Section**

- Displays the currently selected folder path
- Tap the folder path to open a folder picker and select a new folder
- After selecting a new folder, the library will rescan automatically

**Biometric Authentication Section**

- Toggle to enable or disable fingerprint authentication
- Shows whether biometric authentication is available on your device
- When enabling, you may need to verify with your biometric first

**PIN Management Section**

- Change PIN option - Update your authentication PIN
- Requires verifying your current PIN first, then entering a new PIN twice

**Storage Section**

- Clear Thumbnail Cache — Deletes all cached cover thumbnails to free up disk space. Thumbnails will be regenerated the next time the library is loaded.

**Danger Zone**

- Clear Authentication option - Removes all PIN and biometric settings
- This will require you to go through the setup process again on next launch

---

## Features Summary

### Authentication & Security

- PIN-based protection (4-6 digits)
- Biometric authentication support (fingerprint)
- First-time setup wizard
- PIN change functionality
- Option to clear all authentication data

### Library Management

- Automatic scanning of selected folder (configured in Settings)
- Pull-to-refresh to reload the library
- Support for both manga folders and comic archives
- Grid-based library display with cover images
- Search functionality by title
- Sort by title (alphabetical) or chapter count
- Recently read section showing last 8 items
- Visual progress indicators

### Reading Experience

- Vertical scroll mode only
- Pinch-to-zoom (up to 4x) with pan support
- Page jump via modal (tap page indicator in top bar)
- Chapter navigation (previous/next) in top bar
- Auto-hiding top bar for immersive reading
- Auto-save progress (chapter and page position)
- Portrait and landscape orientation support

### File Support

- Folder-based manga (folders containing chapter subfolders or images)
- Archive files (CBZ and ZIP formats)
- Image formats: JPG, JPEG, PNG, WEBP
- Automatic cover image detection
- Natural sorting (handles "Chapter 2" vs "Chapter 10" correctly)

### Progress Tracking

- Automatic saving of reading position
- Continue reading feature (resumes where you left off)
- Last read timestamp tracking
- Progress indicators in library view

---

## Empty States

When there is nothing to display, the app shows a clear message rather than a blank screen.

- **No folder selected** — The library shows a prompt directing the user to go to Settings and select a manga folder
- **Folder selected but no manga found** — The library shows a message: "No manga found in this folder"
- **Search with no results** — Shows "No results for [query]" below the search bar

---

## Error Handling

- **Folder no longer exists** (e.g. SD card removed or folder deleted) — The library shows a warning and prompts the user to reselect a folder in Settings
- **Corrupted ZIP/CBZ** — The file is silently skipped during scanning and does not appear in the library; scanning continues normally for remaining files
- **Image fails to load in reader** — A broken image placeholder is shown for that specific page; the rest of the chapter continues to load normally

---

## Android Storage Permissions

Android 10 (API 29) and above uses scoped storage. The app explicitly requests the necessary storage permissions (`READ_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`) to allow the folder picker and scanner to access user-selected directories. If permission is denied, the app shows a clear explanation and prompts the user to grant access.

---

## Performance

### Non-Blocking Experience

All heavy operations run in the background so the UI always stays responsive.

**Library Screen**

- Folder scanning runs in a **background thread** — the UI never freezes during a scan
- The library grid populates in real time as manga are discovered
- Cover images are **lazy loaded** — only visible grid items load their covers; off-screen items show a placeholder skeleton
- The grid uses a **virtualized list** — only items currently visible on screen are rendered, keeping memory usage low for large collections

**Reader Screen**

- Pages are **loaded on demand** — only the current page plus a small buffer (3 pages ahead, 1 page behind) are kept in memory at a time
- Each page shows a placeholder while its image loads
- When nearing the end of a chapter, the first few pages of the next chapter are **preloaded in the background** to make chapter transitions feel instant

### Caching

**Cover image thumbnail cache** (disk-based)

- Cover images are resized and compressed into thumbnails the first time they are loaded and saved to a local disk cache
- On subsequent library opens, thumbnails are served from cache instantly without re-reading and decoding the original files
- The cache is automatically invalidated for any manga whose source files have changed during a rescan

The thumbnail cache has a maximum size of **500MB**. When the limit is reached, the least recently used thumbnails are evicted first (LRU eviction). No other caching is used — reader pages are fast enough from local disk with the on-demand buffer strategy, and library scan results are always read fresh to stay accurate.

---

## Expected File Organization

### Folder-Based Manga

**Style 1 — Chapters as subfolders:**
```
Your Manga Folder/
└── Manga Title/
    ├── cover.jpg (optional)
    ├── Chapter 1/
    │   ├── 001.jpg
    │   └── 002.jpg
    └── Chapter 2/
        └── ...
```

**Style 2 — Single chapter (images directly in manga folder):**
```
Your Manga Folder/
└── Manga Title/
    ├── 001.jpg
    ├── 002.jpg
    └── ...
```

**Conflict rule**: If a manga folder contains both subfolders and loose images, the app treats the **subfolders as chapters** and ignores the loose images at that level.

**Nesting depth**: The app scans only one level deep for chapters. Folders nested deeper than one level below the manga root are ignored.

### Archive-Based Manga

Supported formats: **.ZIP** and **.CBZ** only.

Archives can contain either flat images or subfolders (treated as chapters):

```
manga_title.zip
├── Chapter 1/
│   ├── 001.jpg
│   └── 002.jpg
└── Chapter 2/
    └── ...
```

Or a flat archive (treated as a single chapter):
```
manga_title.zip
├── 001.jpg
├── 002.jpg
└── ...
```

### Supported Image Formats

- JPG, JPEG, PNG, WEBP

---

## User Experience Highlights

1. **Quick Access**: After initial setup, access your library with a simple PIN or biometric
2. **Easy Discovery**: Browse your entire collection in the grid library with cover images
3. **Resume Reading**: The continue reading feature takes you exactly where you left off
4. **Simple Reading**: Vertical scroll mode works great for all content types in any orientation
5. **Privacy**: Your reading history and library are protected behind authentication
6. **Organization**: Natural sorting ensures chapters appear in the correct order
7. **Immersive Reading**: Auto-hiding top bar for a distraction-free experience
8. **Easy Refresh**: Pull down on the library to reload your collection

---

## Development Task List

### Phase 1 — Foundation
1. **Add dependencies** to `pubspec.yaml` (storage, ZIP/archive, biometrics, secure storage, image caching, etc.)
2. **Set up folder structure** — `lib/screens/`, `lib/models/`, `lib/services/`, `lib/widgets/`
3. **Theme & routing** — dark teal theme (#11221F, #11D4B4), named routes for all 7 screens
4. **Android permissions** — storage permissions in `AndroidManifest.xml`

### Phase 2 — Auth
5. **Setup Screen** — first-time PIN creation (4-6 digits) + optional biometric toggle
6. **Login Screen** — PIN entry + biometric auth
7. **Auth service** — secure PIN storage, biometric check, first-launch detection
8. **Splash Screen** — 1-2s display, auto-navigate to Setup or Login

### Phase 3 — Library
9. **Folder scanner service** — scan folder for manga (folders + CBZ/ZIP), natural sort, background thread
10. **Library Screen** — grid view, cover images, chapter count, reading badges, Recently Read section
11. **Search & sort** — by title (A-Z/Z-A), chapter count; search bar
12. **Pull-to-refresh** with scan progress indicator + cancel button
13. **Thumbnail cache service** — disk cache, 500MB LRU eviction, invalidation on rescan

### Phase 4 — Reading
14. **Detail Screen** — cover banner, chapter list, continue reading button, sort toggle, reading indicators
15. **Reader Screen** — vertical scroll, pinch-to-zoom (4x), auto-hiding top bar, page jump modal
16. **Progress service** — auto-save chapter + page position, timestamps
17. **Chapter navigation** — prev/next buttons, preload next chapter

### Phase 5 — Settings & Polish
18. **Settings Screen** — folder picker, biometric toggle, PIN change, clear thumbnail cache, danger zone
19. **Empty states & error handling** — no folder, no manga found, missing folder, broken ZIP, broken image
20. **Performance** — lazy loading covers, virtualized list, on-demand page buffering
