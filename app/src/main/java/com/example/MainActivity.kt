package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.CustomFolderOption
import com.example.data.DownloadStatus
import com.example.data.HOME_TAB_URL
import com.example.download.DownloadNotificationHelper
import com.example.download.StorageHelper
import com.example.ui.BrowserViewModel
import com.example.ui.MainNavSection
import com.example.ui.WebCommand
import com.example.ui.components.BrowserWebView
import com.example.ui.components.CompletedDownloadPopup
import com.example.ui.components.DirectDownloadInputDialog
import com.example.ui.components.OmniboxTopBar
import com.example.ui.components.PreDownloadDialog
import com.example.ui.screens.AddBookmarkDialog
import com.example.ui.screens.BookmarksHistoryScreen
import com.example.ui.screens.DownloadManagerScreen
import com.example.ui.screens.HomeTabScreen
import com.example.ui.screens.SettingsAndGuideScreen
import com.example.ui.screens.TabsSwitcherBottomSheet
import com.example.ui.theme.MadarBrowserTheme

class MainActivity : ComponentActivity() {

    private var openDownloadsRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadNotificationHelper.ensureChannelsCreated(this)
        if (intent?.getBooleanExtra("OPEN_DOWNLOADS_TAB", false) == true) {
            openDownloadsRequested = true
        }
        enableEdgeToEdge()
        setContent {
            val viewModel: BrowserViewModel = viewModel()
            val prefs by viewModel.preferencesState.collectAsStateWithLifecycle()

            LaunchedEffect(openDownloadsRequested) {
                if (openDownloadsRequested) {
                    viewModel.selectSection(MainNavSection.DOWNLOADS)
                    openDownloadsRequested = false
                }
            }

            // Handle external http/https intent if app was launched as a browser link handler
            LaunchedEffect(intent?.dataString) {
                val incomingUrl = intent?.dataString
                if (!incomingUrl.isNullOrBlank() &&
                    (incomingUrl.startsWith("http://") || incomingUrl.startsWith("https://"))
                ) {
                    viewModel.navigateToInput(incomingUrl)
                }
            }

            MadarBrowserTheme(
                themeMode = prefs.themeMode,
                isRtl = prefs.isRtl
            ) {
                MadarBrowserApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_DOWNLOADS_TAB", false)) {
            openDownloadsRequested = true
        }
    }
}

@Composable
fun MadarBrowserApp(viewModel: BrowserViewModel) {
    val context = LocalContext.current
    val prefs by viewModel.preferencesState.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val filteredDownloads by viewModel.filteredDownloads.collectAsStateWithLifecycle()
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val webViewState by viewModel.webViewState.collectAsStateWithLifecycle()
    val showTabsSheet by viewModel.showTabsSheet.collectAsStateWithLifecycle()
    val showAddBookmarkDialog by viewModel.showAddBookmarkDialog.collectAsStateWithLifecycle()
    val showDirectDownloadDialog by viewModel.showDirectDownloadDialog.collectAsStateWithLifecycle()
    val pendingDownloadRequest by viewModel.pendingDownloadRequest.collectAsStateWithLifecycle()
    val selectedFolderInDialog by viewModel.selectedFolderInDialog.collectAsStateWithLifecycle()
    val completedPopupDownload by viewModel.completedPopupDownload.collectAsStateWithLifecycle()
    val longPressElement by viewModel.longPressElement.collectAsStateWithLifecycle()
    val selectedDownloadCategory by viewModel.selectedDownloadCategory.collectAsStateWithLifecycle()
    val selectedStatusFilter by viewModel.selectedStatusFilter.collectAsStateWithLifecycle()
    val downloadSearchQuery by viewModel.downloadSearchQuery.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val webCommand by viewModel.webCommandSeq.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // SAF OpenDocumentTree Launcher for selecting a Custom Download Directory
    val safFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onCustomDirectoryPicked(treeUri, saveAsDefault = true)
        }
    }

    // Runtime permission launcher for Android 13+ POST_NOTIFICATIONS and Android <= 9 legacy storage
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val currentUrl = activeTab?.url ?: HOME_TAB_URL
    val isBookmarked = remember(currentUrl, bookmarks) {
        bookmarks.any { it.url == currentUrl }
    }
    val activeDownloadsCount = remember(allDownloads) {
        allDownloads.count {
            it.statusEnum == DownloadStatus.DOWNLOADING || it.statusEnum == DownloadStatus.PENDING
        }
    }

    // Back button handling across sections and WebView history
    BackHandler(
        enabled = currentSection != MainNavSection.BROWSER ||
            (currentUrl != HOME_TAB_URL && webViewState.canGoBack) ||
            currentUrl != HOME_TAB_URL
    ) {
        when {
            currentSection != MainNavSection.BROWSER -> {
                viewModel.selectSection(MainNavSection.BROWSER)
            }
            webViewState.canGoBack -> {
                viewModel.sendWebCommand(WebCommand.GoBack)
            }
            currentUrl != HOME_TAB_URL -> {
                viewModel.openHomeInCurrentTab()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            OmniboxTopBar(
                currentUrl = currentUrl,
                isLoading = webViewState.isLoading && currentUrl != HOME_TAB_URL,
                progress = webViewState.progress,
                tabsCount = tabs.size.coerceAtLeast(1),
                activeDownloadsCount = activeDownloadsCount,
                isBookmarked = isBookmarked,
                isDesktopMode = activeTab?.isDesktopMode == true,
                canGoBack = webViewState.canGoBack,
                canGoForward = webViewState.canGoForward,
                onHomeClick = { viewModel.openHomeInCurrentTab() },
                onNavigateInput = { viewModel.navigateToInput(it) },
                onReloadOrStopClick = {
                    if (webViewState.isLoading) {
                        viewModel.sendWebCommand(WebCommand.StopLoading)
                    } else {
                        viewModel.sendWebCommand(WebCommand.Reload)
                    }
                },
                onBackClick = { viewModel.sendWebCommand(WebCommand.GoBack) },
                onForwardClick = { viewModel.sendWebCommand(WebCommand.GoForward) },
                onBookmarkToggle = { viewModel.toggleBookmarkForCurrentPage() },
                onTabsClick = { viewModel.setShowTabsSheet(true) },
                onNewTabClick = { viewModel.createNewTab() },
                onToggleDesktopMode = { viewModel.toggleDesktopMode() },
                onOpenSection = { viewModel.selectSection(it) },
                onOpenDirectDownloadDialog = { viewModel.setShowDirectDownloadDialog(true) }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentSection == MainNavSection.BROWSER,
                    onClick = { viewModel.selectSection(MainNavSection.BROWSER) },
                    icon = {
                        Icon(
                            imageVector = if (currentSection == MainNavSection.BROWSER) {
                                Icons.Filled.Public
                            } else {
                                Icons.Outlined.Public
                            },
                            contentDescription = "المتصفح"
                        )
                    },
                    label = { Text("المتصفح") },
                    modifier = Modifier.testTag("nav_tab_browser")
                )

                NavigationBarItem(
                    selected = currentSection == MainNavSection.DOWNLOADS,
                    onClick = { viewModel.selectSection(MainNavSection.DOWNLOADS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (allDownloads.isNotEmpty()) {
                                    Badge { Text(allDownloads.size.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (currentSection == MainNavSection.DOWNLOADS) {
                                    Icons.Filled.Download
                                } else {
                                    Icons.Outlined.Download
                                },
                                contentDescription = "التحميلات"
                            )
                        }
                    },
                    label = { Text("التحميلات") },
                    modifier = Modifier.testTag("nav_tab_downloads")
                )

                NavigationBarItem(
                    selected = currentSection == MainNavSection.BOOKMARKS_HISTORY,
                    onClick = { viewModel.selectSection(MainNavSection.BOOKMARKS_HISTORY) },
                    icon = {
                        Icon(
                            imageVector = if (currentSection == MainNavSection.BOOKMARKS_HISTORY) {
                                Icons.Filled.Bookmarks
                            } else {
                                Icons.Outlined.Bookmarks
                            },
                            contentDescription = "المفضلة والسجل"
                        )
                    },
                    label = { Text("المفضلة والسجل") },
                    modifier = Modifier.testTag("nav_tab_bookmarks")
                )

                NavigationBarItem(
                    selected = currentSection == MainNavSection.SETTINGS_GUIDE,
                    onClick = { viewModel.selectSection(MainNavSection.SETTINGS_GUIDE) },
                    icon = {
                        Icon(
                            imageVector = if (currentSection == MainNavSection.SETTINGS_GUIDE) {
                                Icons.Filled.Settings
                            } else {
                                Icons.Outlined.Settings
                            },
                            contentDescription = "الإعدادات"
                        )
                    },
                    label = { Text("الإعدادات") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentSection) {
                MainNavSection.BROWSER -> {
                    val tab = activeTab
                    if (tab == null || tab.url == HOME_TAB_URL) {
                        HomeTabScreen(
                            quickLinks = bookmarks.filter { it.isQuickLink },
                            downloads = allDownloads,
                            defaultFolderName = prefs.defaultCustomFolderName,
                            onOpenUrl = { viewModel.navigateToInput(it) },
                            onAddQuickLinkClick = { viewModel.setShowAddBookmarkDialog(true) },
                            onOpenDownloadManager = { viewModel.selectSection(MainNavSection.DOWNLOADS) },
                            onOpenDirectDownloadDialog = { viewModel.setShowDirectDownloadDialog(true) },
                            onTriggerSampleDownload = { preset ->
                                viewModel.initiateDownloadInterception(
                                    url = preset.url,
                                    mimeType = preset.mimeType,
                                    sourceLabel = "مختبر التحميل الفوري"
                                )
                            },
                            onOpenInteractiveWebTestPortal = {
                                viewModel.openInteractiveDownloadTestWebPage()
                            }
                        )
                    } else {
                        BrowserWebView(
                            tabId = tab.id,
                            url = tab.url,
                            javaScriptEnabled = prefs.javaScriptEnabled,
                            cookiesEnabled = prefs.cookiesEnabled,
                            isDesktopMode = tab.isDesktopMode,
                            webCommand = webCommand,
                            longPressElement = longPressElement,
                            onCommandHandled = { viewModel.clearWebCommand() },
                            onPageStarted = { startedUrl, title ->
                                viewModel.onWebViewPageStarted(startedUrl, title)
                            },
                            onProgressChanged = { prog, back, forward ->
                                viewModel.onWebViewProgressChanged(prog, back, forward)
                            },
                            onPageFinished = { finishedUrl, title, back, forward ->
                                viewModel.onWebViewPageFinished(finishedUrl, title, back, forward)
                            },
                            onDownloadIntercepted = { dlUrl, ua, disp, mime, len, source ->
                                viewModel.initiateDownloadInterception(
                                    url = dlUrl,
                                    userAgent = ua,
                                    contentDisposition = disp,
                                    mimeType = mime,
                                    contentLength = len,
                                    sourceLabel = source
                                )
                            },
                            onLongPressDetected = { viewModel.setLongPressElement(it) },
                            onOpenInNewTab = { newTabUrl ->
                                viewModel.createNewTab(url = newTabUrl, title = newTabUrl)
                            },
                            onShowMessage = { viewModel.showMessage(it) }
                        )
                    }
                }

                MainNavSection.DOWNLOADS -> {
                    DownloadManagerScreen(
                        downloads = filteredDownloads,
                        selectedCategory = selectedDownloadCategory,
                        selectedStatusFilter = selectedStatusFilter,
                        searchQuery = downloadSearchQuery,
                        defaultFolderName = prefs.defaultCustomFolderName,
                        onCategorySelected = { viewModel.setDownloadCategoryFilter(it) },
                        onStatusFilterSelected = { viewModel.setDownloadStatusFilter(it) },
                        onSearchQueryChanged = { viewModel.setDownloadSearchQuery(it) },
                        onOpenDirectDownloadDialog = { viewModel.setShowDirectDownloadDialog(true) },
                        onPickDefaultFolderSaf = { safFolderPickerLauncher.launch(null) },
                        onPauseDownload = { viewModel.pauseDownload(it) },
                        onResumeDownload = { viewModel.resumeDownload(it) },
                        onRetryDownload = { viewModel.retryDownload(it) },
                        onDeleteDownload = { id, deleteDisk -> viewModel.deleteDownload(id, deleteDisk) },
                        onClearCompleted = { viewModel.clearCompletedDownloads() },
                        onTriggerSampleDownload = { preset ->
                            viewModel.initiateDownloadInterception(
                                url = preset.url,
                                mimeType = preset.mimeType,
                                sourceLabel = "مدير التحميلات"
                            )
                        },
                        onShowMessage = { viewModel.showMessage(it) }
                    )
                }

                MainNavSection.BOOKMARKS_HISTORY -> {
                    BookmarksHistoryScreen(
                        bookmarks = bookmarks,
                        history = history,
                        onOpenUrl = { viewModel.navigateToInput(it) },
                        onAddBookmarkClick = { viewModel.setShowAddBookmarkDialog(true) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onDeleteHistoryItem = { viewModel.deleteHistoryItem(it) },
                        onClearAllHistory = { viewModel.clearAllHistory() }
                    )
                }

                MainNavSection.SETTINGS_GUIDE -> {
                    SettingsAndGuideScreen(
                        prefs = prefs,
                        onThemeModeChanged = { viewModel.setThemeMode(it) },
                        onRtlChanged = { viewModel.setRtlDirection(it) },
                        onSearchEngineChanged = { viewModel.setSearchEngine(it) },
                        onJavaScriptChanged = { viewModel.setJavaScriptEnabled(it) },
                        onCookiesChanged = { viewModel.setCookiesEnabled(it) },
                        onPickDefaultFolderSaf = { safFolderPickerLauncher.launch(null) },
                        onResetDefaultFolder = { viewModel.resetToDefaultDownloadsFolder() }
                    )
                }
            }
        }
    }

    // ==================== DIALOGS & SHEETS ====================

    // 1. Pre-Download Confirmation & Customization Dialog (Edit Filename + Choose Custom Folder)
    val activeRequest = pendingDownloadRequest
    if (activeRequest != null) {
        val currentFolder = selectedFolderInDialog ?: CustomFolderOption(
            uriString = prefs.defaultCustomFolderUri,
            displayName = prefs.defaultCustomFolderName,
            subtitlePath = "الذاكرة الداخلية/Download/MadarBrowser"
        )
        PreDownloadDialog(
            request = activeRequest,
            selectedFolder = currentFolder,
            savedCustomFolders = prefs.savedCustomFolders,
            onSelectFolderOption = { viewModel.selectFolderOptionInDialog(it) },
            onPickCustomDirectoryWithSaf = { safFolderPickerLauncher.launch(null) },
            onConfirmStartDownload = { customFileName ->
                viewModel.confirmAndStartDownload(customFileName)
            },
            onDismiss = { viewModel.dismissPendingDownloadDialog() }
        )
    }

    // 2. Compact Completed Download Popup Dialog (Appears on completion with vibration)
    val completedItem = completedPopupDownload
    if (completedItem != null) {
        CompletedDownloadPopup(
            item = completedItem,
            onOpenFile = {
                StorageHelper.openDownloadedFile(
                    context = context,
                    item = completedItem,
                    onError = { viewModel.showMessage(it) }
                )
            },
            onShareFile = {
                StorageHelper.shareDownloadedFile(
                    context = context,
                    item = completedItem,
                    onError = { viewModel.showMessage(it) }
                )
            },
            onViewInDownloads = {
                viewModel.selectSection(MainNavSection.DOWNLOADS)
            },
            onDismiss = { viewModel.dismissCompletedPopup() }
        )
    }

    // 3. Direct Download URL Entry Dialog
    if (showDirectDownloadDialog) {
        DirectDownloadInputDialog(
            onSubmitUrl = { enteredUrl ->
                viewModel.setShowDirectDownloadDialog(false)
                viewModel.initiateDownloadInterception(
                    url = enteredUrl,
                    sourceLabel = "رابط مباشر يدوي"
                )
            },
            onDismiss = { viewModel.setShowDirectDownloadDialog(false) }
        )
    }

    // 4. Open Tabs Switcher Bottom Sheet
    if (showTabsSheet) {
        TabsSwitcherBottomSheet(
            tabs = tabs,
            onSelectTab = { viewModel.switchTab(it) },
            onCloseTab = { viewModel.closeTab(it) },
            onNewTab = { viewModel.createNewTab() },
            onCloseAllTabs = { viewModel.closeAllTabs() },
            onDismiss = { viewModel.setShowTabsSheet(false) }
        )
    }

    // 5. Add Bookmark / Quick Link Dialog
    if (showAddBookmarkDialog) {
        AddBookmarkDialog(
            initialTitle = if (currentUrl == HOME_TAB_URL) "" else activeTab?.title.orEmpty(),
            initialUrl = if (currentUrl == HOME_TAB_URL) "" else currentUrl,
            onConfirm = { title, url, isQuickLink ->
                viewModel.addCustomBookmark(title, url, isQuickLink)
            },
            onDismiss = { viewModel.setShowAddBookmarkDialog(false) }
        )
    }
}
