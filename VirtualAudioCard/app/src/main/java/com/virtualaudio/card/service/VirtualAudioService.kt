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

class VirtualAudioService : Service() {
    
    companion object {
        private const val TAG = "VirtualAudioService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "virtual_audio_channel"
        
        // 音频参数
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 4096
    }
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = AtomicBoolean(false)
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 虚拟音频缓冲区
    private val inputBuffer = ByteArray(BUFFER_SIZE)
    private val outputBuffer = ByteArray(BUFFER_SIZE)
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeAudioComponents()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startVirtualAudioLoop()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopVirtualAudioLoop()
        releaseAudioComponents()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "虚拟音频服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "虚拟声卡音频处理服务"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("虚拟声卡运行中")
            .setContentText("正在处理音频数据")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
    
    private fun initializeAudioComponents() {
        try {
            // 初始化音频录制
            val minBufferSizeIn = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                maxOf(minBufferSizeIn, BUFFER_SIZE)
            )
            
            // 初始化音频播放
            val minBufferSizeOut = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSizeOut, BUFFER_SIZE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                
            Log.d(TAG, "音频组件初始化成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "音频组件初始化失败", e)
        }
    }
    
    private fun startVirtualAudioLoop() {
        if (isRecording.compareAndSet(false, true)) {
            serviceScope.launch {
                try {
                    audioRecord?.startRecording()
                    audioTrack?.play()
                    
                    Log.d(TAG, "虚拟音频循环开始")
                    
                    while (isRecording.get()) {
                        // 录制音频数据
                        val bytesRead = audioRecord?.read(inputBuffer, 0, inputBuffer.size) ?: 0
                        
                        if (bytesRead > 0) {
                            // 这里可以添加音频处理逻辑
                            processAudioData(inputBuffer, outputBuffer, bytesRead)
                            
                            // 播放处理后的音频
                            audioTrack?.write(outputBuffer, 0, bytesRead)
                        }
                        
                        // 避免CPU过度使用
                        delay(1)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "虚拟音频循环错误", e)
                } finally {
                    audioRecord?.stop()
                    audioTrack?.stop()
                }
            }
        }
    }
    
    private fun stopVirtualAudioLoop() {
        isRecording.set(false)
    }
    
    private fun processAudioData(input: ByteArray, output: ByteArray, length: Int) {
        // 基本的音频处理 - 这里可以添加更复杂的处理逻辑
        // 例如：降噪、变声、回声消除等
        
        for (i in 0 until length step 2) {
            if (i + 1 < length) {
                // 读取16位音频样本
                val sample = (input[i].toInt() and 0xFF) or 
                           ((input[i + 1].toInt() and 0xFF) shl 8)
                
                // 简单的音量调节和滤波
                var processedSample = (sample * 0.8).toInt() // 降低音量
                
                // 简单的高通滤波（移除低频噪音）
                processedSample = if (Math.abs(processedSample) < 100) 0 else processedSample
                
                // 限制范围
                processedSample = processedSample.coerceIn(-32768, 32767)
                
                // 写回输出缓冲区
                output[i] = (processedSample and 0xFF).toByte()
                output[i + 1] = ((processedSample shr 8) and 0xFF).toByte()
            }
        }
    }
    
    private fun releaseAudioComponents() {
        try {
            audioRecord?.release()
            audioTrack?.release()
            Log.d(TAG, "音频组件已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放音频组件时出错", e)
        }
    }
}