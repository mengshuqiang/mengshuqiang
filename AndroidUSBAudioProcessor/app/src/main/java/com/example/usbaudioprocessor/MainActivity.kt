package com.example.usbaudioprocessor

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.usbaudioprocessor.audio.AudioProcessor
import com.example.usbaudioprocessor.audio.EffectType
import com.example.usbaudioprocessor.databinding.ActivityMainBinding
import com.example.usbaudioprocessor.usb.USBAudioManager
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbManager: UsbManager
    private lateinit var usbAudioManager: USBAudioManager
    private lateinit var audioProcessor: AudioProcessor
    
    private var isRecording = false
    private var isProcessing = false
    private var currentDevice: UsbDevice? = null
    
    companion object {
        private const val TAG = "MainActivity"
        private const val ACTION_USB_PERMISSION = "com.example.usbaudioprocessor.USB_PERMISSION"
    }
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB设备已连接: ${it.deviceName}")
                        checkAndRequestUsbPermission(it)
                    }
                }
                
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        Log.d(TAG, "USB设备已断开: ${it.deviceName}")
                        if (it == currentDevice) {
                            stopAudioProcessing()
                            currentDevice = null
                            updateUI()
                        }
                    }
                }
                
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                Log.d(TAG, "USB权限已授予: ${it.deviceName}")
                                connectToDevice(it)
                            }
                        } else {
                            Log.d(TAG, "USB权限被拒绝")
                            Toast.makeText(this@MainActivity, "USB权限被拒绝", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化USB管理器
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        usbAudioManager = USBAudioManager(this, usbManager)
        audioProcessor = AudioProcessor()
        
        setupUI()
        registerUsbReceiver()
        requestAudioPermission()
        scanForUsbDevices()
    }
    
    private fun setupUI() {
        // 录制/停止按钮
        binding.btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        // 音频效果选择
        binding.radioGroupEffects.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioNone -> audioProcessor.setEffect(EffectType.NONE)
                R.id.radioEcho -> audioProcessor.setEffect(EffectType.ECHO)
                R.id.radioReverb -> audioProcessor.setEffect(EffectType.REVERB)
                R.id.radioEqualizer -> audioProcessor.setEffect(EffectType.EQUALIZER)
                R.id.radioPitchShift -> audioProcessor.setEffect(EffectType.PITCH_SHIFT)
            }
        }
        
        // 音量控制
        binding.seekBarVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                audioProcessor.setVolume(progress / 100f)
                binding.tvVolumeValue.text = "$progress%"
            }
            
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // 均衡器控制（低音、中音、高音）
        binding.seekBarBass.setOnSeekBarChangeListener(createEqualizerListener(0))
        binding.seekBarMid.setOnSeekBarChangeListener(createEqualizerListener(1))
        binding.seekBarTreble.setOnSeekBarChangeListener(createEqualizerListener(2))
        
        updateUI()
    }
    
    private fun createEqualizerListener(band: Int) = object : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
            val gain = (progress - 50) / 5f // -10 to +10 dB
            audioProcessor.setEqualizerBand(band, gain)
            when (band) {
                0 -> binding.tvBassValue.text = "${gain.toInt()}dB"
                1 -> binding.tvMidValue.text = "${gain.toInt()}dB"
                2 -> binding.tvTrebleValue.text = "${gain.toInt()}dB"
            }
        }
        
        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
    }
    
    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        registerReceiver(usbReceiver, filter)
    }
    
    private fun requestAudioPermission() {
        Dexter.withContext(this)
            .withPermission(android.Manifest.permission.RECORD_AUDIO)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    Log.d(TAG, "音频权限已授予")
                }
                
                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    Toast.makeText(this@MainActivity, "需要音频权限才能使用此应用", Toast.LENGTH_LONG).show()
                }
                
                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            }).check()
    }
    
    private fun scanForUsbDevices() {
        val deviceList = usbManager.deviceList
        binding.tvDeviceStatus.text = "扫描到 ${deviceList.size} 个USB设备"
        
        for ((_, device) in deviceList) {
            if (usbAudioManager.isAudioDevice(device)) {
                Log.d(TAG, "发现USB音频设备: ${device.deviceName}")
                checkAndRequestUsbPermission(device)
                break
            }
        }
    }
    
    private fun checkAndRequestUsbPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            connectToDevice(device)
        } else {
            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }
    
    private fun connectToDevice(device: UsbDevice) {
        lifecycleScope.launch {
            try {
                val connected = withContext(Dispatchers.IO) {
                    usbAudioManager.connectToDevice(device)
                }
                
                if (connected) {
                    currentDevice = device
                    val info = usbAudioManager.getDeviceInfo(device)
                    binding.tvDeviceInfo.text = """
                        设备: ${device.deviceName}
                        厂商: ${info.vendorName}
                        产品: ${info.productName}
                        音频类: ${if (info.isAudioClass) "是" else "否"}
                        输入通道: ${info.inputChannels}
                        输出通道: ${info.outputChannels}
                    """.trimIndent()
                    
                    updateUI()
                    Toast.makeText(this@MainActivity, "已连接到USB音频设备", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "无法连接到USB音频设备", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "连接设备失败", e)
                Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startRecording() {
        if (currentDevice == null) {
            Toast.makeText(this, "请先连接USB音频设备", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    audioProcessor.startProcessing(
                        usbAudioManager.getAudioFormat(),
                        usbAudioManager.getSampleRate()
                    )
                }
                
                isRecording = true
                updateUI()
                binding.visualizer.startVisualizer()
                Toast.makeText(this@MainActivity, "开始音频处理", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "启动录制失败", e)
                Toast.makeText(this@MainActivity, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopRecording() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    audioProcessor.stopProcessing()
                }
                
                isRecording = false
                updateUI()
                binding.visualizer.stopVisualizer()
                Toast.makeText(this@MainActivity, "停止音频处理", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "停止录制失败", e)
            }
        }
    }
    
    private fun stopAudioProcessing() {
        if (isRecording) {
            stopRecording()
        }
        usbAudioManager.disconnect()
    }
    
    private fun updateUI() {
        runOnUiThread {
            binding.btnRecord.text = if (isRecording) "停止处理" else "开始处理"
            binding.btnRecord.isEnabled = currentDevice != null
            
            binding.tvDeviceStatus.text = if (currentDevice != null) {
                "USB音频设备已连接"
            } else {
                "等待USB音频设备连接..."
            }
            
            // 启用/禁用控制面板
            val controlsEnabled = currentDevice != null
            binding.radioGroupEffects.isEnabled = controlsEnabled
            binding.seekBarVolume.isEnabled = controlsEnabled
            binding.seekBarBass.isEnabled = controlsEnabled
            binding.seekBarMid.isEnabled = controlsEnabled
            binding.seekBarTreble.isEnabled = controlsEnabled
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAudioProcessing()
        unregisterReceiver(usbReceiver)
    }
}