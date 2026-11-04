package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HOME_TAB_URL
import com.example.ui.MainNavSection
import com.example.ui.theme.SuccessGreen

@Composable
fun OmniboxTopBar(
    currentUrl: String,
    isLoading: Boolean,
    progress: Float,
    tabsCount: Int,
    activeDownloadsCount: Int,
    isBookmarked: Boolean,
    isDesktopMode: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onHomeClick: () -> Unit,
    onNavigateInput: (String) -> Unit,
    onReloadOrStopClick: () -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTabsClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onOpenSection: (MainNavSection) -> Unit,
    onOpenDirectDownloadDialog: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var textInput by remember {
        mutableStateOf(if (currentUrl == HOME_TAB_URL) "" else currentUrl)
    }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(currentUrl, isFocused) {
        if (!isFocused) {
            textInput = if (currentUrl == HOME_TAB_URL) "" else currentUrl
        }
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                // Home button
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onHomeClick()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("home_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "الصفحة الرئيسية",
                        tint = if (currentUrl == HOME_TAB_URL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Chrome-like Omnibox Address & Search Bar
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        val isHttps = currentUrl.startsWith("https://")
                        val isHome = currentUrl == HOME_TAB_URL

                        Icon(
                            imageVector = when {
                                isHome -> Icons.Default.Search
                                isHttps -> Icons.Default.Lock
                                else -> Icons.Default.Public
                            },
                            contentDescription = "حالة أمان الاتصال",
                            tint = when {
                                isHome -> MaterialTheme.colorScheme.primary
                                isHttps -> SuccessGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (textInput.isEmpty()) {
                                Text(
                                    text = "ابحث في Google أو اكتب عنوان URL...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            BasicTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        focusManager.clearFocus()
                                        onNavigateInput(textInput)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { state ->
                                        isFocused = state.isFocused
                                    }
                                    .testTag("omnibox_input")
                            )
                        }

                        if (isFocused && textInput.isNotEmpty()) {
                            IconButton(
                                onClick = { textInput = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح النص",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else if (!isHome) {
                            IconButton(
                                onClick = onBookmarkToggle,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("bookmark_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "إضافة للمفضلة",
                                    tint = if (isBookmarked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = onReloadOrStopClick,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("reload_stop_button")
                            ) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = if (isLoading) "إيقاف التحميل" else "تحديث الصفحة",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Quick Download Manager shortcut with active badge
                IconButton(
                    onClick = { onOpenSection(MainNavSection.DOWNLOADS) },
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("top_downloads_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (activeDownloadsCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ) {
                                    Text(text = activeDownloadsCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "مدير التحميلات",
                            tint = if (activeDownloadsCount > 0) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Chrome-style Open Tabs Counter Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onTabsClick)
                        .testTag("tabs_switcher_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(22.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                    ) {
                        Text(
                            text = tabsCount.coerceAtMost(99).toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Overflow Menu Button
                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("browser_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "قائمة المتصفح والإعدادات",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        // Navigation row inside menu (Back / Forward / Reload)
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    showOverflowMenu = false
                                    onBackClick()
                                },
                                enabled = canGoBack
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "رجوع"
                                )
                            }
                            IconButton(
                                onClick = {
                                    showOverflowMenu = false
                                    onForwardClick()
                                },
                                enabled = canGoForward
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "تقدم"
                                )
                            }
                            IconButton(
                                onClick = {
                                    showOverflowMenu = false
                                    onBookmarkToggle()
                                }
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "حفظ في المفضلة",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("تبويب جديد") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onNewTabClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("إضافة رابط تحميل مباشر") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenDirectDownloadDialog()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("مدير التحميلات المتقدم") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenSection(MainNavSection.DOWNLOADS)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("السجل والإشارات المرجعية") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenSection(MainNavSection.BOOKMARKS_HISTORY)
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("وضع سطح المكتب")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(
                                        checked = isDesktopMode,
                                        onCheckedChange = null
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onToggleDesktopMode()
                            }
                        )

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = { Text("الإعدادات ودليل Android Studio") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                onOpenSection(MainNavSection.SETTINGS_GUIDE)
                            }
                        )
                    }
                }
            }

            // Progress Bar while loading web pages
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0.05f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .testTag("webview_progress_bar")
                )
            }
        }
    }
}
