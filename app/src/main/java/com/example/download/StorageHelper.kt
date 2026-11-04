package com.example.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.example.data.CustomFolderOption
import com.example.data.DownloadEntity
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder
import java.util.Locale

object StorageHelper {

    /**
     * Returns the default app-managed Downloads directory, guaranteed to be writable
     * on all Android versions without requiring broad storage permissions.
     */
    fun getDefaultDownloadsDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Persists read/write permission for a directory selected via SAF OpenDocumentTree
     * and returns a user-friendly Arabic description of the chosen folder.
     */
    fun persistAndDescribeTreeUri(context: Context, treeUri: Uri): CustomFolderOption {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (_: SecurityException) {
            // Ignore if already granted or not persistable in test environment
        }

        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: treeUri.lastPathSegment.orEmpty()
        val decoded = runCatching { URLDecoder.decode(docId, "UTF-8") }.getOrDefault(docId)
        val cleanPath = decoded
            .replace("primary:", "الذاكرة الداخلية/")
            .replace("home:", "المستندات/")
        val folderShortName = cleanPath.substringAfterLast('/').ifBlank { "مجلد مخصص" }

        return CustomFolderOption(
            uriString = treeUri.toString(),
            displayName = "مجلد: $folderShortName",
            subtitlePath = cleanPath
        )
    }

    /**
     * Resolves a clean suggested file name from URL, Content-Disposition header, and MIME type.
     */
    fun guessCleanFileName(
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ): String {
        if (url.startsWith("madar://sample/")) {
            return url.substringAfterLast('/').ifBlank { "sample_file.bin" }
        }
        val rawGuessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val decoded = runCatching { URLDecoder.decode(rawGuessed, "UTF-8") }.getOrDefault(rawGuessed)
        return sanitizeFileName(decoded, mimeType)
    }

    /**
     * Sanitizes a user-edited file name so it is safe for Android file systems and preserves an extension.
     */
    fun sanitizeFileName(inputName: String, mimeType: String? = null): String {
        val trimmed = inputName.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "downloaded_file_${System.currentTimeMillis()}" }

        if (trimmed.contains('.')) {
            return trimmed
        }

        val extFromMime = mimeType?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
        return if (!extFromMime.isNullOrBlank()) {
            "$trimmed.$extFromMime"
        } else {
            "$trimmed.bin"
        }
    }

    /**
     * Resolves MIME type from file extension if server returned generic octet-stream.
     */
    fun resolveMimeType(fileName: String, currentMimeType: String?): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!mapped.isNullOrBlank()) return mapped
        }
        if (!currentMimeType.isNullOrBlank() && currentMimeType != "application/octet-stream") {
            return currentMimeType
        }
        return when (ext) {
            "pdf" -> "application/pdf"
            "mp4", "m4v" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt", "md", "log" -> "text/plain"
            "html", "htm" -> "text/html"
            else -> "application/octet-stream"
        }
    }

    /**
     * Exports a completed local file to public MediaStore.Downloads on Android 10+ (API 29+)
     * so it is also visible in the system Downloads app under Scoped Storage.
     */
    fun mirrorToPublicDownloadsIfSupported(
        context: Context,
        localFile: File,
        fileName: String,
        mimeType: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !localFile.exists()) return null
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MadarBrowser")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(localFile).use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrNull()
    }

    /**
     * Opens a completed download using Android's Intent.ACTION_VIEW and FileProvider / SAF URI.
     */
    fun openDownloadedFile(
        context: Context,
        item: DownloadEntity,
        onError: (String) -> Unit
    ) {
        val targetPath = item.savedFileUriOrPath
        if (targetPath.isBlank()) {
            onError("لم يتم العثور على مسار الملف المحمل")
            return
        }

        try {
            val contentUri: Uri = if (targetPath.startsWith("content://")) {
                Uri.parse(targetPath)
            } else {
                val file = File(targetPath)
                if (!file.exists()) {
                    onError("الملف غير موجود على الذاكرة، ربما تم نقله أو حذفه")
                    return
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val resolvedMime = resolveMimeType(item.fileName, item.mimeType)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(viewIntent, "فتح ${item.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            onError("لا يوجد تطبيق مثبت على الجهاز لفتح هذا النوع من الملفات (${item.fileName.substringAfterLast('.', "")})")
        }
    }

    /**
     * Shares a downloaded file with other apps using FileProvider or SAF URI.
     */
    fun shareDownloadedFile(
        context: Context,
        item: DownloadEntity,
        onError: (String) -> Unit
    ) {
        val targetPath = item.savedFileUriOrPath
        if (targetPath.isBlank()) {
            onError("الملف غير متاح للمشاركة بعد")
            return
        }

        try {
            val contentUri: Uri = if (targetPath.startsWith("content://")) {
                Uri.parse(targetPath)
            } else {
                val file = File(targetPath)
                if (!file.exists()) {
                    onError("الملف غير موجود على الجهاز")
                    return
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val resolvedMime = resolveMimeType(item.fileName, item.mimeType)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = resolvedMime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "تم تحميل ${item.fileName} عبر متصفح مدار")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة ${item.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            onError("تعذر مشاركة الملف: ${e.localizedMessage ?: ""}")
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "غير معروف"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        return "${formatBytes(bytesPerSec)}/s"
    }
}
