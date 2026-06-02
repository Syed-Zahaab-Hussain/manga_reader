package com.example.mangareader.core.io

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

object FolderAccess {

    fun hasStorageAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    fun allFilesAccessIntent(context: Context): Intent {
        // Some OEMs don't handle the per-app settings action; fall back to the generic one.
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        val canOpenPerApp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            perApp.resolveActivity(context.packageManager) != null
        val intent = if (canOpenPerApp) {
            perApp
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun legacyReadPermission(): String = Manifest.permission.READ_EXTERNAL_STORAGE

    /**
     * Resolves a SAF tree [uri] returned by [android.content.Intent.ACTION_OPEN_DOCUMENT_TREE]
     * into a real [File] on local storage.
     *
     * Handles:
     * - the primary internal volume ("primary:")
     * - secondary volumes such as SD cards (volume ids like "XXXX-XXXX")
     * - picking a volume root (empty path segment)
     *
     * Returns null when the uri points at a non-local document provider (cloud, downloads
     * provider, etc.) that cannot be represented as a file path.
     */
    fun treeUriToFolder(context: Context, uri: Uri): File? = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(uri) ?: return null
        val parts = documentId.split(":", limit = 2)
        if (parts.isEmpty()) return null
        val volume = parts[0].trim()
        val relativePath = (parts.getOrElse(1) { "" }).trim()
        val volumeRoot = resolveVolumeRoot(context, volume) ?: return null
        val folder = if (relativePath.isEmpty()) {
            volumeRoot
        } else {
            File(volumeRoot, relativePath.removePrefix(".").removePrefix("/"))
        }
        folder.takeIf { it.isDirectory }
    }.getOrNull()

    private fun resolveVolumeRoot(context: Context, volume: String): File? {
        @Suppress("DEPRECATION")
        val primary = Environment.getExternalStorageDirectory()
        if (volume.equals(PRIMARY_VOLUME, ignoreCase = true)) return primary
        if (volume.isEmpty()) return primary

        // Secondary storage (e.g. SD cards): derive each volume's mount root from the
        // app-specific dirs, which look like /storage/<VOLUME_ID>/Android/data/<pkg>/files.
        return context.getExternalFilesDirs(null).firstNotNullOfOrNull { dir ->
            if (dir == null) return@firstNotNullOfOrNull null
            val path = dir.absolutePath
            val marker = "/Android/"
            val index = path.indexOf(marker)
            if (index <= 0) return@firstNotNullOfOrNull null
            val root = File(path.substring(0, index))
            if (root.name.equals(volume, ignoreCase = true)) root else null
        }
    }

    fun describeTreeUri(uri: Uri): String = runCatching {
        DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull() ?: uri.toString()

    private const val PRIMARY_VOLUME = "primary"
}
