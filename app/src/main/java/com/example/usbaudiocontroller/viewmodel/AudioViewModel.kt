package com.example.usbaudiocontroller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.usbaudiocontroller.audio.AudioConfig
import com.example.usbaudiocontroller.audio.AudioProcessor
import com.example.usbaudiocontroller.manager.AudioDeviceInfo
import com.example.usbaudiocontroller.manager.USBConnectionState
import com.example.usbaudiocontroller.manager.USBManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 音频控制ViewModel
 * 管理UI状态和业务逻辑
 */
class AudioViewModel(application: Application) : AndroidViewModel(application) {
    
    // USB管理器
    private val usbManager = USBManager(application)
    
    // 音频处理器
    private val audioProcessor = AudioProcessor()
    
    // USB连接状态
    val connectionState: StateFlow<USBConnectionState> = usbManager.connectionState
    
    // 当前设备信息
    private val _deviceInfo = MutableStateFlow<AudioDeviceInfo?>(null)
    val deviceInfo: StateFlow<AudioDeviceInfo?> = _deviceInfo.asStateFlow()
    
    // 音频配置
    val audioConfig: StateFlow<AudioConfig> = audioProcessor.audioConfig
    
    // 录音状态
    val isRecording: StateFlow<Boolean> = audioProcessor.isRecording
    
    // 播放状态
    val isPlaying: StateFlow<Boolean> = audioProcessor.isPlaying
    
    // 音频电平
    val audioLevel: StateFlow<Float> = audioProcessor.audioLevel
    
    // 音频数据（用于可视化）
    val audioData: StateFlow<FloatArray> = audioProcessor.audioData
    
    // 音频效果状态
    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()
    
    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()
    
    private val _virtualizerEnabled = MutableStateFlow(false)
    val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()
    
    private val _reverbEnabled = MutableStateFlow(false)
    val reverbEnabled: StateFlow<Boolean> = _reverbEnabled.asStateFlow()
    
    // 音频参数
    private val _sampleRate = MutableStateFlow(48000)
    val sampleRate: StateFlow<Int> = _sampleRate.asStateFlow()
    
    private val _bitDepth = MutableStateFlow(16)
    val bitDepth: StateFlow<Int> = _bitDepth.asStateFlow()
    
    private val _channelCount = MutableStateFlow(2)
    val channelCount: StateFlow<Int> = _channelCount.asStateFlow()
    
    // 均衡器频段
    private val _equalizerBands = MutableStateFlow(listOf<EqualizerBand>())
    val equalizerBands: StateFlow<List<EqualizerBand>> = _equalizerBands.asStateFlow()
    
    // 效果强度
    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()
    
    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()
    
    private val _reverbPreset = MutableStateFlow(0)
    val reverbPreset: StateFlow<Int> = _reverbPreset.asStateFlow()
    
    init {
        // 监听USB设备连接状态
        viewModelScope.launch {
            usbManager.currentDevice.collect { device ->
                device?.let {
                    _deviceInfo.value = usbManager.getAudioDeviceInfo(it)
                    // 设备连接后初始化音频处理器
                    initializeAudioProcessor()
                } ?: run {
                    _deviceInfo.value = null
                }
            }
        }
        
        // 初始化均衡器频段
        initializeEqualizerBands()
    }
    
    /**
     * 初始化音频处理器
     */
    private fun initializeAudioProcessor() {
        val config = AudioConfig(
            sampleRate = _sampleRate.value,
            channelConfig = if (_channelCount.value == 2) {
                android.media.AudioFormat.CHANNEL_IN_STEREO
            } else {
                android.media.AudioFormat.CHANNEL_IN_MONO
            },
            audioFormat = when (_bitDepth.value) {
                16 -> android.media.AudioFormat.ENCODING_PCM_16BIT
                24 -> android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED
                32 -> android.media.AudioFormat.ENCODING_PCM_32BIT
                else -> android.media.AudioFormat.ENCODING_PCM_16BIT
            }
        )
        audioProcessor.initialize(config)
    }
    
    /**
     * 初始化均衡器频段
     */
    private fun initializeEqualizerBands() {
        // 创建5个频段的均衡器
        _equalizerBands.value = listOf(
            EqualizerBand(0, "60Hz", 0),
            EqualizerBand(1, "230Hz", 0),
            EqualizerBand(2, "910Hz", 0),
            EqualizerBand(3, "3.6kHz", 0),
            EqualizerBand(4, "14kHz", 0)
        )
    }
    
    /**
     * 扫描USB设备
     */
    fun scanForDevices() {
        usbManager.scanForAudioDevices()
    }
    
    /**
     * 开始/停止录音
     */
    fun toggleRecording() {
        if (isRecording.value) {
            audioProcessor.stopRecording()
        } else {
            audioProcessor.startRecording()
        }
    }
    
    /**
     * 开始/停止播放
     */
    fun togglePlayback() {
        if (isPlaying.value) {
            audioProcessor.stopPlayback()
        } else {
            audioProcessor.startPlayback()
        }
    }
    
    /**
     * 设置采样率
     */
    fun setSampleRate(rate: Int) {
        _sampleRate.value = rate
        updateAudioConfig()
    }
    
    /**
     * 设置位深度
     */
    fun setBitDepth(depth: Int) {
        _bitDepth.value = depth
        updateAudioConfig()
    }
    
    /**
     * 设置声道数
     */
    fun setChannelCount(count: Int) {
        _channelCount.value = count
        updateAudioConfig()
    }
    
    /**
     * 更新音频配置
     */
    private fun updateAudioConfig() {
        val config = AudioConfig(
            sampleRate = _sampleRate.value,
            channelConfig = if (_channelCount.value == 2) {
                android.media.AudioFormat.CHANNEL_IN_STEREO
            } else {
                android.media.AudioFormat.CHANNEL_IN_MONO
            },
            audioFormat = when (_bitDepth.value) {
                16 -> android.media.AudioFormat.ENCODING_PCM_16BIT
                24 -> android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED
                32 -> android.media.AudioFormat.ENCODING_PCM_32BIT
                else -> android.media.AudioFormat.ENCODING_PCM_16BIT
            }
        )
        audioProcessor.updateAudioConfig(config)
    }
    
    /**
     * 切换均衡器
     */
    fun toggleEqualizer() {
        _equalizerEnabled.value = !_equalizerEnabled.value
        audioProcessor.setEqualizerEnabled(_equalizerEnabled.value)
    }
    
    /**
     * 设置均衡器频段
     */
    fun setEqualizerBand(bandIndex: Int, level: Int) {
        val bands = _equalizerBands.value.toMutableList()
        if (bandIndex in bands.indices) {
            bands[bandIndex] = bands[bandIndex].copy(level = level)
            _equalizerBands.value = bands
            audioProcessor.setEqualizerBand(bandIndex.toShort(), level.toShort())
        }
    }
    
    /**
     * 切换低音增强
     */
    fun toggleBassBoost() {
        _bassBoostEnabled.value = !_bassBoostEnabled.value
        audioProcessor.setBassBoostEnabled(_bassBoostEnabled.value)
    }
    
    /**
     * 设置低音增强强度
     */
    fun setBassBoostStrength(strength: Int) {
        _bassBoostStrength.value = strength
        audioProcessor.setBassBoostStrength(strength.toShort())
    }
    
    /**
     * 切换虚拟环绕
     */
    fun toggleVirtualizer() {
        _virtualizerEnabled.value = !_virtualizerEnabled.value
        audioProcessor.setVirtualizerEnabled(_virtualizerEnabled.value)
    }
    
    /**
     * 设置虚拟环绕强度
     */
    fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        audioProcessor.setVirtualizerStrength(strength.toShort())
    }
    
    /**
     * 切换混响
     */
    fun toggleReverb() {
        _reverbEnabled.value = !_reverbEnabled.value
        audioProcessor.setReverbEnabled(_reverbEnabled.value)
    }
    
    /**
     * 设置混响预设
     */
    fun setReverbPreset(preset: Int) {
        _reverbPreset.value = preset
        audioProcessor.setReverbPreset(preset.toShort())
    }
    
    /**
     * 断开USB设备
     */
    fun disconnectDevice() {
        usbManager.disconnectDevice()
    }
    
    override fun onCleared() {
        super.onCleared()
        audioProcessor.release()
        usbManager.cleanup()
    }
}

/**
 * 均衡器频段
 */
data class EqualizerBand(
    val index: Int,
    val frequency: String,
    val level: Int
)