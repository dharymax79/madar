package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

const val HOME_TAB_URL = "madar://home"

enum class DownloadStatus(val arabicLabel: String) {
    PENDING("في الانتظار"),
    DOWNLOADING("جاري التحميل"),
    PAUSED("متوقف مؤقتاً"),
    COMPLETED("مكتمل"),
    FAILED("فشل التحميل")
}

enum class DownloadCategory(val arabicLabel: String) {
    ALL("الكل"),
    VIDEO("فيديو"),
    IMAGE("صور"),
    DOCUMENT("مستندات"),
    AUDIO("صوتيات"),
    ARCHIVE("ملفات مضغوطة"),
    OTHER("أخرى");

    companion object {
        fun fromMimeOrName(mimeType: String, fileName: String): DownloadCategory {
            val lowerMime = mimeType.lowercase()
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                lowerMime.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv") -> VIDEO
                lowerMime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp") -> IMAGE
                lowerMime.startsWith("audio/") || ext in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> AUDIO
                lowerMime.contains("pdf") || lowerMime.contains("word") || lowerMime.contains("excel") ||
                    lowerMime.contains("presentation") || lowerMime.startsWith("text/") ||
                    ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "epub") -> DOCUMENT
                lowerMime.contains("zip") || lowerMime.contains("rar") || lowerMime.contains("tar") ||
                    lowerMime.contains("7z") || lowerMime.contains("android.package-archive") ||
                    ext in listOf("zip", "rar", "7z", "tar", "gz", "apk", "iso") -> ARCHIVE
                else -> OTHER
            }
        }
    }
}

enum class SearchEngine(val arabicName: String, val searchUrlTemplate: String, val homeUrl: String) {
    GOOGLE("بحث Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
    DUCKDUCKGO("DuckDuckGo (الخصوصية)", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com"),
    BING("Microsoft Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com")
}

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val isActive: Boolean = false,
    val isDesktopMode: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browsing_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "عام",
    val isQuickLink: Boolean = true,
    val accentHex: String = "#0284C7",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val category: String = DownloadCategory.OTHER.name,
    /**
     * Empty string means default app/system Downloads directory.
     * Non-empty content:// tree URI means a custom directory chosen via Android SAF (OpenDocumentTree).
     */
    val customFolderUri: String = "",
    val customFolderName: String = "مجلد التحميلات الافتراضي (Downloads)",
    /**
     * Stores either an absolute file path (when saved in app/Downloads folder)
     * or a content:// document URI (when saved inside a user-picked SAF tree).
     */
    val savedFileUriOrPath: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = 0L,
    val status: String = DownloadStatus.PENDING.name,
    val errorMessage: String? = null,
    val userAgent: String = "",
    val cookies: String = "",
    val supportsResume: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    val statusEnum: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.PENDING)

    val categoryEnum: DownloadCategory
        get() = runCatching { DownloadCategory.valueOf(category) }.getOrDefault(DownloadCategory.OTHER)

    val progressFraction: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}

/**
 * Holds metadata for the Pre-Download Confirmation & Customization Dialog
 * shown before any download starts so the user can edit the file name and select the custom storage path.
 */
data class PendingDownloadRequest(
    val url: String,
    val suggestedFileName: String,
    val mimeType: String = "application/octet-stream",
    val contentLength: Long = -1L,
    val userAgent: String = "",
    val cookies: String = "",
    val sourceLabel: String = "صفحة الويب",
    val isFetchingMetadata: Boolean = false,
    val supportsRange: Boolean = true
)

/**
 * Represents a storage folder option (either the default Downloads directory or a user-picked SAF folder).
 */
data class CustomFolderOption(
    val uriString: String, // "" for default Downloads directory, or "content://..." for SAF tree
    val displayName: String,
    val subtitlePath: String
)

/**
 * Sample files available in the Home Tab Download Test Lab so users can immediately test
 * custom filename editing, custom folder selection, and pause/resume functionality.
 */
data class SampleDownloadPreset(
    val title: String,
    val description: String,
    val url: String,
    val defaultFileName: String,
    val mimeType: String,
    val approxSizeLabel: String,
    val category: DownloadCategory
)
