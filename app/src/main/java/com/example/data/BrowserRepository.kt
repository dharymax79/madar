package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val dao: BrowserDao) {

    val tabsFlow: Flow<List<BrowserTabEntity>> = dao.observeAllTabs()
    val historyFlow: Flow<List<HistoryEntity>> = dao.observeHistory()
    val bookmarksFlow: Flow<List<BookmarkEntity>> = dao.observeBookmarks()
    val downloadsFlow: Flow<List<DownloadEntity>> = dao.observeDownloads()

    suspend fun ensureInitialData() {
        val currentTabs = dao.getAllTabsOnce()
        if (currentTabs.isEmpty()) {
            dao.insertTab(
                BrowserTabEntity(
                    title = "الصفحة الرئيسية",
                    url = HOME_TAB_URL,
                    isActive = true
                )
            )
        }
        val bookmarksCount = dao.getBookmarksCount()
        if (bookmarksCount == 0) {
            val defaultQuickLinks = listOf(
                BookmarkEntity(
                    title = "محرك Google",
                    url = "https://www.google.com",
                    category = "بحث",
                    isQuickLink = true,
                    accentHex = "#0284C7"
                ),
                BookmarkEntity(
                    title = "ويكيبيديا العربية",
                    url = "https://ar.wikipedia.org",
                    category = "موسوعة",
                    isQuickLink = true,
                    accentHex = "#0D9488"
                ),
                BookmarkEntity(
                    title = "أرشيف الإنترنت",
                    url = "https://archive.org",
                    category = "مكتبة وملفات",
                    isQuickLink = true,
                    accentHex = "#4F46E5"
                ),
                BookmarkEntity(
                    title = "GitHub",
                    url = "https://github.com",
                    category = "برمجة",
                    isQuickLink = true,
                    accentHex = "#334155"
                ),
                BookmarkEntity(
                    title = "الجزيرة نت",
                    url = "https://www.aljazeera.net",
                    category = "أخبار",
                    isQuickLink = true,
                    accentHex = "#D97706"
                ),
                BookmarkEntity(
                    title = "مطورو أندرويد",
                    url = "https://developer.android.com",
                    category = "تقنية",
                    isQuickLink = true,
                    accentHex = "#059669"
                )
            )
            defaultQuickLinks.forEach { dao.insertBookmark(it) }
        }
    }

    suspend fun createNewTab(url: String = HOME_TAB_URL, title: String = "الصفحة الرئيسية"): Long {
        dao.deactivateAllTabs()
        return dao.insertTab(
            BrowserTabEntity(
                title = title,
                url = url,
                isActive = true
            )
        )
    }

    suspend fun switchTab(tabId: Long) {
        dao.switchActiveTab(tabId)
    }

    suspend fun updateTab(tab: BrowserTabEntity) {
        dao.updateTab(tab.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun closeTab(tabId: Long) {
        val allTabs = dao.getAllTabsOnce()
        val target = allTabs.find { it.id == tabId } ?: return
        dao.deleteTabById(tabId)
        val remaining = dao.getAllTabsOnce()
        if (remaining.isEmpty()) {
            createNewTab()
        } else if (target.isActive) {
            dao.switchActiveTab(remaining.last().id)
        }
    }

    suspend fun closeAllTabs() {
        dao.deleteAllTabs()
        createNewTab()
    }

    suspend fun recordHistory(title: String, url: String) {
        if (url.isBlank() || url == HOME_TAB_URL || url.startsWith("data:") || url.startsWith("about:")) return
        dao.insertHistory(
            HistoryEntity(
                title = title.ifBlank { url },
                url = url,
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistoryItem(id: Long) = dao.deleteHistoryById(id)
    suspend fun clearHistory() = dao.clearAllHistory()

    suspend fun addBookmark(title: String, url: String, category: String = "عام", isQuickLink: Boolean = true) {
        dao.insertBookmark(
            BookmarkEntity(
                title = title.ifBlank { url },
                url = url,
                category = category,
                isQuickLink = isQuickLink,
                accentHex = listOf("#0284C7", "#0D9488", "#4F46E5", "#D97706", "#059669", "#E11D48").random()
            )
        )
    }

    suspend fun removeBookmarkById(id: Long) = dao.deleteBookmarkById(id)
    suspend fun removeBookmarkByUrl(url: String) = dao.deleteBookmarkByUrl(url)

    suspend fun deleteDownload(id: Long) = dao.deleteDownloadById(id)
    suspend fun clearCompletedDownloads() = dao.clearCompletedDownloads()
}
