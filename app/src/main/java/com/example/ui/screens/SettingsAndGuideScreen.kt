package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BrowserPreferencesState
import com.example.data.SearchEngine
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.SuccessGreen

@Composable
fun SettingsAndGuideScreen(
    prefs: BrowserPreferencesState,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onRtlChanged: (Boolean) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onJavaScriptChanged: (Boolean) -> Unit,
    onCookiesChanged: (Boolean) -> Unit,
    onPickDefaultFolderSaf: () -> Unit,
    onResetDefaultFolder: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_guide_screen")
    ) {
        // 1. Theme & RTL Section
        item {
            SettingsCard(
                title = "المظهر، الوضع المظلم، واتجاه الواجهة (RTL)",
                icon = Icons.Default.DarkMode
            ) {
                Text(
                    text = "وضع الإضاءة والألوان:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                val modes = listOf(
                    AppThemeMode.SYSTEM to "تلقائي",
                    AppThemeMode.LIGHT to "فاتح",
                    AppThemeMode.DARK to "مظلم"
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = prefs.themeMode == mode,
                            onClick = { onThemeModeChanged(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        ) {
                            Text(label)
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "اتجاه الواجهة من اليمين إلى اليسار (RTL)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "دعم كامل للغة العربية وتخطيط القوائم والأزرار",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.isRtl,
                        onCheckedChange = onRtlChanged,
                        modifier = Modifier.testTag("rtl_switch")
                    )
                }
            }
        }

        // 2. Custom Download Directory & Scoped Storage Section
        item {
            SettingsCard(
                title = "إدارة مجلدات التخزين المخصصة و Scoped Storage",
                icon = Icons.Default.FolderSpecial
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "مجلد الحفظ الافتراضي الحالي:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = prefs.defaultCustomFolderName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (prefs.defaultCustomFolderUri.isBlank()) {
                                "المسار: الذاكرة الداخلية/Download/MadarBrowser"
                            } else {
                                "URI: ${prefs.defaultCustomFolderUri}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onPickDefaultFolderSaf,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اختيار مجلد مخصص")
                    }

                    if (prefs.defaultCustomFolderUri.isNotBlank()) {
                        OutlinedButton(
                            onClick = onResetDefaultFolder,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استعادة الافتراضي")
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "متوافق مع أندرويد ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) عبر Scoped Storage & Storage Access Framework بدون الحاجة لأذونات خطيرة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 3. Browser & Privacy Settings
        item {
            SettingsCard(
                title = "إعدادات المتصفح ومحرك البحث",
                icon = Icons.Default.Tune
            ) {
                Text(
                    text = "محرك البحث الافتراضي في الشريط العلوي:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                SearchEngine.entries.forEach { engine ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = prefs.searchEngine == engine,
                            onClick = { onSearchEngineChanged(engine) }
                        )
                        Column {
                            Text(
                                text = engine.arabicName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = engine.homeUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تفعيل الجافاسكريبت (JavaScript)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "ضروري لتشغيل المواقع التفاعلية الحديثة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.javaScriptEnabled,
                        onCheckedChange = onJavaScriptChanged
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "السماح بملفات تعريف الارتباط (Cookies)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "لحفظ جلسات تسجيل الدخول وتمريرها لمدير التحميلات",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.cookiesEnabled,
                        onCheckedChange = onCookiesChanged
                    )
                }
            }
        }

        // 4. Built-in Android Studio Setup & Architecture Guide (Required by Prompt #4)
        item {
            SettingsCard(
                title = "دليل إعداد وهندسة المشروع في Android Studio",
                icon = Icons.Default.Code
            ) {
                GuidePoint(
                    step = "1. المتطلبات وإعداد البيئة في Android Studio",
                    details = "• افتح المشروع في Android Studio Ladybug أو أحدث.\n" +
                        "• يعتمد المشروع على لغة Kotlin و Jetpack Compose (Material 3) مع حزمة موارد الخطوط المحلية (Cairo TTF).\n" +
                        "• تأكد من مزامنة ملفات Gradle (Sync Project with Gradle Files) لتوليد كود قاعدة البيانات Room عبر أداة KSP."
                )
                GuidePoint(
                    step = "2. هيكلية الكود المعمارية (Clean MVVM Architecture)",
                    details = "• طبقة البيانات (data/): تضم كيانات Room الأربعة (BrowserTabEntity, HistoryEntity, BookmarkEntity, DownloadEntity) ومستودع BrowserRepository وتفضيلات DataStore.\n" +
                        "• محرك التحميل (download/): يضم DownloadEngine المبني على OkHttp + Coroutines مع دعم ترويسة HTTP Range للإيقاف المؤقت والاستئناف، و StorageHelper لإدارة الملفات.\n" +
                        "• طبقة الواجهات (ui/): تضم BrowserViewModel، الشريط العلوي OmniboxTopBar، نافذة التخصيص PreDownloadDialog، وشاشة DownloadManagerScreen."
                )
                GuidePoint(
                    step = "3. آلية عمل Scoped Storage والأذونات الحديثة",
                    details = "• على أندرويد 10 فما فوق (API 29+): يستخدم التطبيق ActivityResultContracts.OpenDocumentTree لاختيار أي مجلد مخصص على الجهاز مع أخذ صلاحية دائمة (takePersistableUriPermission) والكتابة المباشرة عبر DocumentsContract.\n" +
                        "• عند الحفظ في المجلد الافتراضي: يتم الحفظ في مجلد التطبيق السريع مع التصدير التلقائي إلى MediaStore.Downloads، ويتم فتح الملفات ومشاركتها بأمان عبر FileProvider."
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun GuidePoint(step: String, details: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
