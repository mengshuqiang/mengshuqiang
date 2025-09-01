package com.virtualheadset.audioprocessor.audio

import kotlin.math.*

/**
 * 音频处理器
 * 提供各种音频效果处理功能
 */
class AudioProcessor {
    
    enum class Effect {
        NONE,
        ECHO,
        REVERB,
        PITCH_UP,
        PITCH_DOWN,
        ROBOT,
        DISTORTION,
        CHORUS,
        FLANGER
    }
    
    private var currentEffect = Effect.NONE
    
    // 效果参数
    private val echoDelay = 0.2f // 回声延迟（秒）
    private val echoDecay = 0.5f // 回声衰减
    private var echoBuffer: ShortArray? = null
    private var echoBufferIndex = 0
    
    // 混响参数
    private val reverbDecay = 0.3f
    private val reverbDelays = intArrayOf(1557, 1617, 1491, 1422) // 采样延迟
    private val reverbBuffers = Array(4) { ShortArray(2000) }
    private val reverbIndices = IntArray(4)
    
    // 变调参数
    private val pitchUpFactor = 1.2f
    private val pitchDownFactor = 0.8f
    
    // 机器人效果参数
    private var robotPhase = 0.0
    private val robotFrequency = 100.0 // Hz
    
    /**
     * 设置音频效果
     */
    fun setEffect(effect: Effect) {
        currentEffect = effect
        // 重置效果缓冲区
        when (effect) {
            Effect.ECHO -> initEchoBuffer()
            Effect.REVERB -> initReverbBuffers()
            else -> {}
        }
    }
    
    /**
     * 处理音频数据
     */
    fun processAudio(input: ShortArray, length: Int): ShortArray {
        return when (currentEffect) {
            Effect.NONE -> input.copyOf(length)
            Effect.ECHO -> applyEcho(input, length)
            Effect.REVERB -> applyReverb(input, length)
            Effect.PITCH_UP -> applyPitchShift(input, length, pitchUpFactor)
            Effect.PITCH_DOWN -> applyPitchShift(input, length, pitchDownFactor)
            Effect.ROBOT -> applyRobotEffect(input, length)
            Effect.DISTORTION -> applyDistortion(input, length)
            Effect.CHORUS -> applyChorus(input, length)
            Effect.FLANGER -> applyFlanger(input, length)
        }
    }
    
    /**
     * 初始化回声缓冲区
     */
    private fun initEchoBuffer() {
        val bufferSize = (44100 * echoDelay).toInt()
        echoBuffer = ShortArray(bufferSize)
        echoBufferIndex = 0
    }
    
    /**
     * 初始化混响缓冲区
     */
    private fun initReverbBuffers() {
        for (i in reverbBuffers.indices) {
            reverbBuffers[i].fill(0)
            reverbIndices[i] = 0
        }
    }
    
    /**
     * 应用回声效果
     */
    private fun applyEcho(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val buffer = echoBuffer ?: return input.copyOf(length)
        
        for (i in 0 until length) {
            // 从缓冲区读取延迟的样本
            val delayedSample = buffer[echoBufferIndex]
            
            // 混合原始信号和延迟信号
            val mixed = (input[i] + delayedSample * echoDecay).toInt()
            output[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            
            // 将当前样本存入缓冲区
            buffer[echoBufferIndex] = input[i]
            echoBufferIndex = (echoBufferIndex + 1) % buffer.size
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
            
            // 从多个延迟线获取样本并混合
            for (j in reverbBuffers.indices) {
                val delayedSample = reverbBuffers[j][reverbIndices[j]]
                reverbSum += delayedSample * reverbDecay
                
                // 更新延迟线
                reverbBuffers[j][reverbIndices[j]] = input[i]
                reverbIndices[j] = (reverbIndices[j] + 1) % reverbDelays[j]
            }
            
            // 混合原始信号和混响信号
            val mixed = (input[i] + reverbSum / reverbBuffers.size).toInt()
            output[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return output
    }
    
    /**
     * 应用变调效果（简单的重采样方法）
     */
    private fun applyPitchShift(input: ShortArray, length: Int, factor: Float): ShortArray {
        val outputLength = (length / factor).toInt()
        val output = ShortArray(length)
        
        for (i in 0 until minOf(outputLength, length)) {
            val srcIndex = (i * factor).toInt()
            if (srcIndex < length) {
                output[i] = input[srcIndex]
            }
        }
        
        // 填充剩余部分
        for (i in outputLength until length) {
            output[i] = 0
        }
        
        return output
    }
    
    /**
     * 应用机器人效果（环形调制）
     */
    private fun applyRobotEffect(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val sampleRate = 44100.0
        
        for (i in 0 until length) {
            // 生成载波信号
            val carrier = sin(2.0 * PI * robotFrequency * robotPhase / sampleRate)
            
            // 环形调制
            output[i] = (input[i] * carrier).toInt().toShort()
            
            // 更新相位
            robotPhase += 1.0
            if (robotPhase >= sampleRate) {
                robotPhase -= sampleRate
            }
        }
        
        return output
    }
    
    /**
     * 应用失真效果
     */
    private fun applyDistortion(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val threshold = Short.MAX_VALUE * 0.7
        
        for (i in 0 until length) {
            val sample = input[i].toFloat()
            val distorted = when {
                sample > threshold -> threshold + (sample - threshold) * 0.3f
                sample < -threshold -> -threshold + (sample + threshold) * 0.3f
                else -> sample
            }
            output[i] = distorted.toInt().toShort()
        }
        
        return output
    }
    
    /**
     * 应用合唱效果
     */
    private fun applyChorus(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val delayBuffer = ShortArray(1000)
        var delayIndex = 0
        val lfoFreq = 0.5 // Hz
        var lfoPhase = 0.0
        
        for (i in 0 until length) {
            // LFO调制延迟时间
            val lfo = sin(2.0 * PI * lfoFreq * lfoPhase / 44100.0)
            val delay = (20 + 10 * lfo).toInt()
            
            // 获取延迟样本
            val delayedIndex = (delayIndex - delay + delayBuffer.size) % delayBuffer.size
            val delayedSample = delayBuffer[delayedIndex]
            
            // 混合原始和延迟信号
            output[i] = ((input[i] + delayedSample * 0.5) / 1.5).toInt().toShort()
            
            // 更新缓冲区
            delayBuffer[delayIndex] = input[i]
            delayIndex = (delayIndex + 1) % delayBuffer.size
            lfoPhase += 1.0
        }
        
        return output
    }
    
    /**
     * 应用镶边效果
     */
    private fun applyFlanger(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val delayBuffer = ShortArray(500)
        var delayIndex = 0
        val lfoFreq = 0.2 // Hz
        var lfoPhase = 0.0
        
        for (i in 0 until length) {
            // LFO调制延迟时间（更短的延迟）
            val lfo = sin(2.0 * PI * lfoFreq * lfoPhase / 44100.0)
            val delay = (5 + 3 * lfo).toInt()
            
            // 获取延迟样本
            val delayedIndex = (delayIndex - delay + delayBuffer.size) % delayBuffer.size
            val delayedSample = delayBuffer[delayedIndex]
            
            // 混合原始和延迟信号（带反馈）
            val mixed = input[i] + delayedSample * 0.7f
            output[i] = (mixed / 1.7).toInt().toShort()
            
            // 更新缓冲区（带反馈）
            delayBuffer[delayIndex] = (mixed * 0.3).toInt().toShort()
            delayIndex = (delayIndex + 1) % delayBuffer.size
            lfoPhase += 1.0
        }
        
        return output
    }
    
    /**
     * 应用均衡器
     */
    fun applyEqualizer(input: ShortArray, length: Int, bands: FloatArray): ShortArray {
        // 简化的均衡器实现
        // 实际应用中需要使用FFT进行频域处理
        return input.copyOf(length)
    }
    
    /**
     * 应用降噪
     */
    fun applyNoiseReduction(input: ShortArray, length: Int): ShortArray {
        val output = ShortArray(length)
        val threshold = 100 // 噪声阈值
        
        for (i in 0 until length) {
            output[i] = if (abs(input[i]) < threshold) 0 else input[i]
        }
        
        return output
    }
    
    /**
     * 应用压缩器
     */
    fun applyCompressor(input: ShortArray, length: Int, ratio: Float = 4f): ShortArray {
        val output = ShortArray(length)
        val threshold = Short.MAX_VALUE * 0.6f
        
        for (i in 0 until length) {
            val sample = input[i].toFloat()
            val absSample = abs(sample)
            
            val compressed = if (absSample > threshold) {
                val excess = absSample - threshold
                val compressedExcess = excess / ratio
                val sign = if (sample < 0) -1 else 1
                sign * (threshold + compressedExcess)
            } else {
                sample
            }
            
            output[i] = compressed.toInt().toShort()
        }
        
        return output
    }
}