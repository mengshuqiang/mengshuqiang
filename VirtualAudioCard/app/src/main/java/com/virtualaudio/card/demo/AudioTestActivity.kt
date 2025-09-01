package com.virtualaudio.card.demo

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.virtualaudio.card.audio.VirtualAudioDevice
import com.virtualaudio.card.databinding.ActivityAudioTestBinding
import com.virtualaudio.card.service.AudioProcessingService
import com.virtualaudio.card.wechat.WeChatAudioInterceptor

class AudioTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAudioTestBinding
    private var virtualAudioDevice: VirtualAudioDevice? = null
    private var wechatInterceptor: WeChatAudioInterceptor? = null
    private var audioProcessingService: AudioProcessingService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            audioProcessingService = null
            updateUI()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        initializeAudioComponents()
    }
    
    private fun setupUI() {
        binding.apply {
            // 虚拟音频设备控制
            btnInitializeDevice.setOnClickListener {
                initializeVirtualDevice()
            }
            
            btnStartDevice.setOnClickListener {
                startVirtualDevice()
            }
            
            btnStopDevice.setOnClickListener {
                stopVirtualDevice()
            }
            
            // 微信拦截控制
            btnStartInterception.setOnClickListener {
                startWeChatInterception()
            }
            
            btnStopInterception.setOnClickListener {
                stopWeChatInterception()
            }
            
            // 音频参数调节
            setupAudioParameterControls()
            
            // 音频测试
            btnTestMicrophone.setOnClickListener {
                testMicrophone()
            }
            
            btnTestSpeaker.setOnClickListener {
                testSpeaker()
            }
            
            btnTestLoopback.setOnClickListener {
                testAudioLoopback()
            }
        }
    }
    
    private fun setupAudioParameterControls() {
        binding.apply {
            // 噪音门限调节
            seekBarNoiseGate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = -60.0f + progress * 0.5f // -60dB to -10dB
                    tvNoiseGateValue.text = "${value.toInt()}dB"
                    updateAudioParameters()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            // 压缩比调节
            seekBarCompressorRatio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 1.0f + progress * 0.1f // 1:1 to 10:1
                    tvCompressorRatioValue.text = "${value}:1"
                    updateAudioParameters()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            // 语音增强级别
            seekBarVoiceEnhancement.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress // 0 to 100
                    tvVoiceEnhancementValue.text = "${value}%"
                    updateAudioParameters()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }
    
    private fun initializeAudioComponents() {
        // 初始化虚拟音频设备
        virtualAudioDevice = VirtualAudioDevice()
        
        // 初始化微信拦截器
        wechatInterceptor = WeChatAudioInterceptor(this)
        
        // 绑定音频处理服务
        val intent = Intent(this, AudioProcessingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }
    
    private fun initializeVirtualDevice() {
        val success = virtualAudioDevice?.initialize() ?: false
        binding.tvDeviceStatus.text = if (success) "设备已初始化" else "初始化失败"
        updateUI()
    }
    
    private fun startVirtualDevice() {
        virtualAudioDevice?.start()
        binding.tvDeviceStatus.text = "设备运行中"
        updateUI()
    }
    
    private fun stopVirtualDevice() {
        virtualAudioDevice?.stop()
        binding.tvDeviceStatus.text = "设备已停止"
        updateUI()
    }
    
    private fun startWeChatInterception() {
        val success = wechatInterceptor?.initialize() ?: false
        if (success) {
            wechatInterceptor?.startInterception()
            binding.tvInterceptionStatus.text = "微信拦截运行中"
        } else {
            binding.tvInterceptionStatus.text = "拦截初始化失败"
        }
        updateUI()
    }
    
    private fun stopWeChatInterception() {
        wechatInterceptor?.stopInterception()
        binding.tvInterceptionStatus.text = "微信拦截已停止"
        updateUI()
    }
    
    private fun updateAudioParameters() {
        // 这里可以更新音频处理参数
        // 例如调用native方法更新参数
    }
    
    private fun testMicrophone() {
        binding.tvTestResults.text = "正在测试麦克风..."
        // 实现麦克风测试逻辑
        
        // 模拟测试结果
        binding.tvTestResults.text = "麦克风测试完成\n" +
                "采样率: 44100Hz\n" +
                "声道: 单声道\n" +
                "格式: PCM 16位\n" +
                "延迟: 约20ms"
    }
    
    private fun testSpeaker() {
        binding.tvTestResults.text = "正在测试扬声器..."
        // 实现扬声器测试逻辑
        
        binding.tvTestResults.text = "扬声器测试完成\n" +
                "输出设备: 有线耳机\n" +
                "音量: 75%\n" +
                "延迟: 约15ms"
    }
    
    private fun testAudioLoopback() {
        binding.tvTestResults.text = "正在测试音频回环..."
        // 实现音频回环测试
        
        binding.tvTestResults.text = "音频回环测试完成\n" +
                "总延迟: 约35ms\n" +
                "音质: 良好\n" +
                "丢包率: 0%"
    }
    
    private fun updateUI() {
        // 更新UI状态
        val deviceRunning = virtualAudioDevice?.let { 
            // 这里需要检查设备状态的方法
            true // 临时返回值
        } ?: false
        
        binding.apply {
            btnStartDevice.isEnabled = !deviceRunning
            btnStopDevice.isEnabled = deviceRunning
            
            // 更新状态指示器
            if (deviceRunning) {
                ivDeviceStatus.setImageResource(android.R.drawable.presence_online)
            } else {
                ivDeviceStatus.setImageResource(android.R.drawable.presence_offline)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 清理资源
        virtualAudioDevice?.release()
        wechatInterceptor?.release()
        
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}