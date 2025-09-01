package com.virtualaudio.card.audio

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 虚拟音频设备 - 模拟音频输入输出设备
 */
class VirtualAudioDevice(
    private val sampleRate: Int = 44100,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    
    companion object {
        private const val TAG = "VirtualAudioDevice"
        private const val BUFFER_SIZE = 4096
    }
    
    // 音频组件
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioProcessor: AudioProcessor? = null
    
    // 控制状态
    private val isRunning = AtomicBoolean(false)
    private var processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 音频数据队列
    private val inputQueue = ConcurrentLinkedQueue<AudioData>()
    private val outputQueue = ConcurrentLinkedQueue<AudioData>()
    
    // 回调接口
    private var audioInputCallback: ((ByteArray, Int) -> Unit)? = null
    private var audioOutputCallback: ((ByteArray, Int) -> ByteArray)? = null
    
    data class AudioData(
        val data: ByteArray,
        val length: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    fun initialize(): Boolean {
        return try {
            initializeAudioRecord()
            initializeAudioTrack()
            audioProcessor = AudioProcessor(sampleRate)
            Log.d(TAG, "虚拟音频设备初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "虚拟音频设备初始化失败", e)
            false
        }
    }
    
    private fun initializeAudioRecord() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord初始化失败")
        }
    }
    
    private fun initializeAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat
        )
        val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            throw IllegalStateException("AudioTrack初始化失败")
        }
    }
    
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            try {
                audioRecord?.startRecording()
                audioTrack?.play()
                
                // 启动音频处理协程
                startAudioInputLoop()
                startAudioOutputLoop()
                startAudioProcessingLoop()
                
                Log.d(TAG, "虚拟音频设备已启动")
            } catch (e: Exception) {
                Log.e(TAG, "启动虚拟音频设备失败", e)
                isRunning.set(false)
            }
        }
    }
    
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                audioRecord?.stop()
                audioTrack?.stop()
                Log.d(TAG, "虚拟音频设备已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止虚拟音频设备时出错", e)
            }
        }
    }
    
    private fun startAudioInputLoop() {
        processingScope.launch {
            val buffer = ByteArray(BUFFER_SIZE)
            
            while (isRunning.get()) {
                try {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (bytesRead > 0) {
                        // 添加到输入队列
                        inputQueue.offer(AudioData(buffer.copyOf(), bytesRead))
                        
                        // 通知回调
                        audioInputCallback?.invoke(buffer, bytesRead)
                        
                        // 限制队列大小
                        while (inputQueue.size > 10) {
                            inputQueue.poll()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "音频输入循环错误", e)
                }
                
                delay(1) // 避免CPU过度使用
            }
        }
    }
    
    private fun startAudioOutputLoop() {
        processingScope.launch {
            while (isRunning.get()) {
                try {
                    val audioData = outputQueue.poll()
                    
                    if (audioData != null) {
                        // 播放音频数据
                        audioTrack?.write(audioData.data, 0, audioData.length)
                    } else {
                        delay(5) // 没有数据时稍微等待
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "音频输出循环错误", e)
                }
            }
        }
    }
    
    private fun startAudioProcessingLoop() {
        processingScope.launch {
            while (isRunning.get()) {
                try {
                    val inputData = inputQueue.poll()
                    
                    if (inputData != null && audioProcessor != null) {
                        // 转换为浮点数组
                        val floatSamples = bytesToFloats(inputData.data, inputData.length)
                        
                        // 处理音频
                        val processedSamples = audioProcessor!!.processInputAudio(floatSamples)
                        
                        // 转换回字节数组
                        val processedBytes = floatsToBytes(processedSamples)
                        
                        // 如果有输出回调，使用回调处理
                        val finalBytes = audioOutputCallback?.invoke(processedBytes, processedBytes.size)
                            ?: processedBytes
                        
                        // 添加到输出队列
                        outputQueue.offer(AudioData(finalBytes, finalBytes.size))
                    } else {
                        delay(1)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "音频处理循环错误", e)
                }
            }
        }
    }
    
    private fun bytesToFloats(bytes: ByteArray, length: Int): FloatArray {
        val floats = FloatArray(length / 2)
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                val sample = (bytes[i].toInt() and 0xFF) or 
                           ((bytes[i + 1].toInt() and 0xFF) shl 8)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                floats[i / 2] = signedSample / 32768.0f
            }
        }
        return floats
    }
    
    private fun floatsToBytes(floats: FloatArray): ByteArray {
        val bytes = ByteArray(floats.size * 2)
        for (i in floats.indices) {
            val sample = (floats[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
            val index = i * 2
            bytes[index] = (sample and 0xFF).toByte()
            bytes[index + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }
    
    fun setAudioInputCallback(callback: (ByteArray, Int) -> Unit) {
        audioInputCallback = callback
    }
    
    fun setAudioOutputCallback(callback: (ByteArray, Int) -> ByteArray) {
        audioOutputCallback = callback
    }
    
    fun release() {
        stop()
        try {
            audioRecord?.release()
            audioTrack?.release()
            processingScope.cancel()
            Log.d(TAG, "虚拟音频设备已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放虚拟音频设备时出错", e)
        }
    }
    
    // 获取音频设备信息
    fun getDeviceInfo(): String {
        val info = StringBuilder()
        info.append("采样率: ${sampleRate}Hz\n")
        info.append("声道配置: ${if (channelConfig == AudioFormat.CHANNEL_IN_MONO) "单声道" else "立体声"}\n")
        info.append("音频格式: ${if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) "PCM 16位" else "其他"}\n")
        info.append("缓冲区大小: ${BUFFER_SIZE}字节\n")
        info.append("运行状态: ${if (isRunning.get()) "运行中" else "已停止"}\n")
        return info.toString()
    }
}