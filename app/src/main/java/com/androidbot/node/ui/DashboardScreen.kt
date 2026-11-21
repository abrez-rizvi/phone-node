package com.androidbot.node.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var chatPromptPrefill by remember { mutableStateOf<String?>(null) }

    // Detect software keyboard visibility to prevent floating pill from obstructing chat input
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isKeyboardVisible = imeBottom > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            when (selectedTab) {
                0 -> PhotosGridTab(
                    viewModel = viewModel,
                    onNavigateToChatWithPrompt = { prompt ->
                        chatPromptPrefill = prompt
                        selectedTab = 2
                    }
                )
                1 -> AlbumsTab(
                    viewModel = viewModel,
                    onSelectAlbum = {
                        selectedTab = 0
                    }
                )
                2 -> AgentChatTab(
                    viewModel = viewModel,
                    initialPrompt = chatPromptPrefill
                )
                3 -> NodeStatusTab(viewModel = viewModel)
            }
        }

        // Floating Pill Navigation Bar (matches Reference Screenshots 1 & 2)
        AnimatedVisibility(
            visible = !isKeyboardVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            FloatingPillNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    if (index != 2) chatPromptPrefill = null
                    selectedTab = index
                }
            )
        }
    }
}

@Composable
fun FloatingPillNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(24.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xEE161618))
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingPillItem(
                icon = Icons.Default.PhotoLibrary,
                contentDescription = "Photos",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )
            FloatingPillItem(
                icon = Icons.Default.FolderCopy,
                contentDescription = "Albums",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )
            FloatingPillItem(
                icon = Icons.Default.SmartToy,
                contentDescription = "Agent Chat",
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) }
            )
            FloatingPillItem(
                icon = Icons.Default.Dns,
                contentDescription = "Node Engine",
                isSelected = selectedTab == 3,
                onClick = { onTabSelected(3) }
            )
        }
    }
}

@Composable
fun FloatingPillItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF2C2C32) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) Color.White else Color(0xFF8E8E93),
            modifier = Modifier.size(22.dp)
        )
    }
}
