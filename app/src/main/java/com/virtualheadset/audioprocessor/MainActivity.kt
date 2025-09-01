package com.virtualheadset.audioprocessor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.virtualheadset.audioprocessor.audio.AudioProcessor
import com.virtualheadset.audioprocessor.service.VirtualAudioService
import com.virtualheadset.audioprocessor.ui.theme.VirtualHeadsetTheme
import com.virtualheadset.audioprocessor.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startVirtualAudioService()
        } else {
            Toast.makeText(this, "需要音频权限才能使用虚拟耳机功能", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VirtualHeadsetTheme {
                MainScreen()
            }
        }
        
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startVirtualAudioService()
        }
    }
    
    private fun startVirtualAudioService() {
        val intent = Intent(this, VirtualAudioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "虚拟耳机",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            item {
                StatusCard(
                    isProcessing = uiState.isProcessing,
                    onToggle = {
                        viewModel.toggleProcessing(context)
                    }
                )
            }
            
            // 音频效果选择
            item {
                EffectsCard(
                    selectedEffect = uiState.selectedEffect,
                    onEffectSelected = { effect ->
                        viewModel.selectEffect(context, effect)
                    }
                )
            }
            
            // 音频设备信息
            item {
                AudioDeviceCard(
                    inputDevice = uiState.currentInputDevice,
                    outputDevice = uiState.currentOutputDevice,
                    isVirtualDeviceActive = uiState.isVirtualDeviceActive
                )
            }
            
            // 高级设置
            item {
                AdvancedSettingsCard(
                    onOpenSettings = {
                        // 打开高级设置
                    }
                )
            }
        }
    }
}

@Composable
fun StatusCard(
    isProcessing: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isProcessing) Icons.Default.Headset else Icons.Default.HeadsetOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (isProcessing) MaterialTheme.colorScheme.primary else Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isProcessing) "虚拟耳机已激活" else "虚拟耳机未激活",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isProcessing) "正在处理音频流..." else "点击下方按钮开始",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isProcessing) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isProcessing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isProcessing) "停止处理" else "开始处理",
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun EffectsCard(
    selectedEffect: AudioProcessor.Effect,
    onEffectSelected: (AudioProcessor.Effect) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "音频效果",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val effects = listOf(
                AudioProcessor.Effect.NONE to "原始",
                AudioProcessor.Effect.ECHO to "回声",
                AudioProcessor.Effect.REVERB to "混响",
                AudioProcessor.Effect.PITCH_UP to "变调(高)",
                AudioProcessor.Effect.PITCH_DOWN to "变调(低)",
                AudioProcessor.Effect.ROBOT to "机器人",
                AudioProcessor.Effect.DISTORTION to "失真",
                AudioProcessor.Effect.CHORUS to "合唱",
                AudioProcessor.Effect.FLANGER to "镶边"
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(effects) { (effect, name) ->
                    FilterChip(
                        selected = selectedEffect == effect,
                        onClick = { onEffectSelected(effect) },
                        label = { Text(name) },
                        leadingIcon = if (selectedEffect == effect) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun AudioDeviceCard(
    inputDevice: String,
    outputDevice: String,
    isVirtualDeviceActive: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DevicesOther,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "音频设备",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isVirtualDeviceActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "虚拟音频设备已激活",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "输入设备",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = inputDevice,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "输出设备",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = outputDevice,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingsCard(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onOpenSettings
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "高级设置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "采样率、缓冲区、均衡器等",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}