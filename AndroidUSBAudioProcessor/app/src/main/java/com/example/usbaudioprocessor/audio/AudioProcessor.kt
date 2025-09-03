package com.example.usbaudioprocessor.audio

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import kotlin.math.*

enum class EffectType {
    NONE,
    ECHO,
    REVERB,
    EQUALIZER,
    PITCH_SHIFT
}

class AudioProcessor {
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var processingJob: Job? = null
    
    private var isProcessing = false
    private var currentEffect = EffectType.NONE
    private var volume = 1.0f
    
    // 均衡器参数
    private val equalizerBands = FloatArray(3) { 0f } // 低音、中音、高音
    
    // 效果缓冲区
    private var echoBuffer: ShortArray? = null
    private var echoIndex = 0
    private val echoDelay = 0.3f // 300ms延迟
    private val echoDecay = 0.5f // 衰减系数
    
    // 混响参数
    private val reverbDelays = intArrayOf(347, 373, 421, 445) // 采样延迟
    private val reverbBuffers = Array(4) { ShortArray(48000) }
    private val reverbIndices = IntArray(4)
    private val reverbDecay = 0.3f
    
    companion object {
        private const val TAG = "AudioProcessor"
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    /**
     * 开始音频处理
     */
    fun startProcessing(audioFormat: Int, sampleRate: Int) {
        if (isProcessing) {
            Log.w(TAG, "音频处理已在运行")
            return
        }
        
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val outputChannelConfig = AudioFormat.CHANNEL_OUT_STEREO
        
        // 计算缓冲区大小
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        
        val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
        
        // 初始化效果缓冲区
        val echoSamples = (sampleRate * echoDelay).toInt()
        echoBuffer = ShortArray(echoSamples)
        
        // 创建AudioRecord用于录制
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        ).apply {
            if (state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord初始化失败")
            }
        }
        
        // 创建AudioTrack用于播放
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        val audioFormat = AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(outputChannelConfig)
            .build()
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().apply {
                if (state != AudioTrack.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioTrack初始化失败")
                }
            }
        
        // 启动处理循环
        isProcessing = true
        processingJob = GlobalScope.launch(Dispatchers.IO) {
            processAudioLoop(bufferSize, sampleRate)
        }
        
        audioRecord?.startRecording()
        audioTrack?.play()
        
        Log.d(TAG, "音频处理已启动")
    }
    
    /**
     * 音频处理主循环
     */
    private suspend fun processAudioLoop(bufferSize: Int, sampleRate: Int) {
        val audioBuffer = ShortArray(bufferSize / 2) // 16位音频，每个样本2字节
        
        while (isProcessing) {
            try {
                // 从输入读取音频数据
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // 应用音频效果
                    val processedBuffer = when (currentEffect) {
                        EffectType.NONE -> audioBuffer
                        EffectType.ECHO -> applyEcho(audioBuffer, bytesRead)
                        EffectType.REVERB -> applyReverb(audioBuffer, bytesRead)
                        EffectType.EQUALIZER -> applyEqualizer(audioBuffer, bytesRead, sampleRate)
                        EffectType.PITCH_SHIFT -> applyPitchShift(audioBuffer, bytesRead)
                    }
                    
                    // 应用音量控制
                    applyVolume(processedBuffer, bytesRead)
                    
                    // 输出处理后的音频
                    audioTrack?.write(processedBuffer, 0, bytesRead)
                }
                
                // 避免CPU过载
                yield()
                
            } catch (e: Exception) {
                Log.e(TAG, "音频处理错误", e)
            }
        }
    }
    
    /**
     * 应用回声效果
     */
    private fun applyEcho(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val echoBuffer = this.echoBuffer ?: return input
        
        for (i in 0 until length) {
            // 获取延迟的样本
            val echoSample = echoBuffer[echoIndex]
            
            // 混合原始信号和回声
            val mixed = (input[i] + echoSample * echoDecay).toInt()
            output[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            
            // 更新回声缓冲区
            echoBuffer[echoIndex] = input[i]
            echoIndex = (echoIndex + 1) % echoBuffer.size
        }
        
        return output
    }
    
    /**
     * 应用混响效果
     */
    private fun applyReverb(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        
        for (i in 0 until length) {
            var reverbSum = 0f
            
            // 累加所有混响延迟线
            for (j in reverbBuffers.indices) {
                val buffer = reverbBuffers[j]
                val index = reverbIndices[j]
                
                reverbSum += buffer[index] * reverbDecay
                
                // 更新混响缓冲区
                buffer[index] = input[i]
                reverbIndices[j] = (index + 1) % reverbDelays[j]
            }
            
            // 混合原始信号和混响
            val mixed = (input[i] * 0.7f + reverbSum * 0.3f).toInt()
            output[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return output
    }
    
    /**
     * 应用均衡器效果
     */
    private fun applyEqualizer(input: ShortArray, length: Int, sampleRate: Int): ShortArray {
        val output = ShortArray(length)
        
        // 简化的3频段均衡器
        // 低音: < 250 Hz
        // 中音: 250 Hz - 4000 Hz  
        // 高音: > 4000 Hz
        
        for (i in 0 until length) {
            var sample = input[i].toFloat()
            
            // 应用频段增益
            // 这是一个简化的实现，实际应该使用IIR或FIR滤波器
            sample *= (1.0f + equalizerBands[0] * 0.1f) // 低音增益
            sample *= (1.0f + equalizerBands[1] * 0.1f) // 中音增益
            sample *= (1.0f + equalizerBands[2] * 0.1f) // 高音增益
            
            output[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return output
    }
    
    /**
     * 应用变调效果
     */
    private fun applyPitchShift(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val pitchFactor = 1.2f // 提高音调20%
        
        // 简单的重采样实现
        for (i in 0 until length) {
            val sourceIndex = (i / pitchFactor).toInt()
            if (sourceIndex < length) {
                output[i] = input[sourceIndex]
            } else {
                output[i] = 0
            }
        }
        
        return output
    }
    
    /**
     * 应用音量控制
     */
    private fun applyVolume(buffer: ShortArray, length: Int) {
        for (i in 0 until length) {
            val sample = (buffer[i] * volume).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
    
    /**
     * 设置音频效果
     */
    fun setEffect(effect: EffectType) {
        currentEffect = effect
        Log.d(TAG, "音频效果已设置为: $effect")
    }
    
    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 2f)
    }
    
    /**
     * 设置均衡器频段
     */
    fun setEqualizerBand(band: Int, gain: Float) {
        if (band in equalizerBands.indices) {
            equalizerBands[band] = gain.coerceIn(-10f, 10f)
        }
    }
    
    /**
     * 停止音频处理
     */
    fun stopProcessing() {
        isProcessing = false
        
        processingJob?.cancel()
        processingJob = null
        
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
        
        Log.d(TAG, "音频处理已停止")
    }
    
    /**
     * 获取当前音频电平
     */
    fun getAudioLevel(): Float {
        // 这里可以实现实时音频电平检测
        return 0f
    }
}