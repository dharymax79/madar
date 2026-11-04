package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomFolderOption
import com.example.data.DownloadCategory
import com.example.data.PendingDownloadRequest
import com.example.download.StorageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pre-Download Confirmation & Customization Dialog.
 * Appears BEFORE starting any download so the user can:
 * 1. Manually edit and choose the file name before saving (while preserving or editing extension).
 * 2. Choose and specify a Custom Download Directory on the device via Android SAF (OpenDocumentTree).
 */
@Composable
fun PreDownloadDialog(
    request: PendingDownloadRequest,
    selectedFolder: CustomFolderOption,
    savedCustomFolders: List<CustomFolderOption>,
    onSelectFolderOption: (CustomFolderOption) -> Unit,
    onPickCustomDirectoryWithSaf: () -> Unit,
    onConfirmStartDownload: (customFileName: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Split suggested name into base name and extension for convenient editing
    val initialFull = request.suggestedFileName
    var baseName by remember(initialFull) {
        mutableStateOf(initialFull.substringBeforeLast('.', initialFull))
    }
    var extension by remember(initialFull) {
        val ext = initialFull.substringAfterLast('.', "")
        mutableStateOf(ext)
    }

    LaunchedEffect(request.suggestedFileName) {
        baseName = request.suggestedFileName.substringBeforeLast('.', request.suggestedFileName)
        extension = request.suggestedFileName.substringAfterLast('.', "")
    }

    val finalPreviewName = remember(baseName, extension) {
        val cleanBase = baseName.trim().ifBlank { "ملف_محمل" }
        val cleanExt = extension.trim().trimStart('.')
        if (cleanExt.isNotEmpty()) "$cleanBase.$cleanExt" else cleanBase
    }

    val category = DownloadCategory.fromMimeOrName(request.mimeType, finalPreviewName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column {
                    Text(
                        text = "تأكيد وتخصيص التحميل",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "عدّل اسم الملف وحدد مسار الحفظ قبل البدء",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // File Metadata Summary Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "النوع: ${category.arabicLabel} (${request.mimeType.substringAfterLast('/')})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = request.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (request.isFetchingMetadata) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = StorageHelper.formatBytes(request.contentLength),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // SECTION A: Editable File Name
                Text(
                    text = "أ. اسم الملف المخصص قبل الحفظ:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = baseName,
                        onValueChange = { baseName = it },
                        label = { Text("اسم الملف") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("download_filename_input")
                    )

                    OutlinedTextField(
                        value = extension,
                        onValueChange = { extension = it.trimStart('.') },
                        label = { Text("الامتداد") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(92.dp)
                            .testTag("download_extension_input")
                    )
                }

                // Quick file naming helper chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    AssistChip(
                        onClick = {
                            val dateStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            if (!baseName.contains(dateStamp)) {
                                baseName = "${baseName}_$dateStamp"
                            }
                        },
                        label = { Text("+ إضافة التاريخ") }
                    )
                    AssistChip(
                        onClick = {
                            baseName = "${category.arabicLabel}_${System.currentTimeMillis() % 10000}"
                        },
                        label = { Text("تسمية عربية تلقائية") }
                    )
                }

                HorizontalDivider()

                // SECTION B: Custom Download Directory Selector
                Text(
                    text = "ب. مجلد ومسار التخزين المخصص:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Current Selected Folder Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickCustomDirectoryWithSaf() }
                        .testTag("download_folder_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedFolder.uriString.isBlank()) {
                                Icons.Default.Folder
                            } else {
                                Icons.Default.FolderSpecial
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFolder.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedFolder.subtitlePath}/$finalPreviewName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "محدد",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Quick Folder Switcher (Default vs Saved Custom Folders)
                val defaultOption = remember {
                    CustomFolderOption(
                        uriString = "",
                        displayName = "مجلد التحميلات الافتراضي (Downloads)",
                        subtitlePath = "الذاكرة الداخلية/Download/MadarBrowser"
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = selectedFolder.uriString.isBlank(),
                        onClick = { onSelectFolderOption(defaultOption) },
                        label = { Text("التحميلات الافتراضية") }
                    )
                    savedCustomFolders.forEach { folder ->
                        FilterChip(
                            selected = selectedFolder.uriString == folder.uriString,
                            onClick = { onSelectFolderOption(folder) },
                            label = { Text(folder.displayName) }
                        )
                    }
                }

                // Button to launch Android SAF OpenDocumentTree
                OutlinedButton(
                    onClick = onPickCustomDirectoryWithSaf,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_custom_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختيار مجلد مخصص آخر على الجهاز (Scoped Storage)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmStartDownload(finalPreviewName) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_start_download_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("بدء التحميل الآن")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_download_dialog_button")
            ) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * Dialog for manually entering a direct download URL inside the Download Manager.
 */
@Composable
fun DirectDownloadInputDialog(
    onSubmitUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة رابط تحميل مباشر",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "أدخل رابط أي ملف (صورة، فيديو، مستند PDF، ملف مضغوط) ليقوم المتصفح بفحصه وفتح نافذة تخصيص الاسم والمجلد:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("رابط الملف المباشر (URL)") },
                    placeholder = { Text("https://example.com/file.pdf") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_download_url_input")
                )

                Text(
                    text = "أو اختر رابطاً تجريبياً سريعاً:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    AssistChip(
                        onClick = { urlInput = "madar://sample/madar_android_architecture_guide.pdf" },
                        label = { Text("ملف PDF تجريبي") }
                    )
                    AssistChip(
                        onClick = { urlInput = "madar://sample/madar_space_wallpaper_hd.png" },
                        label = { Text("صورة 4K PNG") }
                    )
                    AssistChip(
                        onClick = { urlInput = "madar://sample/madar_ambient_chime.wav" },
                        label = { Text("ملف صوتي WAV") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        onSubmitUrl(urlInput.trim())
                    }
                },
                enabled = urlInput.isNotBlank(),
                modifier = Modifier.testTag("submit_direct_url_button")
            ) {
                Text("فحص وتخصيص التحميل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

fun categoryIcon(category: DownloadCategory): ImageVector {
    return when (category) {
        DownloadCategory.VIDEO -> Icons.Default.VideoFile
        DownloadCategory.IMAGE -> Icons.Default.Image
        DownloadCategory.DOCUMENT -> Icons.Default.Description
        DownloadCategory.AUDIO -> Icons.Default.AudioFile
        DownloadCategory.ARCHIVE -> Icons.Default.Folder
        else -> Icons.Default.InsertDriveFile
    }
}
