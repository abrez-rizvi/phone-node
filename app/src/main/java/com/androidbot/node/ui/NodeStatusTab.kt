package com.androidbot.node.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NodeStatusTab(viewModel: DashboardViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val isRunning by viewModel.isServerRunning.collectAsState()
    val port by viewModel.serverPort.collectAsState()
    val localIp by viewModel.localIp.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val tunnelUrl by viewModel.tunnelUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var hideKey by remember { mutableStateOf(true) }

    var tunnelInput by remember(tunnelUrl) { mutableStateOf(tunnelUrl) }
    var isEditingTunnel by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp)
            .padding(bottom = 70.dp) // Leave clearance for floating bottom bar
    ) {
        // App Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Node Engine",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Serif
                )
                Text(
                    text = "Ktor Edge Server & Tunnel Gateway",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { viewModel.refreshIp() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Network", tint = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Status Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRunning) "ACTIVE / RUNNING" else "STOPPED",
                        color = if (isRunning) Color(0xFF4ADE80) else Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Port: $port",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Network Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (networkStatus.isCellular) Color(0xFF451A03) else Color(0xFF064E3B),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = networkStatus.networkType,
                        color = if (networkStatus.isCellular) Color(0xFFFDBA74) else Color(0xFF6EE7B7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Local IP / LAN URL
                val lanUrl = if (isRunning) "http://$localIp:$port" else "Offline"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAN URL: $lanUrl",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRunning) {
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(lanUrl)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy LAN", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                // Cellular Notice (if on 5G/4G CGNAT)
                if (networkStatus.isCellular) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF27272A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "⚡ Cellular Carrier CGNAT Active:\nDirect inbound ports are blocked by mobile carriers on 5G/4G. Use the Cloudflare Tunnel below for internet access.",
                            color = Color(0xFFFDE68A),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Cloudflare Public Tunnel Section
                if (isRunning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!isEditingTunnel) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloudflare Global Tunnel:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = tunnelUrl,
                                    color = Color(0xFF4ADE80),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(tunnelUrl)) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Tunnel", tint = Color(0xFF4ADE80))
                            }
                            IconButton(onClick = { isEditingTunnel = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Tunnel", tint = Color(0xFF94A3B8))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = tunnelInput,
                                onValueChange = { tunnelInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("https://....trycloudflare.com", fontSize = 11.sp) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    viewModel.updateTunnelUrl(tunnelInput)
                                    isEditingTunnel = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Save Tunnel", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Start / Stop Server Button
        Button(
            onClick = { viewModel.toggleServer() },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF0284C7)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRunning) "Stop Server" else "Start Server",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Groq API Key Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Groq Cloud API Key (LLM Inference)",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("gsk_...", color = Color(0xFF475569)) },
                        visualTransformation = if (hideKey) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            TextButton(onClick = { hideKey = !hideKey }) {
                                Text(if (hideKey) "Show" else "Hide", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.updateApiKey(keyInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27272A))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save Key", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Real-Time Console Log Stream
        Text(
            text = "Real-Time Node Console",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF09090B), RoundedCornerShape(10.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "System idle. Network & execution trace logs appear here.",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(logs) { logLine ->
                        val logColor = when {
                            logLine.contains("ERROR") -> Color(0xFFF87171)
                            logLine.contains("[AGENT]") -> Color(0xFF38BDF8)
                            logLine.contains("[SERVER]") -> Color(0xFF4ADE80)
                            logLine.contains("[STORAGE]") -> Color(0xFFFBBF24)
                            else -> Color(0xFFCBD5E1)
                        }
                        Text(
                            text = logLine,
                            color = logColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
