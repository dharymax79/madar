package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Patterns
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkEntity
import com.example.data.BrowserPreferencesState
import com.example.data.BrowserRepository
import com.example.data.BrowserTabEntity
import com.example.data.CustomFolderOption
import com.example.data.DownloadCategory
import com.example.data.DownloadEntity
import com.example.data.DownloadStatus
import com.example.data.HOME_TAB_URL
import com.example.data.HistoryEntity
import com.example.data.MadarDatabase
import com.example.data.PendingDownloadRequest
import com.example.data.SearchEngine
import com.example.data.SettingsPreferences
import com.example.download.DownloadEngine
import com.example.download.StorageHelper
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

enum class MainNavSection {
    BROWSER,
    DOWNLOADS,
    BOOKMARKS_HISTORY,
    SETTINGS_GUIDE
}

enum class DownloadStatusFilter(val arabicLabel: String) {
    ALL("جميع الحالات"),
    ACTIVE("النشطة والمتوقفة"),
    COMPLETED("المكتملة")
}

data class WebViewSessionState(
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val pageTitle: String = "الصفحة الرئيسية",
    val currentUrl: String = HOME_TAB_URL
)

data class LongPressWebElement(
    val url: String,
    val titleOrType: String,
    val isMediaOrImage: Boolean
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MadarDatabase.getInstance(application)
    private val dao = database.browserDao()
    private val repository = BrowserRepository(dao)
    private val settingsPrefs = SettingsPreferences(application)
    val downloadEngine = DownloadEngine.getInstance(application)
    val completedPopupDownload: StateFlow<DownloadEntity?> = downloadEngine.completedPopupDownload

    val preferencesState: StateFlow<BrowserPreferencesState> = settingsPrefs.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BrowserPreferencesState()
        )

    val tabs: StateFlow<List<BrowserTabEntity>> = repository.tabsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeTab: StateFlow<BrowserTabEntity?> = tabs.combine(MutableStateFlow(Unit)) { list, _ ->
        list.find { it.isActive } ?: list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val history: StateFlow<List<HistoryEntity>> = repository.historyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allDownloads: StateFlow<List<DownloadEntity>> = repository.downloadsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentSection = MutableStateFlow(MainNavSection.BROWSER)
    val currentSection: StateFlow<MainNavSection> = _currentSection.asStateFlow()

    private val _webViewState = MutableStateFlow(WebViewSessionState())
    val webViewState: StateFlow<WebViewSessionState> = _webViewState.asStateFlow()

    private val _showTabsSheet = MutableStateFlow(false)
    val showTabsSheet: StateFlow<Boolean> = _showTabsSheet.asStateFlow()

    private val _showAddBookmarkDialog = MutableStateFlow(false)
    val showAddBookmarkDialog: StateFlow<Boolean> = _showAddBookmarkDialog.asStateFlow()

    private val _showDirectDownloadDialog = MutableStateFlow(false)
    val showDirectDownloadDialog: StateFlow<Boolean> = _showDirectDownloadDialog.asStateFlow()

    private val _pendingDownloadRequest = MutableStateFlow<PendingDownloadRequest?>(null)
    val pendingDownloadRequest: StateFlow<PendingDownloadRequest?> = _pendingDownloadRequest.asStateFlow()

    private val _selectedFolderInDialog = MutableStateFlow<CustomFolderOption?>(null)
    val selectedFolderInDialog: StateFlow<CustomFolderOption?> = _selectedFolderInDialog.asStateFlow()

    private val _longPressElement = MutableStateFlow<LongPressWebElement?>(null)
    val longPressElement: StateFlow<LongPressWebElement?> = _longPressElement.asStateFlow()

    private val _selectedDownloadCategory = MutableStateFlow(DownloadCategory.ALL)
    val selectedDownloadCategory: StateFlow<DownloadCategory> = _selectedDownloadCategory.asStateFlow()

    private val _selectedStatusFilter = MutableStateFlow(DownloadStatusFilter.ALL)
    val selectedStatusFilter: StateFlow<DownloadStatusFilter> = _selectedStatusFilter.asStateFlow()

    private val _downloadSearchQuery = MutableStateFlow("")
    val downloadSearchQuery: StateFlow<String> = _downloadSearchQuery.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Trigger counter for WebView imperative commands (reload, back, forward)
    private val _webCommandSeq = MutableStateFlow<WebCommand?>(null)
    val webCommandSeq: StateFlow<WebCommand?> = _webCommandSeq.asStateFlow()

    fun dismissCompletedPopup() {
        downloadEngine.dismissCompletedPopup()
    }

    val filteredDownloads: StateFlow<List<DownloadEntity>> = combine(
        allDownloads,
        _selectedDownloadCategory,
        _selectedStatusFilter,
        _downloadSearchQuery
    ) { list, category, statusFilter, query ->
        list.filter { item ->
            val matchesCategory = category == DownloadCategory.ALL || item.categoryEnum == category
            val matchesStatus = when (statusFilter) {
                DownloadStatusFilter.ALL -> true
                DownloadStatusFilter.ACTIVE -> item.statusEnum in listOf(
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.PAUSED,
                    DownloadStatus.PENDING,
                    DownloadStatus.FAILED
                )
                DownloadStatusFilter.COMPLETED -> item.statusEnum == DownloadStatus.COMPLETED
            }
            val matchesQuery = query.isBlank() ||
                item.fileName.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true)
            matchesCategory && matchesStatus && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialData()
        }
    }

    fun selectSection(section: MainNavSection) {
        _currentSection.value = section
    }

    fun setShowTabsSheet(show: Boolean) {
        _showTabsSheet.value = show
    }

    fun setShowAddBookmarkDialog(show: Boolean) {
        _showAddBookmarkDialog.value = show
    }

    fun setShowDirectDownloadDialog(show: Boolean) {
        _showDirectDownloadDialog.value = show
    }

    fun setLongPressElement(element: LongPressWebElement?) {
        _longPressElement.value = element
    }

    fun setDownloadCategoryFilter(category: DownloadCategory) {
        _selectedDownloadCategory.value = category
    }

    fun setDownloadStatusFilter(filter: DownloadStatusFilter) {
        _selectedStatusFilter.value = filter
    }

    fun setDownloadSearchQuery(query: String) {
        _downloadSearchQuery.value = query
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    // ==================== BROWSER & TABS ACTIONS ====================

    fun navigateToInput(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val resolvedUrl = resolveInputToUrl(trimmed, preferencesState.value.searchEngine)
        _currentSection.value = MainNavSection.BROWSER

        viewModelScope.launch {
            val current = activeTab.value
            if (current != null) {
                repository.updateTab(
                    current.copy(
                        url = resolvedUrl,
                        title = if (resolvedUrl == HOME_TAB_URL) "الصفحة الرئيسية" else trimmed
                    )
                )
            } else {
                repository.createNewTab(url = resolvedUrl, title = trimmed)
            }
            if (resolvedUrl != HOME_TAB_URL) {
                _webCommandSeq.value = WebCommand.LoadUrl(resolvedUrl)
            }
        }
    }

    fun openHomeInCurrentTab() {
        _currentSection.value = MainNavSection.BROWSER
        viewModelScope.launch {
            val current = activeTab.value ?: return@launch
            repository.updateTab(
                current.copy(
                    url = HOME_TAB_URL,
                    title = "الصفحة الرئيسية"
                )
            )
            _webViewState.update {
                it.copy(
                    isLoading = false,
                    progress = 0f,
                    pageTitle = "الصفحة الرئيسية",
                    currentUrl = HOME_TAB_URL
                )
            }
        }
    }

    /**
     * Opens an interactive built-in HTML test page inside the WebView containing downloadable
     * images, documents, audio, and video elements so the user can test clicking links and
     * long-pressing images directly inside WebView!
     */
    fun openInteractiveDownloadTestWebPage() {
        _currentSection.value = MainNavSection.BROWSER
        val testPageUrl = "https://madar-test.local/download-lab"
        viewModelScope.launch {
            val current = activeTab.value
            if (current != null) {
                repository.updateTab(
                    current.copy(
                        url = testPageUrl,
                        title = "بوابة اختبار التحميل المباشر"
                    )
                )
            } else {
                repository.createNewTab(url = testPageUrl, title = "بوابة اختبار التحميل المباشر")
            }
            _webCommandSeq.value = WebCommand.LoadUrl(testPageUrl)
        }
    }

    fun createNewTab(url: String = HOME_TAB_URL, title: String = "الصفحة الرئيسية") {
        _currentSection.value = MainNavSection.BROWSER
        _showTabsSheet.value = false
        viewModelScope.launch {
            repository.createNewTab(url, title)
            if (url != HOME_TAB_URL) {
                _webCommandSeq.value = WebCommand.LoadUrl(url)
            }
        }
    }

    fun switchTab(tabId: Long) {
        _showTabsSheet.value = false
        _currentSection.value = MainNavSection.BROWSER
        viewModelScope.launch {
            repository.switchTab(tabId)
        }
    }

    fun closeTab(tabId: Long) {
        viewModelScope.launch {
            repository.closeTab(tabId)
        }
    }

    fun closeAllTabs() {
        _showTabsSheet.value = false
        viewModelScope.launch {
            repository.closeAllTabs()
            showMessage("تم إغلاق جميع التبويبات وفتح صفحة رئيسية جديدة")
        }
    }

    fun toggleDesktopMode() {
        val current = activeTab.value ?: return
        val nextDesktop = !current.isDesktopMode
        viewModelScope.launch {
            repository.updateTab(current.copy(isDesktopMode = nextDesktop))
            _webCommandSeq.value = WebCommand.Reload
            showMessage(if (nextDesktop) "تم تفعيل وضع سطح المكتب (Desktop Site)" else "تم العودة إلى وضع الهاتف المحمول")
        }
    }

    fun sendWebCommand(command: WebCommand) {
        _webCommandSeq.value = command
    }

    fun clearWebCommand() {
        _webCommandSeq.value = null
    }

    fun onWebViewPageStarted(url: String, title: String?) {
        _webViewState.update {
            it.copy(
                isLoading = true,
                progress = 0.1f,
                currentUrl = url,
                pageTitle = title?.takeIf { t -> t.isNotBlank() } ?: it.pageTitle
            )
        }
    }

    fun onWebViewProgressChanged(progressFraction: Float, canGoBack: Boolean, canGoForward: Boolean) {
        _webViewState.update {
            it.copy(
                progress = progressFraction,
                isLoading = progressFraction < 1.0f,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun onWebViewPageFinished(url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) {
        val cleanTitle = title?.takeIf { it.isNotBlank() } ?: url
        _webViewState.update {
            it.copy(
                isLoading = false,
                progress = 1.0f,
                currentUrl = url,
                pageTitle = cleanTitle,
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
        viewModelScope.launch {
            val current = activeTab.value
            if (current != null && current.url != HOME_TAB_URL) {
                repository.updateTab(
                    current.copy(
                        url = url,
                        title = cleanTitle
                    )
                )
            }
            if (!url.startsWith("https://madar-test.local")) {
                repository.recordHistory(cleanTitle, url)
            }
        }
    }

    // ==================== BOOKMARKS & HISTORY ====================

    fun toggleBookmarkForCurrentPage() {
        val current = activeTab.value ?: return
        val url = current.url
        if (url == HOME_TAB_URL) {
            _showAddBookmarkDialog.value = true
            return
        }
        val existing = bookmarks.value.find { it.url == url }
        viewModelScope.launch {
            if (existing != null) {
                repository.removeBookmarkByUrl(url)
                showMessage("تمت إزالة الصفحة من الإشارات المرجعية")
            } else {
                repository.addBookmark(title = current.title, url = url, isQuickLink = true)
                showMessage("تم حفظ الصفحة في الإشارات المرجعية والاختصارات السريعة")
            }
        }
    }

    fun addCustomBookmark(title: String, rawUrl: String, isQuickLink: Boolean) {
        val resolvedUrl = resolveInputToUrl(rawUrl, preferencesState.value.searchEngine)
        viewModelScope.launch {
            repository.addBookmark(title = title.ifBlank { resolvedUrl }, url = resolvedUrl, isQuickLink = isQuickLink)
            _showAddBookmarkDialog.value = false
            showMessage("تمت إضافة الاختصار بنجاح")
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.removeBookmarkById(id)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            showMessage("تم مسح سجل التصفح بالكامل")
        }
    }

    // ==================== CUSTOM DOWNLOAD MANAGER & PRE-DOWNLOAD DIALOG ====================

    /**
     * Triggered whenever a download link is clicked in WebView, an image/video is long-pressed,
     * a sample file is clicked, or a manual link is entered.
     * Opens the Pre-Download Dialog so the user can edit the filename and pick the target directory.
     */
    fun initiateDownloadInterception(
        url: String,
        userAgent: String = "",
        contentDisposition: String? = null,
        mimeType: String? = null,
        contentLength: Long = -1L,
        sourceLabel: String = "صفحة الويب"
    ) {
        if (url.isBlank()) return
        val cookies = runCatching { CookieManager.getInstance().getCookie(url).orEmpty() }.getOrDefault("")
        val initialFileName = StorageHelper.guessCleanFileName(url, contentDisposition, mimeType)
        val resolvedMime = StorageHelper.resolveMimeType(initialFileName, mimeType)

        val prefs = preferencesState.value
        _selectedFolderInDialog.value = CustomFolderOption(
            uriString = prefs.defaultCustomFolderUri,
            displayName = prefs.defaultCustomFolderName,
            subtitlePath = if (prefs.defaultCustomFolderUri.isBlank()) {
                "الذاكرة الداخلية/Download/MadarBrowser"
            } else {
                prefs.savedCustomFolders.find { it.uriString == prefs.defaultCustomFolderUri }?.subtitlePath
                    ?: "مجلد مخصص عبر نظام Scoped Storage"
            }
        )

        val initialRequest = PendingDownloadRequest(
            url = url,
            suggestedFileName = initialFileName,
            mimeType = resolvedMime,
            contentLength = contentLength,
            userAgent = userAgent,
            cookies = cookies,
            sourceLabel = sourceLabel,
            isFetchingMetadata = true
        )
        _pendingDownloadRequest.value = initialRequest

        viewModelScope.launch {
            val enriched = downloadEngine.inspectUrlMetadata(initialRequest)
            // Update if dialog is still showing for the same URL
            _pendingDownloadRequest.update { current ->
                if (current != null && current.url == url) enriched else current
            }
        }
    }

    fun dismissPendingDownloadDialog() {
        _pendingDownloadRequest.value = null
    }

    fun selectFolderOptionInDialog(option: CustomFolderOption) {
        _selectedFolderInDialog.value = option
    }

    /**
     * Called when the user selects a custom directory using Android's `OpenDocumentTree` SAF picker.
     */
    fun onCustomDirectoryPicked(treeUri: Uri, saveAsDefault: Boolean = true) {
        val context = getApplication<Application>()
        val folderOption = StorageHelper.persistAndDescribeTreeUri(context, treeUri)
        _selectedFolderInDialog.value = folderOption
        if (saveAsDefault) {
            viewModelScope.launch {
                settingsPrefs.setDefaultDownloadFolder(
                    uriString = folderOption.uriString,
                    displayName = folderOption.displayName,
                    subtitlePath = folderOption.subtitlePath
                )
            }
        }
        showMessage("تم تحديد مجلد الحفظ: ${folderOption.displayName}")
    }

    fun resetToDefaultDownloadsFolder() {
        val defaultOption = CustomFolderOption(
            uriString = "",
            displayName = "مجلد التحميلات الافتراضي (Downloads)",
            subtitlePath = "الذاكرة الداخلية/Download/MadarBrowser"
        )
        _selectedFolderInDialog.value = defaultOption
        viewModelScope.launch {
            settingsPrefs.setDefaultDownloadFolder(
                uriString = "",
                displayName = defaultOption.displayName,
                subtitlePath = defaultOption.subtitlePath
            )
        }
    }

    /**
     * Confirmed by the user inside PreDownloadDialog with their custom filename and custom folder.
     */
    fun confirmAndStartDownload(customFileName: String) {
        val request = _pendingDownloadRequest.value ?: return
        val folder = _selectedFolderInDialog.value ?: CustomFolderOption(
            uriString = "",
            displayName = "مجلد التحميلات الافتراضي (Downloads)",
            subtitlePath = "الذاكرة الداخلية/Download/MadarBrowser"
        )
        _pendingDownloadRequest.value = null

        downloadEngine.enqueueAndStartDownload(
            request = request,
            customFileName = customFileName,
            customFolderUri = folder.uriString,
            customFolderName = folder.displayName
        ) {
            showMessage("بدأ تحميل \"$customFileName\" إلى ${folder.displayName}")
        }
    }

    fun pauseDownload(id: Long) = downloadEngine.pauseDownload(id)
    fun resumeDownload(id: Long) = downloadEngine.resumeDownload(id)
    fun retryDownload(id: Long) = downloadEngine.retryDownload(id)
    fun deleteDownload(id: Long, deleteFileFromDisk: Boolean) =
        downloadEngine.deleteDownload(id, deleteFileFromDisk)

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            repository.clearCompletedDownloads()
            showMessage("تم مسح سجل التحميلات المكتملة")
        }
    }

    // ==================== SETTINGS ACTIONS ====================

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsPrefs.setThemeMode(mode) }
    }

    fun setRtlDirection(isRtl: Boolean) {
        viewModelScope.launch { settingsPrefs.setRtlDirection(isRtl) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsPrefs.setSearchEngine(engine) }
    }

    fun setJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsPrefs.setJavaScriptEnabled(enabled) }
    }

    fun setCookiesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPrefs.setCookiesEnabled(enabled)
            runCatching { CookieManager.getInstance().setAcceptCookie(enabled) }
        }
    }

    private fun resolveInputToUrl(input: String, engine: SearchEngine): String {
        val trimmed = input.trim()
        if (trimmed == HOME_TAB_URL || trimmed.startsWith("madar://")) return trimmed
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val looksLikeDomain = !trimmed.contains(' ') &&
            (Patterns.WEB_URL.matcher(trimmed).matches() || trimmed.contains('.'))
        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            val encoded = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
            String.format(engine.searchUrlTemplate, encoded)
        }
    }
}

sealed class WebCommand {
    data class LoadUrl(val url: String) : WebCommand()
    data object Reload : WebCommand()
    data object StopLoading : WebCommand()
    data object GoBack : WebCommand()
    data object GoForward : WebCommand()
}
