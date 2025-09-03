package com.example.usbaudiocontroller.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.usbaudiocontroller.viewmodel.AudioViewModel
import kotlin.math.*

/**
 * 音频可视化卡片
 */
@Composable
fun AudioVisualizerCard(
    audioViewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val audioLevel by audioViewModel.audioLevel.collectAsState()
    val audioData by audioViewModel.audioData.collectAsState()
    val isRecording by audioViewModel.isRecording.collectAsState()
    val isPlaying by audioViewModel.isPlaying.collectAsState()
    
    var visualizationType by remember { mutableStateOf(VisualizationType.WAVEFORM) }
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和选择器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "音频监控",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // 可视化类型选择
                Row {
                    VisualizationType.values().forEach { type ->
                        FilterChip(
                            selected = visualizationType == type,
                            onClick = { visualizationType = type },
                            label = {
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // VU表
            VUMeter(
                level = audioLevel,
                isActive = isRecording || isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 可视化区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                when (visualizationType) {
                    VisualizationType.WAVEFORM -> {
                        WaveformVisualizer(
                            audioData = audioData,
                            isActive = isRecording || isPlaying,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    VisualizationType.SPECTRUM -> {
                        SpectrumVisualizer(
                            audioData = audioData,
                            isActive = isRecording || isPlaying,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    VisualizationType.BARS -> {
                        BarsVisualizer(
                            audioData = audioData,
                            isActive = isRecording || isPlaying,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 音频信息
            AudioInfoPanel(
                audioLevel = audioLevel,
                isRecording = isRecording,
                isPlaying = isPlaying
            )
        }
    }
}

/**
 * VU表
 */
@Composable
private fun VUMeter(
    level: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) level else 0f,
        animationSpec = tween(durationMillis = 100)
    )
    
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVUMeter(animatedLevel)
        }
    }
}

/**
 * 绘制VU表
 */
private fun DrawScope.drawVUMeter(level: Float) {
    val width = size.width
    val height = size.height
    val barHeight = height * 0.6f
    val barY = (height - barHeight) / 2
    
    // 背景
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.1f),
        topLeft = Offset(0f, barY),
        size = Size(width, barHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    
    // 电平条
    val fillWidth = width * level
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF4CAF50),
            Color(0xFF8BC34A),
            Color(0xFFFFEB3B),
            Color(0xFFFFC107),
            Color(0xFFFF9800),
            Color(0xFFF44336)
        ),
        startX = 0f,
        endX = width
    )
    
    drawRoundRect(
        brush = gradient,
        topLeft = Offset(0f, barY),
        size = Size(fillWidth, barHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    )
    
    // 刻度线
    for (i in 1..10) {
        val x = width * (i / 10f)
        val lineHeight = if (i % 5 == 0) barHeight else barHeight * 0.5f
        val lineY = barY + (barHeight - lineHeight) / 2
        
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(x, lineY),
            end = Offset(x, lineY + lineHeight),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * 波形可视化
 */
@Composable
private fun WaveformVisualizer(
    audioData: FloatArray,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val waveColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier) {
        if (audioData.isNotEmpty() && isActive) {
            drawWaveform(audioData, waveColor)
        } else {
            // 绘制静态中线
            drawLine(
                color = waveColor.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

/**
 * 绘制波形
 */
private fun DrawScope.drawWaveform(audioData: FloatArray, color: Color) {
    val width = size.width
    val height = size.height
    val centerY = height / 2
    
    val path = Path()
    val stepX = width / audioData.size.toFloat()
    
    audioData.forEachIndexed { index, value ->
        val x = index * stepX
        val y = centerY + (value * height / 2)
        
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )
}

/**
 * 频谱可视化
 */
@Composable
private fun SpectrumVisualizer(
    audioData: FloatArray,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val spectrumColor = MaterialTheme.colorScheme.secondary
    
    Canvas(modifier = modifier) {
        if (audioData.isNotEmpty() && isActive) {
            drawSpectrum(audioData, spectrumColor)
        }
    }
}

/**
 * 绘制频谱
 */
private fun DrawScope.drawSpectrum(audioData: FloatArray, color: Color) {
    val fftSize = 32 // 简化的FFT大小
    val barCount = min(fftSize, audioData.size)
    val barWidth = size.width / barCount
    val barSpacing = barWidth * 0.1f
    
    for (i in 0 until barCount) {
        val magnitude = abs(audioData[i])
        val barHeight = magnitude * size.height
        val x = i * barWidth + barSpacing
        val y = size.height - barHeight
        
        drawRect(
            color = color.copy(alpha = 0.8f),
            topLeft = Offset(x, y),
            size = Size(barWidth - barSpacing * 2, barHeight)
        )
    }
}

/**
 * 条形可视化
 */
@Composable
private fun BarsVisualizer(
    audioData: FloatArray,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val barColors = listOf(
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFFFF9800),
        Color(0xFF9C27B0),
        Color(0xFFF44336)
    )
    
    Canvas(modifier = modifier) {
        if (audioData.isNotEmpty() && isActive) {
            drawBars(audioData, barColors)
        }
    }
}

/**
 * 绘制条形
 */
private fun DrawScope.drawBars(audioData: FloatArray, colors: List<Color>) {
    val barCount = 5
    val barWidth = size.width / barCount
    val barSpacing = barWidth * 0.15f
    
    for (i in 0 until barCount) {
        val dataIndex = (i * audioData.size / barCount).coerceIn(0, audioData.size - 1)
        val magnitude = abs(audioData[dataIndex])
        val barHeight = magnitude * size.height
        val x = i * barWidth + barSpacing
        val y = size.height - barHeight
        
        // 绘制条形
        drawRoundRect(
            color = colors[i % colors.size],
            topLeft = Offset(x, y),
            size = Size(barWidth - barSpacing * 2, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
        
        // 绘制顶部高亮
        drawRoundRect(
            color = Color.White.copy(alpha = 0.3f),
            topLeft = Offset(x, y),
            size = Size(barWidth - barSpacing * 2, 4.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
    }
}

/**
 * 音频信息面板
 */
@Composable
private fun AudioInfoPanel(
    audioLevel: Float,
    isRecording: Boolean,
    isPlaying: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        InfoItem(
            label = "电平",
            value = "${(audioLevel * 100).toInt()}%"
        )
        
        InfoItem(
            label = "状态",
            value = when {
                isRecording -> "录音中"
                isPlaying -> "播放中"
                else -> "待机"
            }
        )
        
        InfoItem(
            label = "峰值",
            value = "${(-60 + audioLevel * 60).toInt()} dB"
        )
    }
}

/**
 * 信息项
 */
@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 可视化类型
 */
private enum class VisualizationType(val label: String) {
    WAVEFORM("波形"),
    SPECTRUM("频谱"),
    BARS("条形")
}