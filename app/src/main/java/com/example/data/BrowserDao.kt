package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {

    // ==================== TABS ====================
    @Query("SELECT * FROM browser_tabs ORDER BY id ASC")
    fun observeAllTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs ORDER BY id ASC")
    suspend fun getAllTabsOnce(): List<BrowserTabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: BrowserTabEntity): Long

    @Update
    suspend fun updateTab(tab: BrowserTabEntity)

    @Query("UPDATE browser_tabs SET isActive = 0")
    suspend fun deactivateAllTabs()

    @Query("UPDATE browser_tabs SET isActive = 1 WHERE id = :tabId")
    suspend fun markTabActive(tabId: Long)

    @Transaction
    suspend fun switchActiveTab(tabId: Long) {
        deactivateAllTabs()
        markTabActive(tabId)
    }

    @Query("DELETE FROM browser_tabs WHERE id = :tabId")
    suspend fun deleteTabById(tabId: Long)

    @Query("DELETE FROM browser_tabs")
    suspend fun deleteAllTabs()

    // ==================== HISTORY ====================
    @Query("SELECT * FROM browsing_history ORDER BY visitedAt DESC LIMIT 300")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity): Long

    @Query("DELETE FROM browsing_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM browsing_history")
    suspend fun clearAllHistory()

    // ==================== BOOKMARKS ====================
    @Query("SELECT * FROM bookmarks ORDER BY isQuickLink DESC, createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun getBookmarksCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteBookmarkByUrl(url: String)

    // ==================== DOWNLOADS ====================
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query(
        """
        UPDATE downloads 
        SET downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            status = :status,
            savedFileUriOrPath = :savedFileUriOrPath,
            supportsResume = :supportsResume
        WHERE id = :id
        """
    )
    suspend fun updateDownloadProgress(
        id: Long,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        status: String,
        savedFileUriOrPath: String,
        supportsResume: Boolean
    )

    @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0, errorMessage = :error WHERE id = :id")
    suspend fun updateDownloadStatus(id: Long, status: String, error: String? = null)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompletedDownloads()
}
