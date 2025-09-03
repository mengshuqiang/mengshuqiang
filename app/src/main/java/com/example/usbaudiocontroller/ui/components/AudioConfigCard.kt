package com.example.usbaudiocontroller.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.usbaudiocontroller.viewmodel.AudioViewModel

/**
 * 音频配置卡片
 */
@Composable
fun AudioConfigCard(
    audioViewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val sampleRate by audioViewModel.sampleRate.collectAsState()
    val bitDepth by audioViewModel.bitDepth.collectAsState()
    val channelCount by audioViewModel.channelCount.collectAsState()
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "音频参数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 采样率选择
            ConfigSection(
                title = "采样率",
                currentValue = "$sampleRate Hz"
            ) {
                SampleRateSelector(
                    currentRate = sampleRate,
                    onRateSelected = { audioViewModel.setSampleRate(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 位深度选择
            ConfigSection(
                title = "位深度",
                currentValue = "$bitDepth bit"
            ) {
                BitDepthSelector(
                    currentDepth = bitDepth,
                    onDepthSelected = { audioViewModel.setBitDepth(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 声道选择
            ConfigSection(
                title = "声道",
                currentValue = if (channelCount == 2) "立体声" else "单声道"
            ) {
                ChannelSelector(
                    currentCount = channelCount,
                    onCountSelected = { audioViewModel.setChannelCount(it) }
                )
            }
        }
    }
}

/**
 * 配置部分
 */
@Composable
private fun ConfigSection(
    title: String,
    currentValue: String,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        content()
    }
}

/**
 * 采样率选择器
 */
@Composable
private fun SampleRateSelector(
    currentRate: Int,
    onRateSelected: (Int) -> Unit
) {
    val rates = listOf(44100, 48000, 88200, 96000, 192000)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        rates.forEach { rate ->
            FilterChip(
                selected = currentRate == rate,
                onClick = { onRateSelected(rate) },
                label = {
                    Text(
                        text = "${rate / 1000}k",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}

/**
 * 位深度选择器
 */
@Composable
private fun BitDepthSelector(
    currentDepth: Int,
    onDepthSelected: (Int) -> Unit
) {
    val depths = listOf(16, 24, 32)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        depths.forEach { depth ->
            FilterChip(
                selected = currentDepth == depth,
                onClick = { onDepthSelected(depth) },
                label = {
                    Text(
                        text = "$depth bit",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
        }
    }
}

/**
 * 声道选择器
 */
@Composable
private fun ChannelSelector(
    currentCount: Int,
    onCountSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FilterChip(
            selected = currentCount == 1,
            onClick = { onCountSelected(1) },
            label = {
                Text(
                    text = "单声道",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
        
        FilterChip(
            selected = currentCount == 2,
            onClick = { onCountSelected(2) },
            label = {
                Text(
                    text = "立体声",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
    }
}