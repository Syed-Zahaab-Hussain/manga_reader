import 'package:path/path.dart' as p;

/// Natural comparison of two strings, handling embedded numbers correctly.
/// e.g. "Chapter 2" < "Chapter 10"
int naturalCompare(String a, String b) {
  final reg = RegExp(r'(\d+)|(\D+)');
  final aMatches = reg.allMatches(a).toList();
  final bMatches = reg.allMatches(b).toList();

  final len =
      aMatches.length < bMatches.length ? aMatches.length : bMatches.length;
  for (var i = 0; i < len; i++) {
    final aSeg = aMatches[i].group(0)!;
    final bSeg = bMatches[i].group(0)!;
    final aNum = int.tryParse(aSeg);
    final bNum = int.tryParse(bSeg);
    if (aNum != null && bNum != null) {
      final cmp = aNum.compareTo(bNum);
      if (cmp != 0) return cmp;
    } else {
      final cmp = aSeg.compareTo(bSeg);
      if (cmp != 0) return cmp;
    }
  }
  return aMatches.length.compareTo(bMatches.length);
}

/// Whether the file name has a supported image extension.
bool isImageFile(String name) {
  final lower = name.toLowerCase();
  return lower.endsWith('.jpg') ||
      lower.endsWith('.jpeg') ||
      lower.endsWith('.png') ||
      lower.endsWith('.webp');
}

/// Whether the file is a cover image (e.g. "cover.jpg").
bool isCoverFile(String name) {
  final lower = p.basenameWithoutExtension(name).toLowerCase();
  return lower == 'cover' && isImageFile(name);
}
