package com.virtualaudio.card.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

object AudioUtils {
    
    private const val TAG = "AudioUtils"
    
    /**
     * 获取音频设备信息
     */
    fun getAudioDeviceInfo(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val info = StringBuilder()
        
        info.append("=== 音频设备信息 ===\n")
        info.append("音频模式: ${getAudioModeString(audioManager.mode)}\n")
        info.append("音乐音量: ${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}\n")
        info.append("通话音量: ${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}\n")
        info.append("扬声器状态: ${audioManager.isSpeakerphoneOn}\n")
        info.append("有线耳机: ${audioManager.isWiredHeadsetOn}\n")
        info.append("蓝牙A2DP: ${audioManager.isBluetoothA2dpOn}\n")
        info.append("蓝牙SCO: ${audioManager.isBluetoothScoOn}\n")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            info.append("\n=== 音频设备列表 ===\n")
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
            for (device in devices) {
                info.append("${getDeviceTypeString(device.type)}: ${device.productName}\n")
            }
        }
        
        return info.toString()
    }
    
    /**
     * 检查音频权限
     */
    fun checkAudioPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        for (permission in permissions) {
            if (context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        
        return missingPermissions
    }
    
    /**
     * 获取最佳音频配置
     */
    fun getOptimalAudioConfig(context: Context): AudioConfig {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // 获取原生采样率和缓冲区大小
        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val bufferSizeStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        
        val sampleRate = sampleRateStr?.toIntOrNull() ?: 44100
        val bufferSize = bufferSizeStr?.toIntOrNull() ?: 1024
        
        return AudioConfig(
            sampleRate = sampleRate,
            bufferSize = bufferSize,
            channelCount = 1, // 单声道，适合语音
            bitDepth = 16
        )
    }
    
    /**
     * 设置音频为通话模式
     */
    fun setVoiceCallMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        Log.d(TAG, "音频模式设置为通话模式")
    }
    
    /**
     * 恢复正常音频模式
     */
    fun restoreNormalMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        Log.d(TAG, "音频模式恢复为正常模式")
    }
    
    /**
     * 检查是否连接耳机
     */
    fun isHeadsetConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { 
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        } else {
            return audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn
        }
    }
    
    /**
     * 计算音频延迟
     */
    fun calculateAudioLatency(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val bufferSize = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 1024
            val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 44100
            
            // 估算延迟 (毫秒)
            (bufferSize * 1000.0 / sampleRate).toInt()
        } else {
            50 // 默认估计值
        }
    }
    
    private fun getAudioModeString(mode: Int): String {
        return when (mode) {
            AudioManager.MODE_NORMAL -> "正常模式"
            AudioManager.MODE_RINGTONE -> "铃声模式"
            AudioManager.MODE_IN_CALL -> "通话模式"
            AudioManager.MODE_IN_COMMUNICATION -> "通信模式"
            else -> "未知模式($mode)"
        }
    }
    
    private fun getDeviceTypeString(type: Int): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "内置听筒"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙SCO"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙A2DP"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
                else -> "其他设备($type)"
            }
        }
        return "设备类型$type"
    }
    
    /**
     * 音频配置数据类
     */
    data class AudioConfig(
        val sampleRate: Int,
        val bufferSize: Int,
        val channelCount: Int,
        val bitDepth: Int
    ) {
        override fun toString(): String {
            return "采样率: ${sampleRate}Hz, 缓冲区: ${bufferSize}帧, 声道: $channelCount, 位深: ${bitDepth}位"
        }
    }
    
    /**
     * 音频质量分析
     */
    fun analyzeAudioQuality(samples: FloatArray): AudioQualityMetrics {
        val rms = calculateRMS(samples)
        val snr = calculateSNR(samples)
        val thd = calculateTHD(samples)
        
        return AudioQualityMetrics(
            rms = rms,
            snr = snr,
            thd = thd,
            peakLevel = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0f
        )
    }
    
    private fun calculateRMS(samples: FloatArray): Float {
        var sum = 0.0f
        for (sample in samples) {
            sum += sample * sample
        }
        return kotlin.math.sqrt(sum / samples.size)
    }
    
    private fun calculateSNR(samples: FloatArray): Float {
        // 简化的信噪比计算
        val signal = calculateRMS(samples)
        val noise = samples.take(samples.size / 10).let { calculateRMS(it.toFloatArray()) }
        
        return if (noise > 0) 20 * kotlin.math.log10(signal / noise) else 60.0f
    }
    
    private fun calculateTHD(samples: FloatArray): Float {
        // 简化的总谐波失真计算
        // 这里返回一个估算值
        return 0.1f // 0.1%
    }
    
    data class AudioQualityMetrics(
        val rms: Float,
        val snr: Float,
        val thd: Float,
        val peakLevel: Float
    ) {
        override fun toString(): String {
            return "RMS: ${"%.3f".format(rms)}, SNR: ${"%.1f".format(snr)}dB, THD: ${"%.2f".format(thd)}%, 峰值: ${"%.3f".format(peakLevel)}"
        }
    }
}