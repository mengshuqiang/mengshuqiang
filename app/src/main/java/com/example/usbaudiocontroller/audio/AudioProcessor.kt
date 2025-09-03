package com.example.usbaudiocontroller.audio

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * 音频处理器
 * 负责音频的录制、播放、路由和效果处理
 */
class AudioProcessor {
    
    companion object {
        private const val TAG = "AudioProcessor"
        
        // 音频参数默认值
        const val DEFAULT_SAMPLE_RATE = 48000
        const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val DEFAULT_BUFFER_SIZE_MULTIPLIER = 2
    }
    
    // 音频配置
    private val _audioConfig = MutableStateFlow(AudioConfig())
    val audioConfig: StateFlow<AudioConfig> = _audioConfig.asStateFlow()
    
    // 音频状态
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    // 音频电平
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    // 音频数据流
    private val _audioData = MutableStateFlow(FloatArray(0))
    val audioData: StateFlow<FloatArray> = _audioData.asStateFlow()
    
    // 音频组件
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null
    
    // 协程作用域
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    
    /**
     * 初始化音频系统
     */
    fun initialize(config: AudioConfig = AudioConfig()) {
        _audioConfig.value = config
        setupAudioComponents(config)
    }
    
    /**
     * 设置音频组件
     */
    private fun setupAudioComponents(config: AudioConfig) {
        try {
            // 计算缓冲区大小
            val minBufferSize = AudioRecord.getMinBufferSize(
                config.sampleRate,
                config.channelConfig,
                config.audioFormat
            )
            
            val bufferSize = minBufferSize * DEFAULT_BUFFER_SIZE_MULTIPLIER
            
            // 初始化AudioRecord（录音）
            if (config.enableInput) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    config.sampleRate,
                    config.channelConfig,
                    config.audioFormat,
                    bufferSize
                ).apply {
                    if (state != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord初始化失败")
                        release()
                        audioRecord = null
                    }
                }
            }
            
            // 初始化AudioTrack（播放）
            if (config.enableOutput) {
                val outputChannelConfig = when (config.channelConfig) {
                    AudioFormat.CHANNEL_IN_MONO -> AudioFormat.CHANNEL_OUT_MONO
                    AudioFormat.CHANNEL_IN_STEREO -> AudioFormat.CHANNEL_OUT_STEREO
                    else -> AudioFormat.CHANNEL_OUT_STEREO
                }
                
                audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(config.sampleRate)
                        .setChannelMask(outputChannelConfig)
                        .setEncoding(config.audioFormat)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                ).apply {
                    if (state != AudioTrack.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioTrack初始化失败")
                        release()
                        audioTrack = null
                    } else {
                        // 初始化音频效果
                        initializeAudioEffects(audioSessionId)
                    }
                }
            }
            
            Log.d(TAG, "音频组件初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "音频组件初始化失败", e)
        }
    }
    
    /**
     * 初始化音频效果
     */
    private fun initializeAudioEffects(sessionId: Int) {
        try {
            // 均衡器
            equalizer = Equalizer(0, sessionId).apply {
                enabled = false
            }
            
            // 低音增强
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = false
            }
            
            // 虚拟环绕
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = false
            }
            
            // 混响效果
            presetReverb = PresetReverb(0, sessionId).apply {
                enabled = false
            }
            
            Log.d(TAG, "音频效果初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "音频效果初始化失败", e)
        }
    }
    
    /**
     * 开始录音
     */
    fun startRecording() {
        if (_isRecording.value) {
            Log.w(TAG, "已经在录音中")
            return
        }
        
        audioRecord?.let { recorder ->
            if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording()
                _isRecording.value = true
                
                recordingJob = audioScope.launch {
                    processAudioInput(recorder)
                }
                
                Log.d(TAG, "开始录音")
            } else {
                Log.e(TAG, "AudioRecord未初始化")
            }
        } ?: Log.e(TAG, "AudioRecord为空")
    }
    
    /**
     * 停止录音
     */
    fun stopRecording() {
        if (!_isRecording.value) {
            return
        }
        
        recordingJob?.cancel()
        recordingJob = null
        
        audioRecord?.stop()
        _isRecording.value = false
        
        Log.d(TAG, "停止录音")
    }
    
    /**
     * 处理音频输入
     */
    private suspend fun processAudioInput(recorder: AudioRecord) {
        val bufferSize = 1024
        val buffer = ShortArray(bufferSize)
        
        while (_isRecording.value && isActive) {
            val readSize = recorder.read(buffer, 0, bufferSize)
            
            if (readSize > 0) {
                // 计算音频电平
                calculateAudioLevel(buffer, readSize)
                
                // 转换为浮点数组用于可视化
                val floatBuffer = FloatArray(readSize)
                for (i in 0 until readSize) {
                    floatBuffer[i] = buffer[i] / 32768.0f
                }
                _audioData.value = floatBuffer
                
                // 如果启用了实时播放，将数据写入AudioTrack
                if (_isPlaying.value) {
                    audioTrack?.write(buffer, 0, readSize)
                }
            }
            
            delay(10) // 小延迟避免CPU过载
        }
    }
    
    /**
     * 计算音频电平
     */
    private fun calculateAudioLevel(buffer: ShortArray, size: Int) {
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        
        val average = sum / size
        val db = 20 * log10(average / 32768.0)
        val normalizedLevel = max(0f, min(1f, (db + 60) / 60f))
        
        _audioLevel.value = normalizedLevel
    }
    
    /**
     * 开始播放
     */
    fun startPlayback() {
        if (_isPlaying.value) {
            Log.w(TAG, "已经在播放中")
            return
        }
        
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                _isPlaying.value = true
                Log.d(TAG, "开始播放")
            } else {
                Log.e(TAG, "AudioTrack未初始化")
            }
        } ?: Log.e(TAG, "AudioTrack为空")
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        if (!_isPlaying.value) {
            return
        }
        
        audioTrack?.stop()
        _isPlaying.value = false
        
        Log.d(TAG, "停止播放")
    }
    
    /**
     * 设置均衡器
     */
    fun setEqualizerBand(band: Short, level: Short) {
        equalizer?.let {
            if (band < it.numberOfBands) {
                it.setBandLevel(band, level)
                Log.d(TAG, "设置均衡器频段 $band: $level")
            }
        }
    }
    
    /**
     * 启用/禁用均衡器
     */
    fun setEqualizerEnabled(enabled: Boolean) {
        equalizer?.enabled = enabled
        Log.d(TAG, "均衡器 ${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 设置低音增强
     */
    fun setBassBoostStrength(strength: Short) {
        bassBoost?.setStrength(strength)
        Log.d(TAG, "设置低音增强: $strength")
    }
    
    /**
     * 启用/禁用低音增强
     */
    fun setBassBoostEnabled(enabled: Boolean) {
        bassBoost?.enabled = enabled
        Log.d(TAG, "低音增强 ${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 设置虚拟环绕强度
     */
    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.setStrength(strength)
        Log.d(TAG, "设置虚拟环绕: $strength")
    }
    
    /**
     * 启用/禁用虚拟环绕
     */
    fun setVirtualizerEnabled(enabled: Boolean) {
        virtualizer?.enabled = enabled
        Log.d(TAG, "虚拟环绕 ${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 设置混响预设
     */
    fun setReverbPreset(preset: Short) {
        presetReverb?.preset = preset
        Log.d(TAG, "设置混响预设: $preset")
    }
    
    /**
     * 启用/禁用混响
     */
    fun setReverbEnabled(enabled: Boolean) {
        presetReverb?.enabled = enabled
        Log.d(TAG, "混响 ${if (enabled) "启用" else "禁用"}")
    }
    
    /**
     * 更新音频配置
     */
    fun updateAudioConfig(config: AudioConfig) {
        // 停止当前的音频处理
        stopRecording()
        stopPlayback()
        
        // 释放旧的资源
        release()
        
        // 重新初始化
        initialize(config)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopRecording()
        stopPlayback()
        
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.release()
        audioTrack = null
        
        equalizer?.release()
        equalizer = null
        
        bassBoost?.release()
        bassBoost = null
        
        virtualizer?.release()
        virtualizer = null
        
        presetReverb?.release()
        presetReverb = null
        
        audioScope.cancel()
        
        Log.d(TAG, "音频资源已释放")
    }
}

/**
 * 音频配置
 */
data class AudioConfig(
    val sampleRate: Int = AudioProcessor.DEFAULT_SAMPLE_RATE,
    val channelConfig: Int = AudioProcessor.DEFAULT_CHANNEL_CONFIG,
    val audioFormat: Int = AudioProcessor.DEFAULT_AUDIO_FORMAT,
    val enableInput: Boolean = true,
    val enableOutput: Boolean = true,
    val enableEffects: Boolean = true,
    val bufferSizeMultiplier: Int = AudioProcessor.DEFAULT_BUFFER_SIZE_MULTIPLIER
)