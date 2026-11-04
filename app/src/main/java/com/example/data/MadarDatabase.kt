package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BrowserTabEntity::class,
        HistoryEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MadarDatabase : RoomDatabase() {

    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: MadarDatabase? = null

        fun getInstance(context: Context): MadarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MadarDatabase::class.java,
                    "madar_browser.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
