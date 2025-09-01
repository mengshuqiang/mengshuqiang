package com.virtualheadset.audioprocessor.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.virtualheadset.audioprocessor.audio.AudioProcessor
import com.virtualheadset.audioprocessor.audio.AudioRoutingManager
import com.virtualheadset.audioprocessor.service.VirtualAudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private var audioRoutingManager: AudioRoutingManager? = null
    
    data class UiState(
        val isProcessing: Boolean = false,
        val selectedEffect: AudioProcessor.Effect = AudioProcessor.Effect.NONE,
        val currentInputDevice: String = "默认麦克风",
        val currentOutputDevice: String = "默认扬声器",
        val isVirtualDeviceActive: Boolean = false,
        val volumeLevel: Float = 1.0f,
        val isNoiseReductionEnabled: Boolean = false,
        val isEchoCalcellationEnabled: Boolean = false
    )
    
    fun initializeAudioRouting(context: Context) {
        audioRoutingManager = AudioRoutingManager(context)
        
        viewModelScope.launch {
            audioRoutingManager?.audioDeviceState?.collect { deviceState ->
                _uiState.value = _uiState.value.copy(
                    currentInputDevice = deviceState.currentInputDevice,
                    currentOutputDevice = deviceState.currentOutputDevice,
                    isVirtualDeviceActive = deviceState.isVirtualDeviceActive
                )
            }
        }
    }
    
    fun toggleProcessing(context: Context) {
        val newState = !_uiState.value.isProcessing
        _uiState.value = _uiState.value.copy(isProcessing = newState)
        
        val intent = Intent(context, VirtualAudioService::class.java)
        if (newState) {
            // 开始处理
            intent.action = VirtualAudioService.ACTION_START_PROCESSING
            context.startService(intent)
            
            // 激活虚拟音频设备
            audioRoutingManager?.createVirtualAudioDevice()
            audioRoutingManager?.routeAudioToVirtualDevice()
        } else {
            // 停止处理
            intent.action = VirtualAudioService.ACTION_STOP_PROCESSING
            context.startService(intent)
            
            // 恢复默认音频路由
            audioRoutingManager?.restoreDefaultRouting()
        }
    }
    
    fun selectEffect(context: Context, effect: AudioProcessor.Effect) {
        _uiState.value = _uiState.value.copy(selectedEffect = effect)
        
        // 发送效果更改到服务
        val intent = Intent(context, VirtualAudioService::class.java).apply {
            action = VirtualAudioService.ACTION_APPLY_EFFECT
            putExtra(VirtualAudioService.EXTRA_EFFECT_TYPE, effect.name)
        }
        context.startService(intent)
    }
    
    fun setVolumeLevel(level: Float) {
        _uiState.value = _uiState.value.copy(volumeLevel = level.coerceIn(0f, 1f))
    }
    
    fun toggleNoiseReduction() {
        _uiState.value = _uiState.value.copy(
            isNoiseReductionEnabled = !_uiState.value.isNoiseReductionEnabled
        )
    }
    
    fun toggleEchoCancellation() {
        _uiState.value = _uiState.value.copy(
            isEchoCalcellationEnabled = !_uiState.value.isEchoCalcellationEnabled
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        audioRoutingManager?.abandonAudioFocus()
    }
}