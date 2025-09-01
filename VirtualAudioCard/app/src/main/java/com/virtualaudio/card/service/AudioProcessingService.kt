package com.virtualaudio.card.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class AudioProcessingService : Service() {
    
    companion object {
        private const val TAG = "AudioProcessingService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "audio_processing_channel"
        
        // 音频参数
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE = 4096
    }
    
    private var isProcessing = AtomicBoolean(false)
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 音频处理参数
    private var noiseGate = -40.0 // dB
    private var compressorRatio = 4.0
    private var compressorThreshold = -20.0 // dB
    
    // 滤波器状态
    private var highPassFilter = HighPassFilter(SAMPLE_RATE, 80.0) // 80Hz高通
    private var lowPassFilter = LowPassFilter(SAMPLE_RATE, 8000.0) // 8kHz低通
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startAudioProcessing()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopAudioProcessing()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频处理服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "实时音频处理和增强"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("音频处理运行中")
            .setContentText("正在进行实时音频增强")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
    
    private fun startAudioProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "音频处理服务启动")
        }
    }
    
    private fun stopAudioProcessing() {
        isProcessing.set(false)
        Log.d(TAG, "音频处理服务停止")
    }
    
    /**
     * 处理音频数据的主要函数
     * @param inputData 输入的原始音频数据
     * @param outputData 输出的处理后音频数据
     * @param length 数据长度
     */
    fun processAudioBuffer(inputData: ByteArray, outputData: ByteArray, length: Int) {
        if (!isProcessing.get()) {
            // 如果未启动处理，直接复制数据
            System.arraycopy(inputData, 0, outputData, 0, length)
            return
        }
        
        // 转换为浮点数组进行处理
        val samples = FloatArray(length / 2)
        bytesToFloats(inputData, samples, length)
        
        // 应用音频处理链
        applyNoiseGate(samples)
        applyHighPassFilter(samples)
        applyLowPassFilter(samples)
        applyCompressor(samples)
        applyNormalization(samples)
        
        // 转换回字节数组
        floatsToBytes(samples, outputData)
    }
    
    private fun bytesToFloats(bytes: ByteArray, floats: FloatArray, length: Int) {
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (bytes[i].toInt() and 0xFF) or 
                           ((bytes[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                floats[i / 2] = signedSample / 32768.0f
            }
        }
    }
    
    private fun floatsToBytes(floats: FloatArray, bytes: ByteArray) {
        for (i in floats.indices) {
            val sample = (floats[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
            val index = i * 2
            if (index + 1 < bytes.size) {
                bytes[index] = (sample and 0xFF).toByte()
                bytes[index + 1] = ((sample shr 8) and 0xFF).toByte()
            }
        }
    }
    
    private fun applyNoiseGate(samples: FloatArray) {
        val threshold = dbToLinear(noiseGate)
        for (i in samples.indices) {
            val amplitude = abs(samples[i])
            if (amplitude < threshold) {
                samples[i] = 0.0f
            }
        }
    }
    
    private fun applyHighPassFilter(samples: FloatArray) {
        for (i in samples.indices) {
            samples[i] = highPassFilter.process(samples[i])
        }
    }
    
    private fun applyLowPassFilter(samples: FloatArray) {
        for (i in samples.indices) {
            samples[i] = lowPassFilter.process(samples[i])
        }
    }
    
    private fun applyCompressor(samples: FloatArray) {
        val threshold = dbToLinear(compressorThreshold)
        for (i in samples.indices) {
            val amplitude = abs(samples[i])
            if (amplitude > threshold) {
                val excess = amplitude - threshold
                val compressedExcess = excess / compressorRatio
                val newAmplitude = threshold + compressedExcess
                samples[i] = if (samples[i] >= 0) newAmplitude else -newAmplitude
            }
        }
    }
    
    private fun applyNormalization(samples: FloatArray) {
        // 找到最大振幅
        var maxAmplitude = 0.0f
        for (sample in samples) {
            maxAmplitude = maxOf(maxAmplitude, abs(sample))
        }
        
        // 标准化到0.8倍最大值，避免削波
        if (maxAmplitude > 0.0f) {
            val normalizationFactor = 0.8f / maxAmplitude
            for (i in samples.indices) {
                samples[i] *= normalizationFactor
            }
        }
    }
    
    private fun dbToLinear(db: Double): Float {
        return 10.0f.pow((db / 20.0).toFloat())
    }
    
    // 简单的高通滤波器实现
    private class HighPassFilter(sampleRate: Int, cutoffFreq: Double) {
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
    
    // 简单的低通滤波器实现
    private class LowPassFilter(sampleRate: Int, cutoffFreq: Double) {
        private val rc = 1.0 / (cutoffFreq * 2 * PI)
        private val dt = 1.0 / sampleRate
        private val alpha = dt / (rc + dt)
        private var lastOutput = 0.0f
        
        fun process(input: Float): Float {
            lastOutput = (alpha * input + (1 - alpha) * lastOutput).toFloat()
            return lastOutput
        }
    }
}