package com.example.usbaudiocontroller.manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * USB设备管理器
 * 负责USB音频设备的检测、连接和管理
 */
class USBManager(private val context: Context) {
    
    companion object {
        private const val TAG = "USBManager"
        private const val ACTION_USB_PERMISSION = "com.example.usbaudiocontroller.USB_PERMISSION"
        
        // USB Audio Class codes
        private const val USB_CLASS_AUDIO = 0x01
        private const val USB_SUBCLASS_AUDIOCONTROL = 0x01
        private const val USB_SUBCLASS_AUDIOSTREAMING = 0x02
        private const val USB_SUBCLASS_MIDISTREAMING = 0x03
    }
    
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    // 连接状态
    private val _connectionState = MutableStateFlow(USBConnectionState.DISCONNECTED)
    val connectionState: StateFlow<USBConnectionState> = _connectionState.asStateFlow()
    
    // 当前连接的设备
    private val _currentDevice = MutableStateFlow<UsbDevice?>(null)
    val currentDevice: StateFlow<UsbDevice?> = _currentDevice.asStateFlow()
    
    // USB设备连接
    private var usbConnection: UsbDeviceConnection? = null
    
    // USB权限广播接收器
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            Log.d(TAG, "USB权限已授予: ${it.deviceName}")
                            connectToDevice(it)
                        }
                    } else {
                        Log.d(TAG, "USB权限被拒绝")
                        _connectionState.value = USBConnectionState.PERMISSION_DENIED
                    }
                }
            }
        }
    }
    
    // USB设备插拔监听
    private val usbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB设备已连接: ${it.deviceName}")
                        if (isAudioDevice(it)) {
                            checkAndRequestPermission(it)
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB设备已断开: ${it.deviceName}")
                        if (it == _currentDevice.value) {
                            disconnectDevice()
                        }
                    }
                }
            }
        }
    }
    
    init {
        registerReceivers()
        scanForAudioDevices()
    }
    
    /**
     * 注册广播接收器
     */
    private fun registerReceivers() {
        // 注册USB权限接收器
        val permissionFilter = IntentFilter(ACTION_USB_PERMISSION)
        context.registerReceiver(usbPermissionReceiver, permissionFilter)
        
        // 注册USB设备插拔接收器
        val deviceFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        context.registerReceiver(usbDeviceReceiver, deviceFilter)
    }
    
    /**
     * 扫描已连接的音频设备
     */
    fun scanForAudioDevices() {
        val deviceList = usbManager.deviceList
        Log.d(TAG, "扫描USB设备，共发现 ${deviceList.size} 个设备")
        
        for ((_, device) in deviceList) {
            if (isAudioDevice(device)) {
                Log.d(TAG, "发现USB音频设备: ${device.deviceName}")
                checkAndRequestPermission(device)
                return // 只处理第一个音频设备
            }
        }
        
        Log.d(TAG, "未发现USB音频设备")
        _connectionState.value = USBConnectionState.NO_DEVICE
    }
    
    /**
     * 判断是否为音频设备
     */
    private fun isAudioDevice(device: UsbDevice): Boolean {
        // 检查设备类
        if (device.deviceClass == USB_CLASS_AUDIO) {
            return true
        }
        
        // 检查接口类
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == USB_CLASS_AUDIO) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 获取音频设备信息
     */
    fun getAudioDeviceInfo(device: UsbDevice): AudioDeviceInfo {
        val info = AudioDeviceInfo(
            name = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            manufacturer = device.manufacturerName ?: "Unknown",
            product = device.productName ?: "Unknown",
            serialNumber = device.serialNumber ?: "Unknown"
        )
        
        // 分析音频接口
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == USB_CLASS_AUDIO) {
                when (usbInterface.interfaceSubclass) {
                    USB_SUBCLASS_AUDIOCONTROL -> info.hasAudioControl = true
                    USB_SUBCLASS_AUDIOSTREAMING -> info.hasAudioStreaming = true
                    USB_SUBCLASS_MIDISTREAMING -> info.hasMidiStreaming = true
                }
                
                // 分析端点
                for (j in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(j)
                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                        if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                            info.inputChannels++
                        } else {
                            info.outputChannels++
                        }
                    }
                }
            }
        }
        
        return info
    }
    
    /**
     * 检查并请求USB权限
     */
    private fun checkAndRequestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "已有USB权限，直接连接")
            connectToDevice(device)
        } else {
            Log.d(TAG, "请求USB权限")
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
            _connectionState.value = USBConnectionState.REQUESTING_PERMISSION
        }
    }
    
    /**
     * 连接到USB设备
     */
    private fun connectToDevice(device: UsbDevice) {
        _connectionState.value = USBConnectionState.CONNECTING
        
        try {
            // 打开设备连接
            usbConnection = usbManager.openDevice(device)
            
            if (usbConnection != null) {
                Log.d(TAG, "成功连接到USB设备: ${device.deviceName}")
                _currentDevice.value = device
                _connectionState.value = USBConnectionState.CONNECTED
                
                // 初始化音频接口
                initializeAudioInterface(device)
            } else {
                Log.e(TAG, "无法打开USB设备连接")
                _connectionState.value = USBConnectionState.CONNECTION_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "连接USB设备失败", e)
            _connectionState.value = USBConnectionState.CONNECTION_FAILED
        }
    }
    
    /**
     * 初始化音频接口
     */
    private fun initializeAudioInterface(device: UsbDevice) {
        // 查找音频流接口
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == USB_CLASS_AUDIO &&
                usbInterface.interfaceSubclass == USB_SUBCLASS_AUDIOSTREAMING) {
                
                // 声明接口
                val claimed = usbConnection?.claimInterface(usbInterface, true) ?: false
                if (claimed) {
                    Log.d(TAG, "成功声明音频接口 $i")
                    // 这里可以进一步配置音频参数
                    configureAudioParameters(usbInterface)
                } else {
                    Log.e(TAG, "无法声明音频接口 $i")
                }
            }
        }
    }
    
    /**
     * 配置音频参数
     */
    private fun configureAudioParameters(usbInterface: UsbInterface) {
        // 这里可以通过USB控制传输配置音频参数
        // 例如：采样率、位深度、声道数等
        
        // 示例：设置采样率为48kHz
        val sampleRate = 48000
        val requestType = UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT or 0x01
        val request = 0x01 // SET_CUR
        val value = sampleRate and 0xFFFF
        val index = (sampleRate shr 16) and 0xFFFF
        
        usbConnection?.controlTransfer(
            requestType,
            request,
            value,
            index,
            null,
            0,
            1000
        )
    }
    
    /**
     * 断开设备连接
     */
    fun disconnectDevice() {
        usbConnection?.close()
        usbConnection = null
        _currentDevice.value = null
        _connectionState.value = USBConnectionState.DISCONNECTED
        Log.d(TAG, "USB设备已断开连接")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnectDevice()
        try {
            context.unregisterReceiver(usbPermissionReceiver)
            context.unregisterReceiver(usbDeviceReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销接收器失败", e)
        }
    }
}

/**
 * USB连接状态
 */
enum class USBConnectionState {
    DISCONNECTED,           // 未连接
    NO_DEVICE,             // 没有设备
    REQUESTING_PERMISSION, // 请求权限中
    PERMISSION_DENIED,     // 权限被拒绝
    CONNECTING,            // 连接中
    CONNECTED,             // 已连接
    CONNECTION_FAILED      // 连接失败
}

/**
 * 音频设备信息
 */
data class AudioDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String,
    val product: String,
    val serialNumber: String,
    var hasAudioControl: Boolean = false,
    var hasAudioStreaming: Boolean = false,
    var hasMidiStreaming: Boolean = false,
    var inputChannels: Int = 0,
    var outputChannels: Int = 0
)