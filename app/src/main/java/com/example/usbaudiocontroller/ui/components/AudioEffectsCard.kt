package com.example.usbaudiocontroller.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.usbaudiocontroller.viewmodel.AudioViewModel

/**
 * 音频效果卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsCard(
    audioViewModel: AudioViewModel,
    modifier: Modifier = Modifier
) {
    val equalizerEnabled by audioViewModel.equalizerEnabled.collectAsState()
    val bassBoostEnabled by audioViewModel.bassBoostEnabled.collectAsState()
    val virtualizerEnabled by audioViewModel.virtualizerEnabled.collectAsState()
    val reverbEnabled by audioViewModel.reverbEnabled.collectAsState()
    
    val equalizerBands by audioViewModel.equalizerBands.collectAsState()
    val bassBoostStrength by audioViewModel.bassBoostStrength.collectAsState()
    val virtualizerStrength by audioViewModel.virtualizerStrength.collectAsState()
    val reverbPreset by audioViewModel.reverbPreset.collectAsState()
    
    var expandedEffect by remember { mutableStateOf<String?>(null) }
    
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
                text = "音频效果",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 均衡器
            EffectItem(
                title = "均衡器",
                icon = Icons.Default.Settings,
                enabled = equalizerEnabled,
                expanded = expandedEffect == "equalizer",
                onToggle = { audioViewModel.toggleEqualizer() },
                onExpandToggle = {
                    expandedEffect = if (expandedEffect == "equalizer") null else "equalizer"
                }
            ) {
                EqualizerControls(
                    bands = equalizerBands,
                    onBandChange = { index, level ->
                        audioViewModel.setEqualizerBand(index, level)
                    }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 低音增强
            EffectItem(
                title = "低音增强",
                icon = Icons.Default.GraphicEq,
                enabled = bassBoostEnabled,
                expanded = expandedEffect == "bass",
                onToggle = { audioViewModel.toggleBassBoost() },
                onExpandToggle = {
                    expandedEffect = if (expandedEffect == "bass") null else "bass"
                }
            ) {
                StrengthSlider(
                    value = bassBoostStrength,
                    onValueChange = { audioViewModel.setBassBoostStrength(it) },
                    label = "强度"
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 虚拟环绕
            EffectItem(
                title = "虚拟环绕",
                icon = Icons.Default.Surround,
                enabled = virtualizerEnabled,
                expanded = expandedEffect == "virtualizer",
                onToggle = { audioViewModel.toggleVirtualizer() },
                onExpandToggle = {
                    expandedEffect = if (expandedEffect == "virtualizer") null else "virtualizer"
                }
            ) {
                StrengthSlider(
                    value = virtualizerStrength,
                    onValueChange = { audioViewModel.setVirtualizerStrength(it) },
                    label = "强度"
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // 混响
            EffectItem(
                title = "混响",
                icon = Icons.Default.Waves,
                enabled = reverbEnabled,
                expanded = expandedEffect == "reverb",
                onToggle = { audioViewModel.toggleReverb() },
                onExpandToggle = {
                    expandedEffect = if (expandedEffect == "reverb") null else "reverb"
                }
            ) {
                ReverbPresetSelector(
                    currentPreset = reverbPreset,
                    onPresetSelected = { audioViewModel.setReverbPreset(it) }
                )
            }
        }
    }
}

/**
 * 效果项
 */
@Composable
private fun EffectItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onExpandToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Row {
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() }
                )
                
                IconButton(onClick = onExpandToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }
        }
        
        if (expanded && enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * 均衡器控制
 */
@Composable
private fun EqualizerControls(
    bands: List<com.example.usbaudiocontroller.viewmodel.EqualizerBand>,
    onBandChange: (Int, Int) -> Unit
) {
    Column {
        bands.forEach { band ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = band.frequency,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(60.dp)
                )
                
                Slider(
                    value = band.level.toFloat(),
                    onValueChange = { onBandChange(band.index, it.toInt()) },
                    valueRange = -1500f..1500f,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${band.level / 100}dB",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(50.dp)
                )
            }
        }
    }
}

/**
 * 强度滑块
 */
@Composable
private fun StrengthSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(60.dp)
        )
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..1000f,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = "${value / 10}%",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(50.dp)
        )
    }
}

/**
 * 混响预设选择器
 */
@Composable
private fun ReverbPresetSelector(
    currentPreset: Int,
    onPresetSelected: (Int) -> Unit
) {
    val presets = listOf(
        0 to "无",
        1 to "小房间",
        2 to "中房间",
        3 to "大房间",
        4 to "中厅",
        5 to "大厅",
        6 to "板式"
    )
    
    Column {
        presets.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (value, label) ->
                    FilterChip(
                        selected = currentPreset == value,
                        onClick = { onPresetSelected(value) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}