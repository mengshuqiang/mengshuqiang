package com.example.usbaudioprocessor.audio

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 系统音频捕获服务
 * 使用 AudioPlaybackCapture API (Android 10+) 捕获系统音频输出
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SystemAudioCapture : Service() {
    
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureJob: Job? = null
    
    private var isCapturing = false
    private var audioProcessor: AudioProcessor? = null
    
    // USB音频输出相关
    private var usbAudioTrack: AudioTrack? = null
    private var useUSBOutput = false
    
    companion object {
        private const val TAG = "SystemAudioCapture"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_capture_channel"
        
        // 音频参数
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        const val ACTION_START_CAPTURE = "com.example.usbaudioprocessor.START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.usbaudioprocessor.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_USE_USB = "use_usb_output"
    }
    
    override fun onCreate() {
        super.onCreate()
        audioProcessor = AudioProcessor()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                useUSBOutput = intent.getBooleanExtra(EXTRA_USE_USB, false)
                
                if (resultCode != -1 && resultData != null) {
                    startCapture(resultCode, resultData)
                }
            }
            ACTION_STOP_CAPTURE -> {
                stopCapture()
            }
        }
        
        return START_STICKY
    }
    
    @TargetApi(Build.VERSION_CODES.Q)
    private fun startCapture(resultCode: Int, resultData: Intent) {
        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 获取MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        
        // 配置AudioPlaybackCapture
        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        
        // 计算缓冲区大小
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT
        )
        val bufferSize = minBufferSize * 2
        
        // 创建AudioRecord用于捕获系统音频
        audioRecord = AudioRecord.Builder()
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_IN)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()
        
        // 创建音频输出（普通输出或USB输出）
        if (useUSBOutput) {
            createUSBAudioOutput(bufferSize)
        } else {
            createNormalAudioOutput(bufferSize)
        }
        
        // 开始捕获
        isCapturing = true
        captureJob = GlobalScope.launch(Dispatchers.IO) {
            captureAudioLoop(bufferSize)
        }
        
        audioRecord?.startRecording()
        audioTrack?.play()
        usbAudioTrack?.play()
        
        Log.i(TAG, "System audio capture started")
    }
    
    private fun createNormalAudioOutput(bufferSize: Int) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .build()
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
    
    private fun createUSBAudioOutput(bufferSize: Int) {
        // 尝试创建USB音频输出
        // 注意：需要系统支持USB音频路由
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_OUT)
            .build()
        
        // 获取USB音频设备信息
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        
        var usbDevice: AudioDeviceInfo? = null
        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                usbDevice = device
                Log.i(TAG, "Found USB audio device: ${device.productName}")
                break
            }
        }
        
        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
        
        // 如果找到USB设备，设置为首选设备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && usbDevice != null) {
            usbAudioTrack = trackBuilder.build()
            usbAudioTrack?.preferredDevice = usbDevice
            Log.i(TAG, "USB audio output configured")
        } else {
            // 回退到普通输出
            audioTrack = trackBuilder.build()
            Log.w(TAG, "USB device not found, using default output")
        }
    }
    
    private suspend fun captureAudioLoop(bufferSize: Int) {
        val audioBuffer = ByteArray(bufferSize)
        val shortBuffer = ShortArray(bufferSize / 2)
        
        while (isCapturing) {
            try {
                // 从系统音频读取数据
                val bytesRead = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                
                if (bytesRead > 0) {
                    // 转换为short数组以便处理
                    ByteBuffer.wrap(audioBuffer).asShortBuffer().get(shortBuffer, 0, bytesRead / 2)
                    
                    // 应用音频处理效果
                    val processedBuffer = audioProcessor?.let {
                        when (it.currentEffect) {
                            EffectType.ECHO -> it.applyEcho(shortBuffer, bytesRead / 2)
                            EffectType.REVERB -> it.applyReverb(shortBuffer, bytesRead / 2)
                            EffectType.EQUALIZER -> it.applyEqualizer(shortBuffer, bytesRead / 2, SAMPLE_RATE)
                            else -> shortBuffer
                        }
                    } ?: shortBuffer
                    
                    // 转换回byte数组
                    val processedBytes = ByteArray(bytesRead)
                    val byteBuffer = ByteBuffer.wrap(processedBytes)
                    byteBuffer.asShortBuffer().put(processedBuffer, 0, bytesRead / 2)
                    
                    // 输出到音频设备
                    if (usbAudioTrack != null) {
                        usbAudioTrack?.write(processedBytes, 0, bytesRead)
                    } else {
                        audioTrack?.write(processedBytes, 0, bytesRead)
                    }
                }
                
                yield()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in capture loop", e)
            }
        }
    }
    
    private fun stopCapture() {
        isCapturing = false
        
        captureJob?.cancel()
        captureJob = null
        
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
        
        usbAudioTrack?.apply {
            stop()
            release()
        }
        usbAudioTrack = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        stopForeground(true)
        
        Log.i(TAG, "System audio capture stopped")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频捕获服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于捕获和处理系统音频"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("音频捕获运行中")
                .setContentText("正在捕获和处理系统音频")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("音频捕获运行中")
                .setContentText("正在捕获和处理系统音频")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build()
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
}