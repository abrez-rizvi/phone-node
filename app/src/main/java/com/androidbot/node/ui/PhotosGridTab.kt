package com.androidbot.node.ui

import android.graphics.Bitmap
import com.androidbot.node.ui.util.formatFileSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidbot.node.storage.FileItem
import com.androidbot.node.ui.util.ThumbnailManager
import java.io.File

@Composable
fun PhotosGridTab(
    viewModel: DashboardViewModel,
    onNavigateToChatWithPrompt: (String) -> Unit
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val usage by viewModel.storageUsage.collectAsState()
    val filter by viewModel.currentFilter.collectAsState()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val previewText by viewModel.previewText.collectAsState()
    val previewBitmap by viewModel.previewBitmap.collectAsState()

    var showMenu by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importFile(uri, context.contentResolver)
        }
    }

    val displayFiles = remember(files, filter) {
        when (filter) {
            AlbumFilter.ALL -> files
            AlbumFilter.VIDEOS -> files.filter { it.isVideo }
            AlbumFilter.IMAGES -> files.filter { it.isImage }
            AlbumFilter.DOCUMENTS -> files.filter { it.isText && !it.name.contains("brief") && !it.name.contains("summary") }
            AlbumFilter.AGENT_OUTPUTS -> files.filter { it.name.contains("brief") || it.name.contains("summary") }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Photos Style Header (matches Reference Screenshot 1)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Photos",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color(0xFFD4D4D8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Media") },
                                onClick = {
                                    showMenu = false
                                    filePickerLauncher.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh Vault") },
                                onClick = {
                                    showMenu = false
                                    viewModel.refreshFiles()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle Section Header (matches Screenshot 1: "This month" on left, Location/Vault on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (filter == AlbumFilter.ALL) "This month" else filter.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${displayFiles.size} items • ${formatFileSize(usage.totalNodeBytes)}",
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dense Media Grid (matches Reference Screenshot 1)
        if (displayFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media files found.\nTap '+' to import photos or videos.",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(displayFiles, key = { it.relativePath }) { file ->
                    PhotoThumbnailItem(
                        file = file,
                        resolvedFile = viewModel.getResolvedFile(file.relativePath),
                        onClick = { viewModel.previewFile(file) }
                    )
                }
            }
        }
    }

    // Full Screen Media Viewer
    selectedFile?.let { activeFile ->
        FullScreenMediaViewer(
            file = activeFile,
            resolvedFile = viewModel.getResolvedFile(activeFile.relativePath),
            textPreview = previewText,
            bitmapPreview = previewBitmap,
            onDismiss = { viewModel.closePreview() },
            onDelete = {
                viewModel.deleteFile(it)
                viewModel.closePreview()
            },
            onSendToChat = { prompt ->
                onNavigateToChatWithPrompt(prompt)
            }
        )
    }
}

@Composable
fun PhotoThumbnailItem(
    file: FileItem,
    resolvedFile: File,
    onClick: () -> Unit
) {
    var thumbnail by remember(file.relativePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.relativePath) {
        if (file.isImage || file.isVideo) {
            thumbnail = ThumbnailManager.getThumbnail(resolvedFile, file.isVideo)
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF141416))
            .clickable { onClick() }
    ) {
        when {
            thumbnail != null -> {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Video play badge in bottom-left corner (matches Screenshot 1)
                if (file.isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0x99000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            file.isVideo -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            file.isText -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF18181B))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = if (file.name.contains("brief") || file.name.contains("summary")) Color(0xFFA855F7) else Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = file.name,
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.sp,
                        maxLines = 2,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF18181B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = resolvedFile.extension.uppercase(),
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
