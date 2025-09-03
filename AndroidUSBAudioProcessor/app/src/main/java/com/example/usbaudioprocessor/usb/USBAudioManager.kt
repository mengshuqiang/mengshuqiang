package com.example.usbaudioprocessor.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.AudioFormat
import android.util.Log

class USBAudioManager(
    private val context: Context,
    private val usbManager: UsbManager
) {
    
    private var connection: UsbDeviceConnection? = null
    private var audioInterface: UsbInterface? = null
    private var currentDevice: UsbDevice? = null
    
    companion object {
        private const val TAG = "USBAudioManager"
        private const val USB_CLASS_AUDIO = 1
        private const val USB_SUBCLASS_AUDIOCONTROL = 1
        private const val USB_SUBCLASS_AUDIOSTREAMING = 2
        private const val USB_SUBCLASS_MIDISTREAMING = 3
        
        // 默认音频参数
        private const val DEFAULT_SAMPLE_RATE = 48000
        private const val DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    data class DeviceInfo(
        val vendorId: Int,
        val productId: Int,
        val vendorName: String,
        val productName: String,
        val isAudioClass: Boolean,
        val inputChannels: Int,
        val outputChannels: Int,
        val supportedSampleRates: List<Int>
    )
    
    /**
     * 检查设备是否为音频设备
     */
    fun isAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }
    
    /**
     * 获取设备信息
     */
    fun getDeviceInfo(device: UsbDevice): DeviceInfo {
        var isAudioClass = false
        var inputChannels = 0
        var outputChannels = 0
        val supportedSampleRates = mutableListOf<Int>()
        
        // 分析USB接口
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            
            if (usbInterface.interfaceClass == USB_CLASS_AUDIO) {
                isAudioClass = true
                
                when (usbInterface.interfaceSubclass) {
                    USB_SUBCLASS_AUDIOCONTROL -> {
                        // 音频控制接口
                        Log.d(TAG, "发现音频控制接口")
                    }
                    USB_SUBCLASS_AUDIOSTREAMING -> {
                        // 音频流接口
                        Log.d(TAG, "发现音频流接口")
                        
                        // 检查端点以确定输入/输出通道
                        for (j in 0 until usbInterface.endpointCount) {
                            val endpoint = usbInterface.getEndpoint(j)
                            
                            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                                    inputChannels = maxOf(inputChannels, 2) // 假设立体声
                                } else {
                                    outputChannels = maxOf(outputChannels, 2)
                                }
                            }
                        }
                    }
                    USB_SUBCLASS_MIDISTREAMING -> {
                        Log.d(TAG, "发现MIDI流接口")
                    }
                }
            }
        }
        
        // 添加常见的采样率
        if (isAudioClass) {
            supportedSampleRates.addAll(listOf(44100, 48000, 88200, 96000, 192000))
        }
        
        return DeviceInfo(
            vendorId = device.vendorId,
            productId = device.productId,
            vendorName = device.manufacturerName ?: "Unknown",
            productName = device.productName ?: "Unknown",
            isAudioClass = isAudioClass,
            inputChannels = inputChannels,
            outputChannels = outputChannels,
            supportedSampleRates = supportedSampleRates
        )
    }
    
    /**
     * 连接到USB音频设备
     */
    fun connectToDevice(device: UsbDevice): Boolean {
        if (!isAudioDevice(device)) {
            Log.e(TAG, "设备不是音频设备")
            return false
        }
        
        // 断开之前的连接
        disconnect()
        
        // 打开设备连接
        connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "无法打开设备连接")
            return false
        }
        
        // 查找音频流接口
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            
            if (usbInterface.interfaceClass == USB_CLASS_AUDIO &&
                usbInterface.interfaceSubclass == USB_SUBCLASS_AUDIOSTREAMING) {
                
                // 声明接口
                if (connection?.claimInterface(usbInterface, true) == true) {
                    audioInterface = usbInterface
                    currentDevice = device
                    Log.d(TAG, "成功连接到音频接口")
                    
                    // 配置音频参数
                    configureAudioParameters()
                    
                    return true
                }
            }
        }
        
        Log.e(TAG, "无法声明音频接口")
        disconnect()
        return false
    }
    
    /**
     * 配置音频参数
     */
    private fun configureAudioParameters() {
        // 这里可以通过USB控制传输配置音频参数
        // 例如设置采样率、位深度等
        
        // 示例：设置采样率（实际实现需要根据USB音频类规范）
        val sampleRate = DEFAULT_SAMPLE_RATE
        Log.d(TAG, "配置采样率: $sampleRate Hz")
        
        // 注意：实际的USB控制传输需要根据设备的具体描述符来实现
        // 这里仅作为示例框架
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        audioInterface?.let {
            connection?.releaseInterface(it)
            audioInterface = null
        }
        
        connection?.close()
        connection = null
        currentDevice = null
        
        Log.d(TAG, "已断开USB音频设备连接")
    }
    
    /**
     * 获取音频格式配置
     */
    fun getAudioFormat(): Int {
        return DEFAULT_AUDIO_FORMAT
    }
    
    /**
     * 获取采样率
     */
    fun getSampleRate(): Int {
        return DEFAULT_SAMPLE_RATE
    }
    
    /**
     * 获取通道配置
     */
    fun getChannelConfig(): Int {
        return DEFAULT_CHANNEL_CONFIG
    }
    
    /**
     * 读取音频数据
     */
    fun readAudioData(buffer: ByteArray, timeout: Int = 100): Int {
        val endpoint = audioInterface?.let { findAudioEndpoint(it, UsbConstants.USB_DIR_IN) }
        
        return if (endpoint != null && connection != null) {
            connection!!.bulkTransfer(endpoint, buffer, buffer.size, timeout)
        } else {
            -1
        }
    }
    
    /**
     * 写入音频数据
     */
    fun writeAudioData(buffer: ByteArray, timeout: Int = 100): Int {
        val endpoint = audioInterface?.let { findAudioEndpoint(it, UsbConstants.USB_DIR_OUT) }
        
        return if (endpoint != null && connection != null) {
            connection!!.bulkTransfer(endpoint, buffer, buffer.size, timeout)
        } else {
            -1
        }
    }
    
    /**
     * 查找音频端点
     */
    private fun findAudioEndpoint(usbInterface: UsbInterface, direction: Int): android.hardware.usb.UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            
            // 查找同步或批量传输端点
            if ((endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                 endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) &&
                endpoint.direction == direction) {
                return endpoint
            }
        }
        return null
    }
    
    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean {
        return connection != null && audioInterface != null
    }
    
    /**
     * 获取当前连接的设备
     */
    fun getCurrentDevice(): UsbDevice? {
        return currentDevice
    }
}