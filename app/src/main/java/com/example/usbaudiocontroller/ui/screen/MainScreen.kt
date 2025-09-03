package com.example.usbaudiocontroller.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.usbaudiocontroller.manager.USBConnectionState
import com.example.usbaudiocontroller.ui.components.*
import com.example.usbaudiocontroller.viewmodel.AudioViewModel

/**
 * 主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(audioViewModel: AudioViewModel) {
    val connectionState by audioViewModel.connectionState.collectAsState()
    val deviceInfo by audioViewModel.deviceInfo.collectAsState()
    val isRecording by audioViewModel.isRecording.collectAsState()
    val isPlaying by audioViewModel.isPlaying.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "USB Audio Controller",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { audioViewModel.scanForDevices() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "扫描设备")
                    }
                    
                    if (connectionState == USBConnectionState.CONNECTED) {
                        IconButton(onClick = { audioViewModel.disconnectDevice() }) {
                            Icon(Icons.Default.Close, contentDescription = "断开连接")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 连接状态卡片
            ConnectionStatusCard(
                connectionState = connectionState,
                deviceInfo = deviceInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            
            // 只有在设备连接时显示控制界面
            if (connectionState == USBConnectionState.CONNECTED) {
                // 音频控制卡片
                AudioControlCard(
                    isRecording = isRecording,
                    isPlaying = isPlaying,
                    onRecordClick = { audioViewModel.toggleRecording() },
                    onPlayClick = { audioViewModel.togglePlayback() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // 音频参数配置卡片
                AudioConfigCard(
                    audioViewModel = audioViewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // 音频效果卡片
                AudioEffectsCard(
                    audioViewModel = audioViewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                // 音频可视化卡片
                AudioVisualizerCard(
                    audioViewModel = audioViewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}