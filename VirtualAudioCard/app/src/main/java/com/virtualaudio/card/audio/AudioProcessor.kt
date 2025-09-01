package com.virtualaudio.card.audio

import android.util.Log
import kotlin.math.*

class AudioProcessor(private val sampleRate: Int) {
    
    companion object {
        private const val TAG = "AudioProcessor"
    }
    
    // 音频处理组件
    private val noiseReducer = NoiseReducer(sampleRate)
    private val voiceEnhancer = VoiceEnhancer(sampleRate)
    private val echoSuppressor = EchoSuppressor(sampleRate)
    private val automaticGainControl = AutomaticGainControl()
    
    /**
     * 处理输入音频（麦克风 -> 微信发送）
     */
    fun processInputAudio(inputSamples: FloatArray): FloatArray {
        var processed = inputSamples.copyOf()
        
        // 1. 噪音抑制
        processed = noiseReducer.reduce(processed)
        
        // 2. 语音增强
        processed = voiceEnhancer.enhance(processed)
        
        // 3. 回声抑制
        processed = echoSuppressor.suppress(processed)
        
        // 4. 自动增益控制
        processed = automaticGainControl.process(processed)
        
        return processed
    }
    
    /**
     * 处理输出音频（微信接收 -> 耳机播放）
     */
    fun processOutputAudio(outputSamples: FloatArray): FloatArray {
        var processed = outputSamples.copyOf()
        
        // 1. 噪音抑制（轻度）
        processed = noiseReducer.reduce(processed, intensity = 0.3f)
        
        // 2. 语音清晰度增强
        processed = voiceEnhancer.clarify(processed)
        
        // 3. 音量标准化
        processed = automaticGainControl.normalize(processed)
        
        return processed
    }
}

/**
 * 噪音抑制器
 */
class NoiseReducer(private val sampleRate: Int) {
    
    private val fftSize = 1024
    private val overlapFactor = 0.5f
    private val noiseProfile = FloatArray(fftSize / 2 + 1)
    private var isNoiseProfileReady = false
    
    // 简化的频谱减法噪音抑制
    fun reduce(samples: FloatArray, intensity: Float = 0.7f): FloatArray {
        if (samples.size < fftSize) return samples
        
        val result = samples.copyOf()
        
        // 简单的时域噪音门限
        val threshold = calculateNoiseThreshold(samples)
        
        for (i in samples.indices) {
            val amplitude = abs(samples[i])
            if (amplitude < threshold * intensity) {
                result[i] *= (1.0f - intensity)
            }
        }
        
        return result
    }
    
    private fun calculateNoiseThreshold(samples: FloatArray): Float {
        // 计算样本的RMS值作为噪音阈值
        var sum = 0.0f
        for (sample in samples) {
            sum += sample * sample
        }
        return sqrt(sum / samples.size) * 0.1f // 10%的RMS作为噪音阈值
    }
}

/**
 * 语音增强器
 */
class VoiceEnhancer(private val sampleRate: Int) {
    
    private val voiceFreqLow = 300.0  // 人声频率下限
    private val voiceFreqHigh = 3400.0 // 人声频率上限
    
    private val bandPassFilter = BandPassFilter(sampleRate, voiceFreqLow, voiceFreqHigh)
    private val preEmphasisFilter = PreEmphasisFilter()
    
    fun enhance(samples: FloatArray): FloatArray {
        var enhanced = samples.copyOf()
        
        // 1. 预加重滤波
        enhanced = preEmphasisFilter.process(enhanced)
        
        // 2. 带通滤波（突出人声频段）
        for (i in enhanced.indices) {
            enhanced[i] = bandPassFilter.process(enhanced[i])
        }
        
        // 3. 动态范围压缩
        enhanced = applyDynamicRangeCompression(enhanced)
        
        return enhanced
    }
    
    fun clarify(samples: FloatArray): FloatArray {
        var clarified = samples.copyOf()
        
        // 语音清晰度增强（轻度处理）
        for (i in clarified.indices) {
            clarified[i] = bandPassFilter.process(clarified[i]) * 1.1f
        }
        
        return clarified
    }
    
    private fun applyDynamicRangeCompression(samples: FloatArray): FloatArray {
        val threshold = 0.3f
        val ratio = 3.0f
        val result = samples.copyOf()
        
        for (i in samples.indices) {
            val amplitude = abs(samples[i])
            if (amplitude > threshold) {
                val excess = amplitude - threshold
                val compressedExcess = excess / ratio
                val newAmplitude = threshold + compressedExcess
                result[i] = if (samples[i] >= 0) newAmplitude else -newAmplitude
            }
        }
        
        return result
    }
}

/**
 * 回声抑制器
 */
class EchoSuppressor(private val sampleRate: Int) {
    
    private val delayBufferSize = (sampleRate * 0.5).toInt() // 500ms延迟缓冲
    private val delayBuffer = FloatArray(delayBufferSize)
    private var delayIndex = 0
    
    fun suppress(samples: FloatArray): FloatArray {
        val result = samples.copyOf()
        
        for (i in samples.indices) {
            // 获取延迟的样本
            val delayedSample = delayBuffer[delayIndex]
            
            // 简单的回声消除：当前样本减去延迟样本的一部分
            result[i] = samples[i] - delayedSample * 0.3f
            
            // 更新延迟缓冲区
            delayBuffer[delayIndex] = samples[i]
            delayIndex = (delayIndex + 1) % delayBufferSize
        }
        
        return result
    }
}

/**
 * 自动增益控制
 */
class AutomaticGainControl {
    
    private var targetLevel = 0.7f
    private var currentGain = 1.0f
    private val attackTime = 0.01f
    private val releaseTime = 0.1f
    
    fun process(samples: FloatArray): FloatArray {
        val result = samples.copyOf()
        
        // 计算RMS电平
        var rms = 0.0f
        for (sample in samples) {
            rms += sample * sample
        }
        rms = sqrt(rms / samples.size)
        
        // 计算所需增益
        val targetGain = if (rms > 0) targetLevel / rms else 1.0f
        
        // 平滑增益变化
        val alpha = if (targetGain > currentGain) attackTime else releaseTime
        currentGain += alpha * (targetGain - currentGain)
        
        // 限制增益范围
        currentGain = currentGain.coerceIn(0.1f, 10.0f)
        
        // 应用增益
        for (i in result.indices) {
            result[i] *= currentGain
            result[i] = result[i].coerceIn(-1.0f, 1.0f) // 防止削波
        }
        
        return result
    }
    
    fun normalize(samples: FloatArray): FloatArray {
        val maxAmplitude = samples.maxOfOrNull { abs(it) } ?: 1.0f
        val normalizationFactor = if (maxAmplitude > 0) 0.8f / maxAmplitude else 1.0f
        
        return samples.map { it * normalizationFactor }.toFloatArray()
    }
}

/**
 * 带通滤波器
 */
class BandPassFilter(
    private val sampleRate: Int,
    private val lowFreq: Double,
    private val highFreq: Double
) {
    private val highPassFilter = HighPassFilter(sampleRate, lowFreq)
    private val lowPassFilter = LowPassFilter(sampleRate, highFreq)
    
    fun process(input: Float): Float {
        return lowPassFilter.process(highPassFilter.process(input))
    }
}

/**
 * 高通滤波器
 */
class HighPassFilter(sampleRate: Int, cutoffFreq: Double) {
    private val rc = 1.0 / (cutoffFreq * 2 * PI)
    private val dt = 1.0 / sampleRate
    private val alpha = rc / (rc + dt)
    private var lastInput = 0.0f
    private var lastOutput = 0.0f
    
    fun process(input: Float): Float {
        val output = (alpha * (lastOutput + input - lastInput)).toFloat()
        lastInput = input
        lastOutput = output
        return output
    }
}

/**
 * 低通滤波器
 */
class LowPassFilter(sampleRate: Int, cutoffFreq: Double) {
    private val rc = 1.0 / (cutoffFreq * 2 * PI)
    private val dt = 1.0 / sampleRate
    private val alpha = dt / (rc + dt)
    private var lastOutput = 0.0f
    
    fun process(input: Float): Float {
        lastOutput = (alpha * input + (1 - alpha) * lastOutput).toFloat()
        return lastOutput
    }
}

/**
 * 预加重滤波器
 */
class PreEmphasisFilter {
    private var lastSample = 0.0f
    private val alpha = 0.97f
    
    fun process(samples: FloatArray): FloatArray {
        val result = samples.copyOf()
        
        for (i in samples.indices) {
            val current = samples[i]
            result[i] = current - alpha * lastSample
            lastSample = current
        }
        
        return result
    }
}