package com.virtualheadset.audioprocessor.service

import android.app.*
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.virtualheadset.audioprocessor.MainActivity
import com.virtualheadset.audioprocessor.R
import com.virtualheadset.audioprocessor.audio.AudioProcessor
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 虚拟音频服务
 * 负责创建虚拟音频设备，拦截和处理音频流
 */
class VirtualAudioService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 音频录制器（捕获麦克风输入）
    private var audioRecord: AudioRecord? = null
    
    // 音频播放器（播放处理后的音频）
    private var audioTrack: AudioTrack? = null
    
    // 音频处理器
    private lateinit var audioProcessor: AudioProcessor
    
    // 音频参数
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // 服务状态
    private var isProcessing = false
    
    companion object {
        const val ACTION_START_PROCESSING = "START_PROCESSING"
        const val ACTION_STOP_PROCESSING = "STOP_PROCESSING"
        const val ACTION_APPLY_EFFECT = "APPLY_EFFECT"
        const val EXTRA_EFFECT_TYPE = "EFFECT_TYPE"
        
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "virtual_audio_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        audioProcessor = AudioProcessor()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PROCESSING -> startAudioProcessing()
            ACTION_STOP_PROCESSING -> stopAudioProcessing()
            ACTION_APPLY_EFFECT -> {
                val effectType = intent.getStringExtra(EXTRA_EFFECT_TYPE) ?: ""
                applyAudioEffect(effectType)
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopAudioProcessing()
        serviceScope.cancel()
    }
    
    /**
     * 开始音频处理
     */
    private fun startAudioProcessing() {
        if (isProcessing) return
        
        isProcessing = true
        
        // 初始化音频录制器
        initAudioRecord()
        
        // 初始化音频播放器
        initAudioTrack()
        
        // 启动音频处理循环
        serviceScope.launch {
            processAudioLoop()
        }
        
        updateNotification("正在处理音频...")
    }
    
    /**
     * 停止音频处理
     */
    private fun stopAudioProcessing() {
        isProcessing = false
        
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
                release()
            }
        }
        audioRecord = null
        
        audioTrack?.apply {
            if (state == AudioTrack.STATE_INITIALIZED) {
                stop()
                release()
            }
        }
        audioTrack = null
        
        updateNotification("音频处理已停止")
    }
    
    /**
     * 初始化音频录制器
     */
    private fun initAudioRecord() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(audioFormat)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } else {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
        }
        
        audioRecord?.startRecording()
    }
    
    /**
     * 初始化音频播放器
     */
    private fun initAudioTrack() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(audioFormat)
            .build()
        
        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        }
        
        audioTrack?.play()
    }
    
    /**
     * 音频处理循环
     */
    private suspend fun processAudioLoop() = withContext(Dispatchers.IO) {
        val buffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)
        
        while (isProcessing) {
            try {
                // 从麦克风读取音频数据
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (bytesRead > 0) {
                    // 转换为short数组进行处理
                    ByteBuffer.wrap(buffer).asShortBuffer().get(shortBuffer, 0, bytesRead / 2)
                    
                    // 处理音频数据
                    val processedAudio = audioProcessor.processAudio(shortBuffer, bytesRead / 2)
                    
                    // 转换回byte数组
                    val processedBytes = ByteArray(processedAudio.size * 2)
                    ByteBuffer.wrap(processedBytes).asShortBuffer().put(processedAudio)
                    
                    // 播放处理后的音频
                    audioTrack?.write(processedBytes, 0, processedBytes.size)
                    
                    // 同时可以将处理后的音频发送到虚拟音频输出
                    sendToVirtualOutput(processedBytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // 小延迟避免CPU占用过高
            delay(10)
        }
    }
    
    /**
     * 发送音频到虚拟输出设备
     * 这部分需要root权限或系统级权限来实现
     */
    private fun sendToVirtualOutput(audioData: ByteArray) {
        // 这里需要通过系统API或root权限将音频数据
        // 发送到虚拟音频设备，让其他应用（如微信）能够接收
        
        // 方案1：使用AudioPolicyService（需要系统权限）
        // 方案2：使用ALSA驱动创建虚拟声卡（需要root）
        // 方案3：使用MediaProjection API捕获和重定向音频
    }
    
    /**
     * 应用音频效果
     */
    private fun applyAudioEffect(effectType: String) {
        when (effectType) {
            "ECHO" -> audioProcessor.setEffect(AudioProcessor.Effect.ECHO)
            "REVERB" -> audioProcessor.setEffect(AudioProcessor.Effect.REVERB)
            "PITCH_UP" -> audioProcessor.setEffect(AudioProcessor.Effect.PITCH_UP)
            "PITCH_DOWN" -> audioProcessor.setEffect(AudioProcessor.Effect.PITCH_DOWN)
            "ROBOT" -> audioProcessor.setEffect(AudioProcessor.Effect.ROBOT)
            "NONE" -> audioProcessor.setEffect(AudioProcessor.Effect.NONE)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "虚拟音频服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "虚拟音频处理服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(contentText: String = "虚拟音频服务运行中"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("虚拟耳机")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_headset)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }
}