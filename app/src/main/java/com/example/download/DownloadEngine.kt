package com.example.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.example.data.BrowserDao
import com.example.data.DownloadCategory
import com.example.data.DownloadEntity
import com.example.data.DownloadStatus
import com.example.data.MadarDatabase
import com.example.data.PendingDownloadRequest
import com.example.data.SampleDownloadPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin

/**
 * Application-scoped Resumable Download Engine built with OkHttp, Kotlin Coroutines,
 * Foreground Service, Status Bar Notifications, Haptic Vibration, and Android Scoped Storage (SAF).
 *
 * Because it is an Application Singleton backed by `DownloadForegroundService`, downloads continue
 * seamlessly even when the user exits the browser or switches to another app.
 */
class DownloadEngine private constructor(
    private val appContext: Context,
    private val dao: BrowserDao
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeSnapshots = ConcurrentHashMap<Long, DownloadEntity>()

    // Holds the most recently completed download to display the compact completion popup dialog
    private val _completedPopupDownload = MutableStateFlow<DownloadEntity?>(null)
    val completedPopupDownload: StateFlow<DownloadEntity?> = _completedPopupDownload.asStateFlow()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        @Volatile
        private var INSTANCE: DownloadEngine? = null

        fun getInstance(context: Context): DownloadEngine {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val db = MadarDatabase.getInstance(appCtx)
                val instance = DownloadEngine(appCtx, db.browserDao())
                INSTANCE = instance
                instance
            }
        }

        val SAMPLE_PRESETS = listOf(
            SampleDownloadPreset(
                title = "دليل هندسة أندرويد (مستند PDF)",
                description = "ملف PDF تفاعلي لاختبار تعديل الاسم وحفظ المستندات وفتحها",
                url = "madar://sample/madar_android_architecture_guide.pdf",
                defaultFileName = "دليل_متصفح_مدار_التقني.pdf",
                mimeType = "application/pdf",
                approxSizeLabel = "2.4 MB",
                category = DownloadCategory.DOCUMENT
            ),
            SampleDownloadPreset(
                title = "خلفية مدار الفضائية 4K (صورة PNG)",
                description = "صورة عالية الدقة لاختبار اختيار المجلد المخصص والإيقاف المؤقت",
                url = "madar://sample/madar_space_wallpaper_hd.png",
                defaultFileName = "خلفية_مدار_الفضائية.png",
                mimeType = "image/png",
                approxSizeLabel = "3.2 MB",
                category = DownloadCategory.IMAGE
            ),
            SampleDownloadPreset(
                title = "نغمة مدار الهادئة (ملف صوتي WAV)",
                description = "مقطع صوتي نقي لاختبار استئناف التحميل وتشغيل الصوتيات",
                url = "madar://sample/madar_ambient_chime.wav",
                defaultFileName = "نغمة_مدار_الهادئة.wav",
                mimeType = "audio/wav",
                approxSizeLabel = "2.8 MB",
                category = DownloadCategory.AUDIO
            ),
            SampleDownloadPreset(
                title = "حزمة موارد المطورين (أرشيف ZIP)",
                description = "ملف مضغوط يحتوي على ملفات نصية وأمثلة برمجية",
                url = "madar://sample/madar_developer_starter_kit.zip",
                defaultFileName = "حزمة_موارد_المطورين.zip",
                mimeType = "application/zip",
                approxSizeLabel = "4.0 MB",
                category = DownloadCategory.ARCHIVE
            )
        )
    }

    fun getActiveSnapshots(): List<DownloadEntity> = activeSnapshots.values.toList()

    fun dismissCompletedPopup() {
        _completedPopupDownload.value = null
    }

    /**
     * Inspects URL headers in the background to populate the Pre-Download Dialog
     * with accurate suggested filename, MIME type, and file size.
     */
    suspend fun inspectUrlMetadata(initial: PendingDownloadRequest): PendingDownloadRequest =
        withContext(Dispatchers.IO) {
            if (initial.url.startsWith("madar://sample/")) {
                val preset = SAMPLE_PRESETS.find { it.url == initial.url }
                val sampleSize = when (preset?.category) {
                    DownloadCategory.DOCUMENT -> 2_450_000L
                    DownloadCategory.IMAGE -> 3_200_000L
                    DownloadCategory.AUDIO -> 2_850_000L
                    DownloadCategory.ARCHIVE -> 4_100_000L
                    else -> 2_500_000L
                }
                return@withContext initial.copy(
                    suggestedFileName = preset?.defaultFileName ?: StorageHelper.guessCleanFileName(initial.url, null, initial.mimeType),
                    mimeType = preset?.mimeType ?: initial.mimeType,
                    contentLength = sampleSize,
                    isFetchingMetadata = false,
                    supportsRange = true
                )
            }

            if (initial.url.startsWith("data:")) {
                val mime = initial.url.substringAfter("data:", "").substringBefore(";", "image/png")
                val cleanName = StorageHelper.sanitizeFileName(initial.suggestedFileName, mime)
                return@withContext initial.copy(
                    suggestedFileName = cleanName,
                    mimeType = mime,
                    contentLength = initial.url.length.toLong(),
                    isFetchingMetadata = false,
                    supportsRange = false
                )
            }

            try {
                val requestBuilder = Request.Builder()
                    .url(initial.url)
                    .header("Range", "bytes=0-0")
                if (initial.userAgent.isNotBlank()) {
                    requestBuilder.header("User-Agent", initial.userAgent)
                }
                if (initial.cookies.isNotBlank()) {
                    requestBuilder.header("Cookie", initial.cookies)
                }

                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val contentDisp = response.header("Content-Disposition")
                    val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
                    val contentRange = response.header("Content-Range")
                    val totalFromRange = contentRange?.substringAfterLast('/')?.toLongOrNull()
                    val contentLengthHeader = response.header("Content-Length")?.toLongOrNull()
                    val resolvedTotal = totalFromRange ?: contentLengthHeader ?: initial.contentLength

                    val resolvedMime = StorageHelper.resolveMimeType(
                        initial.suggestedFileName,
                        contentType ?: initial.mimeType
                    )
                    val resolvedName = if (contentDisp != null) {
                        StorageHelper.guessCleanFileName(initial.url, contentDisp, resolvedMime)
                    } else {
                        StorageHelper.sanitizeFileName(initial.suggestedFileName, resolvedMime)
                    }
                    val supportsRange = response.code == 206 ||
                        response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true

                    initial.copy(
                        suggestedFileName = resolvedName,
                        mimeType = resolvedMime,
                        contentLength = resolvedTotal,
                        isFetchingMetadata = false,
                        supportsRange = supportsRange
                    )
                }
            } catch (_: Exception) {
                val resolvedMime = StorageHelper.resolveMimeType(initial.suggestedFileName, initial.mimeType)
                initial.copy(
                    suggestedFileName = StorageHelper.sanitizeFileName(initial.suggestedFileName, resolvedMime),
                    mimeType = resolvedMime,
                    isFetchingMetadata = false
                )
            }
        }

    /**
     * Creates a new download record with the user's customized file name and chosen folder,
     * then immediately starts downloading via the background-safe Foreground Service.
     */
    fun enqueueAndStartDownload(
        request: PendingDownloadRequest,
        customFileName: String,
        customFolderUri: String,
        customFolderName: String,
        onStarted: (Long) -> Unit = {}
    ) {
        engineScope.launch {
            val resolvedMime = StorageHelper.resolveMimeType(customFileName, request.mimeType)
            val safeFileName = StorageHelper.sanitizeFileName(customFileName, resolvedMime)
            val category = DownloadCategory.fromMimeOrName(resolvedMime, safeFileName)

            val entity = DownloadEntity(
                url = request.url,
                fileName = safeFileName,
                mimeType = resolvedMime,
                category = category.name,
                customFolderUri = customFolderUri,
                customFolderName = customFolderName,
                savedFileUriOrPath = "",
                downloadedBytes = 0L,
                totalBytes = request.contentLength,
                speedBytesPerSec = 0L,
                status = DownloadStatus.PENDING.name,
                userAgent = request.userAgent,
                cookies = request.cookies,
                supportsResume = request.supportsRange
            )
            val id = dao.insertDownload(entity)
            withContext(Dispatchers.Main) {
                onStarted(id)
            }
            startOrResumeJob(id)
        }
    }

    fun pauseDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        activeSnapshots.remove(downloadId)
        DownloadNotificationHelper.cancelProgressNotification(appContext, downloadId)
        engineScope.launch {
            dao.updateDownloadStatus(downloadId, DownloadStatus.PAUSED.name)
            syncForegroundServiceState()
        }
    }

    fun resumeDownload(downloadId: Long) {
        if (activeJobs.containsKey(downloadId)) return
        startOrResumeJob(downloadId)
    }

    fun retryDownload(downloadId: Long) {
        activeJobs.remove(downloadId)?.cancel()
        activeSnapshots.remove(downloadId)
        engineScope.launch {
            val current = dao.getDownloadById(downloadId) ?: return@launch
            dao.updateDownload(
                current.copy(
                    downloadedBytes = 0L,
                    speedBytesPerSec = 0L,
                    status = DownloadStatus.PENDING.name,
                    errorMessage = null,
                    savedFileUriOrPath = ""
                )
            )
            startOrResumeJob(downloadId)
        }
    }

    fun deleteDownload(downloadId: Long, deleteFileFromDisk: Boolean) {
        activeJobs.remove(downloadId)?.cancel()
        activeSnapshots.remove(downloadId)
        DownloadNotificationHelper.cancelProgressNotification(appContext, downloadId)
        if (_completedPopupDownload.value?.id == downloadId) {
            _completedPopupDownload.value = null
        }
        engineScope.launch {
            val current = dao.getDownloadById(downloadId)
            if (deleteFileFromDisk && current != null && current.savedFileUriOrPath.isNotBlank()) {
                runCatching {
                    if (current.savedFileUriOrPath.startsWith("content://")) {
                        DocumentsContract.deleteDocument(
                            appContext.contentResolver,
                            Uri.parse(current.savedFileUriOrPath)
                        )
                    } else {
                        File(current.savedFileUriOrPath).delete()
                    }
                }
            }
            dao.deleteDownloadById(downloadId)
            syncForegroundServiceState()
        }
    }

    private fun startOrResumeJob(downloadId: Long) {
        val job = engineScope.launch {
            val item = dao.getDownloadById(downloadId) ?: return@launch
            try {
                val activeItem = item.copy(status = DownloadStatus.DOWNLOADING.name, errorMessage = null)
                activeSnapshots[downloadId] = activeItem
                dao.updateDownloadStatus(downloadId, DownloadStatus.DOWNLOADING.name, null)

                // Start/update the Foreground Service so download continues if user exits the browser
                DownloadForegroundService.startOrUpdate(appContext)
                DownloadNotificationHelper.showProgressNotification(appContext, activeItem)

                if (item.url.startsWith("madar://sample/")) {
                    executeSampleStreamDownload(activeItem)
                } else if (item.url.startsWith("data:")) {
                    executeDataUriDownload(activeItem)
                } else {
                    executeHttpDownload(activeItem)
                }
            } catch (ce: CancellationException) {
                activeSnapshots.remove(downloadId)
                DownloadNotificationHelper.cancelProgressNotification(appContext, downloadId)
                dao.updateDownloadStatus(downloadId, DownloadStatus.PAUSED.name, null)
                syncForegroundServiceState()
                throw ce
            } catch (e: Exception) {
                activeSnapshots.remove(downloadId)
                val reason = e.localizedMessage ?: "انقطع الاتصال أثناء التحميل"
                dao.updateDownloadStatus(downloadId, DownloadStatus.FAILED.name, reason)
                DownloadNotificationHelper.showFailedNotification(appContext, item, reason)
                syncForegroundServiceState()
            } finally {
                activeJobs.remove(downloadId)
                activeSnapshots.remove(downloadId)
                syncForegroundServiceState()
            }
        }
        activeJobs[downloadId] = job
    }

    private fun syncForegroundServiceState() {
        if (activeSnapshots.isEmpty()) {
            DownloadForegroundService.stop(appContext)
        } else {
            DownloadForegroundService.startOrUpdate(appContext)
        }
    }

    /**
     * Marks a download as completed, mirrors to public Downloads if applicable,
     * triggers gentle haptic vibration, posts top status bar notification, and shows the completion popup!
     */
    private suspend fun markDownloadCompleted(
        item: DownloadEntity,
        finalBytes: Long,
        totalBytes: Long,
        savedPathOrUri: String,
        supportsResume: Boolean
    ) {
        if (!savedPathOrUri.startsWith("content://")) {
            StorageHelper.mirrorToPublicDownloadsIfSupported(
                context = appContext,
                localFile = File(savedPathOrUri),
                fileName = item.fileName,
                mimeType = item.mimeType
            )
        }

        val resolvedTotal = if (totalBytes > 0) totalBytes else finalBytes
        dao.updateDownloadProgress(
            id = item.id,
            downloadedBytes = finalBytes,
            totalBytes = resolvedTotal,
            speedBytesPerSec = 0L,
            status = DownloadStatus.COMPLETED.name,
            savedFileUriOrPath = savedPathOrUri,
            supportsResume = supportsResume
        )

        val completedEntity = item.copy(
            downloadedBytes = finalBytes,
            totalBytes = resolvedTotal,
            speedBytesPerSec = 0L,
            status = DownloadStatus.COMPLETED.name,
            savedFileUriOrPath = savedPathOrUri,
            supportsResume = supportsResume
        )

        activeSnapshots.remove(item.id)

        // 1. Trigger gentle completion vibration
        DownloadNotificationHelper.triggerCompletionVibration(appContext)

        // 2. Post completion notification in top status bar
        DownloadNotificationHelper.showCompletedNotification(appContext, completedEntity)

        // 3. Emit completed entity to trigger the compact completion popup dialog in the UI
        _completedPopupDownload.value = completedEntity
    }

    private suspend fun reportRunningProgress(
        item: DownloadEntity,
        currentBytes: Long,
        totalBytes: Long,
        speed: Long,
        savedPathOrUri: String,
        supportsResume: Boolean
    ) {
        dao.updateDownloadProgress(
            id = item.id,
            downloadedBytes = currentBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speed,
            status = DownloadStatus.DOWNLOADING.name,
            savedFileUriOrPath = savedPathOrUri,
            supportsResume = supportsResume
        )
        val updatedSnapshot = item.copy(
            downloadedBytes = currentBytes,
            totalBytes = totalBytes,
            speedBytesPerSec = speed,
            status = DownloadStatus.DOWNLOADING.name,
            savedFileUriOrPath = savedPathOrUri,
            supportsResume = supportsResume
        )
        activeSnapshots[item.id] = updatedSnapshot
        DownloadNotificationHelper.showProgressNotification(appContext, updatedSnapshot)
        DownloadForegroundService.startOrUpdate(appContext)
    }

    private fun openTargetOutputStream(
        item: DownloadEntity,
        append: Boolean
    ): Pair<OutputStream, String> {
        if (item.customFolderUri.isNotBlank()) {
            val resolver = appContext.contentResolver
            if (append && item.savedFileUriOrPath.startsWith("content://")) {
                val existingDocUri = Uri.parse(item.savedFileUriOrPath)
                val out = resolver.openOutputStream(existingDocUri, "wa")
                if (out != null) {
                    return out to item.savedFileUriOrPath
                }
            }
            val treeUri = Uri.parse(item.customFolderUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val createdDocUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                item.mimeType.ifBlank { "application/octet-stream" },
                item.fileName
            ) ?: throw IllegalStateException("تعذر إنشاء الملف في المجلد المخصص المختار")

            val stream = resolver.openOutputStream(createdDocUri, "w")
                ?: throw IllegalStateException("تعذر فتح المجلد المخصص للكتابة")
            return stream to createdDocUri.toString()
        } else {
            val dir = StorageHelper.getDefaultDownloadsDir(appContext)
            val targetFile = if (item.savedFileUriOrPath.isNotBlank() && !item.savedFileUriOrPath.startsWith("content://")) {
                File(item.savedFileUriOrPath)
            } else {
                resolveUniqueLocalFile(dir, item.fileName)
            }
            val stream = FileOutputStream(targetFile, append)
            return stream to targetFile.absolutePath
        }
    }

    private fun resolveUniqueLocalFile(dir: File, desiredName: String): File {
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val baseName = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var counter = 1
        while (candidate.exists()) {
            val numbered = if (ext.isNotEmpty()) "${baseName}_($counter).$ext" else "${baseName}_($counter)"
            candidate = File(dir, numbered)
            counter++
        }
        return candidate
    }

    /**
     * Executes a real HTTP/HTTPS streaming download with HTTP Range resume support.
     */
    private suspend fun executeHttpDownload(item: DownloadEntity) {
        val resumeOffset = if (item.downloadedBytes > 0 && item.savedFileUriOrPath.isNotBlank()) {
            item.downloadedBytes
        } else {
            0L
        }

        val requestBuilder = Request.Builder().url(item.url)
        if (item.userAgent.isNotBlank()) {
            requestBuilder.header("User-Agent", item.userAgent)
        }
        if (item.cookies.isNotBlank()) {
            requestBuilder.header("Cookie", item.cookies)
        }
        if (resumeOffset > 0) {
            requestBuilder.header("Range", "bytes=$resumeOffset-")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("رمز استجابة الخادم: HTTP ${response.code}")
            }

            val isPartialContent = response.code == 206 && resumeOffset > 0
            val actualStartBytes = if (isPartialContent) resumeOffset else 0L
            val body = response.body ?: throw IllegalStateException("استجابة الخادم فارغة")
            val contentLength = body.contentLength()
            val totalBytes = when {
                isPartialContent && item.totalBytes > 0 -> item.totalBytes
                contentLength > 0 -> actualStartBytes + contentLength
                else -> item.totalBytes
            }

            val (outputStream, savedPathOrUri) = openTargetOutputStream(
                item = item,
                append = isPartialContent
            )

            outputStream.use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var currentBytes = actualStartBytes
                    var bytesInWindow = 0L
                    var windowStartMs = System.currentTimeMillis()
                    var lastDbUpdateMs = windowStartMs

                    while (engineScope.isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        currentBytes += read
                        bytesInWindow += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - lastDbUpdateMs
                        if (elapsedMs >= 400) {
                            val windowSec = ((now - windowStartMs).coerceAtLeast(1L)) / 1000.0
                            val speed = (bytesInWindow / windowSec).toLong()
                            reportRunningProgress(
                                item = item,
                                currentBytes = currentBytes,
                                totalBytes = totalBytes,
                                speed = speed,
                                savedPathOrUri = savedPathOrUri,
                                supportsResume = true
                            )
                            lastDbUpdateMs = now
                            windowStartMs = now
                            bytesInWindow = 0L
                        }
                    }
                    out.flush()

                    markDownloadCompleted(
                        item = item,
                        finalBytes = currentBytes,
                        totalBytes = totalBytes,
                        savedPathOrUri = savedPathOrUri,
                        supportsResume = true
                    )
                }
            }
        }
    }

    /**
     * Handles data: URI downloads (such as base64 inline images long-pressed in WebView).
     */
    private suspend fun executeDataUriDownload(item: DownloadEntity) {
        val base64Part = item.url.substringAfter("base64,", "")
        val bytes = if (base64Part.isNotEmpty()) {
            Base64.decode(base64Part, Base64.DEFAULT)
        } else {
            item.url.substringAfter(",", "").toByteArray(Charsets.UTF_8)
        }

        val (outputStream, savedPathOrUri) = openTargetOutputStream(item, append = false)
        outputStream.use { it.write(bytes) }

        markDownloadCompleted(
            item = item,
            finalBytes = bytes.size.toLong(),
            totalBytes = bytes.size.toLong(),
            savedPathOrUri = savedPathOrUri,
            supportsResume = false
        )
    }

    /**
     * Streams authentic binary files (valid PDF, PNG, WAV, or ZIP) in resumable chunks
     * so the user can test Pause/Resume, background downloading, status bar notifications,
     * vibration, and the completion popup dialog immediately.
     */
    private suspend fun executeSampleStreamDownload(item: DownloadEntity) {
        val fullPayload = generateValidSamplePayload(item)
        val totalBytes = fullPayload.size.toLong()
        val startOffset = if (item.downloadedBytes in 1 until totalBytes && item.savedFileUriOrPath.isNotBlank()) {
            item.downloadedBytes.toInt()
        } else {
            0
        }

        val (outputStream, savedPathOrUri) = openTargetOutputStream(
            item = item,
            append = startOffset > 0
        )

        outputStream.use { out ->
            var offset = startOffset
            val chunkSize = 64 * 1024 // 64 KB per tick (~4.5 seconds total so user can test backgrounding & Pause/Resume!)
            var lastUpdateMs = System.currentTimeMillis()
            var windowBytes = 0L

            while (offset < fullPayload.size && engineScope.isActive) {
                val bytesToWrite = minOf(chunkSize, fullPayload.size - offset)
                out.write(fullPayload, offset, bytesToWrite)
                offset += bytesToWrite
                windowBytes += bytesToWrite

                delay(95)

                val now = System.currentTimeMillis()
                val elapsed = (now - lastUpdateMs).coerceAtLeast(1L)
                if (elapsed >= 320 || offset >= fullPayload.size) {
                    val speed = (windowBytes * 1000L) / elapsed
                    reportRunningProgress(
                        item = item,
                        currentBytes = offset.toLong(),
                        totalBytes = totalBytes,
                        speed = speed,
                        savedPathOrUri = savedPathOrUri,
                        supportsResume = true
                    )
                    lastUpdateMs = now
                    windowBytes = 0L
                }
            }
            out.flush()

            markDownloadCompleted(
                item = item,
                finalBytes = totalBytes,
                totalBytes = totalBytes,
                savedPathOrUri = savedPathOrUri,
                supportsResume = true
            )
        }
    }

    private fun generateValidSamplePayload(item: DownloadEntity): ByteArray {
        val ext = item.fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext == "png" || item.mimeType.startsWith("image/") -> {
                val bmp = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f, 1200f, 800f,
                    intArrayOf(Color.parseColor("#0B1120"), Color.parseColor("#0284C7"), Color.parseColor("#0D9488")),
                    null,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, 1200f, 800f, paint)
                paint.shader = null
                paint.color = Color.WHITE
                paint.textSize = 54f
                canvas.drawText("Madar Browser - Custom Download Manager", 90f, 380f, paint)
                canvas.drawText("Saved as: ${item.fileName}", 90f, 460f, paint)
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val basePng = bos.toByteArray()
                val targetSize = 2_400_000
                if (basePng.size >= targetSize) basePng else basePng + ByteArray(targetSize - basePng.size)
            }
            ext == "wav" || item.mimeType.startsWith("audio/") -> {
                val sampleRate = 44100
                val durationSeconds = 16
                val numSamples = sampleRate * durationSeconds
                val pcmData = ByteArray(numSamples * 2)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = if (i % sampleRate < sampleRate / 2) 440.0 else 554.37
                    val sample = (sin(2.0 * Math.PI * freq * t) * 12000).toInt().toShort()
                    pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
                    pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray(Charsets.US_ASCII))
                    putInt(36 + pcmData.size)
                    put("WAVE".toByteArray(Charsets.US_ASCII))
                    put("fmt ".toByteArray(Charsets.US_ASCII))
                    putInt(16)
                    putShort(1)
                    putShort(1)
                    putInt(sampleRate)
                    putInt(sampleRate * 2)
                    putShort(2)
                    putShort(16)
                    put("data".toByteArray(Charsets.US_ASCII))
                    putInt(pcmData.size)
                }.array()
                header + pcmData
            }
            ext == "zip" || item.mimeType.contains("zip") -> {
                val bos = ByteArrayOutputStream()
                ZipOutputStream(bos).use { zos ->
                    zos.putNextEntry(ZipEntry("README_MADAR_BROWSER.txt"))
                    val readme = buildString {
                        appendLine("====================================================")
                        appendLine("   متصفح مدار الذكي - مدير التحميلات المخصص")
                        appendLine("   Madar Browser & Custom Download Manager")
                        appendLine("====================================================")
                        appendLine("اسم الملف المخصص: ${item.fileName}")
                        appendLine("مجلد الحفظ: ${item.customFolderName}")
                        appendLine()
                        repeat(12000) { idx ->
                            appendLine("سطر بيانات اختباري رقم #${idx + 1} لفحص سرعة التحميل والاستئناف التلقائي.")
                        }
                    }
                    zos.write(readme.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                bos.toByteArray()
            }
            else -> {
                val pdfHeader = buildString {
                    append("%PDF-1.4\n")
                    append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
                    append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n")
                    append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n")
                    val textStream = "BT /F1 20 Tf 72 700 Td (Madar Browser - Custom Download Manager) Tj 0 -36 Td (File: ${item.fileName}) Tj 0 -36 Td (Scoped Storage & Resumable Engine Verified!) Tj ET"
                    append("4 0 obj << /Length ${textStream.length} >> stream\n$textStream\nendstream endobj\n")
                    append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")
                    repeat(22000) { idx ->
                        append("% Madar Browser Resumable Stream Padding Block #$idx - Supporting Pause & Resume testing\n")
                    }
                    append("xref\n0 6\n0000000000 65535 f \n")
                    append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n9\n%%EOF\n")
                }
                pdfHeader.toByteArray(Charsets.UTF_8)
            }
        }
    }
}
