package com.example.usbaudiocontroller.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.usbaudiocontroller.manager.AudioDeviceInfo
import com.example.usbaudiocontroller.manager.USBConnectionState

/**
 * 连接状态卡片
 */
@Composable
fun ConnectionStatusCard(
    connectionState: USBConnectionState,
    deviceInfo: AudioDeviceInfo?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和状态图标
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "USB设备状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                ConnectionStateIcon(connectionState)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 连接状态文本
            ConnectionStateText(connectionState)
            
            // 设备信息（如果已连接）
            if (connectionState == USBConnectionState.CONNECTED && deviceInfo != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                
                DeviceInfoSection(deviceInfo)
            }
        }
    }
}

/**
 * 连接状态图标
 */
@Composable
private fun ConnectionStateIcon(state: USBConnectionState) {
    val (icon, color) = when (state) {
        USBConnectionState.CONNECTED -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        USBConnectionState.CONNECTING -> Icons.Default.Refresh to Color(0xFFFFC107)
        USBConnectionState.DISCONNECTED -> Icons.Default.Warning to Color(0xFFFF9800)
        USBConnectionState.NO_DEVICE -> Icons.Default.Info to Color(0xFF9E9E9E)
        USBConnectionState.REQUESTING_PERMISSION -> Icons.Default.Lock to Color(0xFF2196F3)
        USBConnectionState.PERMISSION_DENIED -> Icons.Default.Close to Color(0xFFF44336)
        USBConnectionState.CONNECTION_FAILED -> Icons.Default.Close to Color(0xFFF44336)
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(24.dp)
    )
}

/**
 * 连接状态文本
 */
@Composable
private fun ConnectionStateText(state: USBConnectionState) {
    val (text, color) = when (state) {
        USBConnectionState.CONNECTED -> "设备已连接" to Color(0xFF4CAF50)
        USBConnectionState.CONNECTING -> "正在连接设备..." to Color(0xFFFFC107)
        USBConnectionState.DISCONNECTED -> "设备未连接" to Color(0xFFFF9800)
        USBConnectionState.NO_DEVICE -> "未检测到USB音频设备" to Color(0xFF9E9E9E)
        USBConnectionState.REQUESTING_PERMISSION -> "正在请求USB权限..." to Color(0xFF2196F3)
        USBConnectionState.PERMISSION_DENIED -> "USB权限被拒绝" to Color(0xFFF44336)
        USBConnectionState.CONNECTION_FAILED -> "连接失败" to Color(0xFFF44336)
    }
    
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge
    )
}

/**
 * 设备信息部分
 */
@Composable
private fun DeviceInfoSection(deviceInfo: AudioDeviceInfo) {
    Column {
        Text(
            text = "设备信息",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        InfoRow("制造商", deviceInfo.manufacturer)
        InfoRow("产品", deviceInfo.product)
        InfoRow("设备名称", deviceInfo.name)
        InfoRow("序列号", deviceInfo.serialNumber)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "音频能力",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CapabilityChip(
                label = "音频控制",
                enabled = deviceInfo.hasAudioControl
            )
            CapabilityChip(
                label = "音频流",
                enabled = deviceInfo.hasAudioStreaming
            )
            CapabilityChip(
                label = "MIDI",
                enabled = deviceInfo.hasMidiStreaming
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoChip(
                label = "输入通道",
                value = deviceInfo.inputChannels.toString()
            )
            InfoChip(
                label = "输出通道",
                value = deviceInfo.outputChannels.toString()
            )
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 能力芯片
 */
@Composable
private fun CapabilityChip(label: String, enabled: Boolean) {
    AssistChip(
        onClick = { },
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = if (enabled) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (enabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            labelColor = if (enabled) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    )
}

/**
 * 信息芯片
 */
@Composable
private fun InfoChip(label: String, value: String) {
    AssistChip(
        onClick = { },
        label = { Text("$label: $value") }
    )
}