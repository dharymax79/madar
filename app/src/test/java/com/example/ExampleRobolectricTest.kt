package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DownloadCategory
import com.example.download.StorageHelper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify download helpers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("متصفح مدار", appName)

        val sanitized = StorageHelper.sanitizeFileName("ملف:تجريبي/جديد", "application/pdf")
        assertEquals("ملف_تجريبي_جديد.pdf", sanitized)

        val category = DownloadCategory.fromMimeOrName("video/mp4", "movie.mp4")
        assertEquals(DownloadCategory.VIDEO, category)
    }
}
