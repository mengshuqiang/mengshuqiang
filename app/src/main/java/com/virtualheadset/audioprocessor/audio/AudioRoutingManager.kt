package com.virtualheadset.audioprocessor.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 音频路由管理器
 * 负责管理音频输入输出设备的路由，实现虚拟音频设备功能
 */
class AudioRoutingManager(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // 音频设备状态
    private val _audioDeviceState = MutableStateFlow(AudioDeviceState())
    val audioDeviceState: StateFlow<AudioDeviceState> = _audioDeviceState
    
    // 虚拟音频设备配置
    private var virtualAudioDevice: VirtualAudioDevice? = null
    
    data class AudioDeviceState(
        val isVirtualDeviceActive: Boolean = false,
        val currentInputDevice: String = "默认麦克风",
        val currentOutputDevice: String = "默认扬声器",
        val availableInputDevices: List<String> = emptyList(),
        val availableOutputDevices: List<String> = emptyList()
    )
    
    data class VirtualAudioDevice(
        val name: String = "Virtual Headset",
        val type: Int = AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        val isActive: Boolean = false
    )
    
    init {
        updateAvailableDevices()
        registerAudioDeviceCallback()
    }
    
    /**
     * 创建并激活虚拟音频设备
     * 注意：这需要系统级权限或root权限
     */
    fun createVirtualAudioDevice(): Boolean {
        try {
            // 方案1：使用反射调用隐藏API（需要系统签名）
            // 这里演示概念，实际实现需要系统权限
            
            virtualAudioDevice = VirtualAudioDevice(isActive = true)
            
            // 模拟创建虚拟设备
            // 实际需要通过AudioPolicyService或ALSA创建
            
            _audioDeviceState.value = _audioDeviceState.value.copy(
                isVirtualDeviceActive = true,
                currentInputDevice = "Virtual Headset (Input)",
                currentOutputDevice = "Virtual Headset (Output)"
            )
            
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 停用虚拟音频设备
     */
    fun deactivateVirtualAudioDevice() {
        virtualAudioDevice = virtualAudioDevice?.copy(isActive = false)
        _audioDeviceState.value = _audioDeviceState.value.copy(
            isVirtualDeviceActive = false,
            currentInputDevice = "默认麦克风",
            currentOutputDevice = "默认扬声器"
        )
    }
    
    /**
     * 路由音频到虚拟设备
     */
    fun routeAudioToVirtualDevice() {
        if (!_audioDeviceState.value.isVirtualDeviceActive) {
            createVirtualAudioDevice()
        }
        
        // 设置音频模式为通信模式
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // 启用蓝牙SCO（模拟蓝牙耳机）
        if (audioManager.isBluetoothScoAvailableOffCall) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
        
        // 设置音频路由
        audioManager.isSpeakerphoneOn = false
        audioManager.isWiredHeadsetOn = true
    }
    
    /**
     * 恢复默认音频路由
     */
    fun restoreDefaultRouting() {
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.isSpeakerphoneOn = true
        
        deactivateVirtualAudioDevice()
    }
    
    /**
     * 获取可用的音频设备
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getAvailableAudioDevices(): Pair<List<AudioDeviceInfo>, List<AudioDeviceInfo>> {
        val inputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        return Pair(inputDevices, outputDevices)
    }
    
    /**
     * 更新可用设备列表
     */
    private fun updateAvailableDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val (inputs, outputs) = getAvailableAudioDevices()
            
            _audioDeviceState.value = _audioDeviceState.value.copy(
                availableInputDevices = inputs.map { getDeviceName(it) },
                availableOutputDevices = outputs.map { getDeviceName(it) }
            )
        }
    }
    
    /**
     * 获取设备名称
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getDeviceName(device: AudioDeviceInfo): String {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙耳机"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙音频"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB设备"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB耳机"
            else -> device.productName.toString()
        }
    }
    
    /**
     * 注册音频设备回调
     */
    private fun registerAudioDeviceCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(object : AudioManager.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                    updateAvailableDevices()
                }
                
                override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                    updateAvailableDevices()
                }
            }, null)
        }
    }
    
    /**
     * 设置音频焦点
     */
    fun requestAudioFocus(): Boolean {
        val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .build()
        } else {
            null
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    /**
     * 释放音频焦点
     */
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 需要保存之前的focusRequest对象
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
    
    /**
     * 检查是否有录音权限
     */
    fun hasRecordPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == 
               android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 设置音频参数
     */
    fun configureAudioSettings() {
        // 设置音频采样率
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        
        // 设置缓冲区大小
        val bufferSize = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        
        // 应用音频设置
        applyAudioSettings(sampleRate, bufferSize)
    }
    
    private fun applyAudioSettings(sampleRate: String?, bufferSize: String?) {
        // 应用音频配置
        // 这里可以根据设备能力调整音频参数
    }
}