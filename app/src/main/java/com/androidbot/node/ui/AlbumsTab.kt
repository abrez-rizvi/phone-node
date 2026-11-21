package com.androidbot.node.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androidbot.node.storage.FileItem
import com.androidbot.node.ui.util.ThumbnailManager
import java.text.NumberFormat
import java.util.Locale

data class AlbumCardData(
    val title: String,
    val count: Int,
    val filter: AlbumFilter,
    val representativeFile: FileItem?,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

@Composable
fun AlbumsTab(
    viewModel: DashboardViewModel,
    onSelectAlbum: (AlbumFilter) -> Unit
) {
    val files by viewModel.files.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    val videoFiles = files.filter { it.isVideo }
    val imageFiles = files.filter { it.isImage }
    val docFiles = files.filter { it.isText && !it.name.contains("brief") && !it.name.contains("summary") }
    val agentFiles = files.filter { it.name.contains("brief") || it.name.contains("summary") }

    val albums = listOf(
        AlbumCardData(
            title = "Camera",
            count = files.size,
            filter = AlbumFilter.ALL,
            representativeFile = files.firstOrNull { it.isImage } ?: files.firstOrNull(),
            icon = Icons.Default.PhotoCamera,
            gradientColors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
        ),
        AlbumCardData(
            title = "Favourite",
            count = if (files.isNotEmpty()) 2 else 0,
            filter = AlbumFilter.ALL,
            representativeFile = imageFiles.getOrNull(1) ?: imageFiles.firstOrNull(),
            icon = Icons.Default.Favorite,
            gradientColors = listOf(Color(0xFF3B0764), Color(0xFF1E1B4B))
        ),
        AlbumCardData(
            title = "Video",
            count = videoFiles.size,
            filter = AlbumFilter.VIDEOS,
            representativeFile = videoFiles.firstOrNull(),
            icon = Icons.Default.Movie,
            gradientColors = listOf(Color(0xFF075985), Color(0xFF0C4A6E))
        ),
        AlbumCardData(
            title = "Screenshots",
            count = imageFiles.count { it.name.contains("screenshot", ignoreCase = true) }.takeIf { it > 0 } ?: imageFiles.size,
            filter = AlbumFilter.IMAGES,
            representativeFile = imageFiles.firstOrNull { it.name.contains("screenshot", ignoreCase = true) } ?: imageFiles.firstOrNull(),
            icon = Icons.Default.Folder,
            gradientColors = listOf(Color(0xFF14532D), Color(0xFF064E3B))
        ),
        AlbumCardData(
            title = "Documents",
            count = docFiles.size,
            filter = AlbumFilter.DOCUMENTS,
            representativeFile = docFiles.firstOrNull(),
            icon = Icons.Default.Description,
            gradientColors = listOf(Color(0xFF78350F), Color(0xFF451A03))
        ),
        AlbumCardData(
            title = "Agent Briefs",
            count = agentFiles.size,
            filter = AlbumFilter.AGENT_OUTPUTS,
            representativeFile = agentFiles.firstOrNull(),
            icon = Icons.Default.AutoAwesome,
            gradientColors = listOf(Color(0xFF4C1D95), Color(0xFF2E1065))
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Albums Header (matches Reference Screenshot 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Albums",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Serif
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color(0xFFD4D4D8)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Refresh Library") },
                        onClick = {
                            viewModel.refreshFiles()
                            showMenu = false
                        }
                    )
                }
            }
        }

        // 2-Column Grid of Albums
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums, key = { it.title }) { album ->
                AlbumGridCard(
                    album = album,
                    viewModel = viewModel,
                    onClick = {
                        viewModel.setFilter(album.filter)
                        onSelectAlbum(album.filter)
                    }
                )
            }
        }
    }
}

@Composable
fun AlbumGridCard(
    album: AlbumCardData,
    viewModel: DashboardViewModel,
    onClick: () -> Unit
) {
    var thumbnail by remember(album.representativeFile?.relativePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(album.representativeFile?.relativePath) {
        val file = album.representativeFile
        if (file != null && (file.isImage || file.isVideo)) {
            val resolved = viewModel.getResolvedFile(file.relativePath)
            thumbnail = ThumbnailManager.getThumbnail(resolved, file.isVideo)
        }
    }

    val numberFormatter = remember { NumberFormat.getNumberInstance(Locale.US) }
    val formattedCount = remember(album.count) { numberFormatter.format(album.count) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // Rounded Square Album Cover Card (matches Reference Screenshot 2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(album.gradientColors))
                .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = album.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = album.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title and item count directly below cover (matches Screenshot 2)
        Text(
            text = album.title,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp
        )
        Text(
            text = formattedCount,
            color = Color(0xFF8E8E93),
            fontSize = 13.sp
        )
    }
}
