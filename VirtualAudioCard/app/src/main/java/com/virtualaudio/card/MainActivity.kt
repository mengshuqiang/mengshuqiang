package com.virtualaudio.card

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.virtualaudio.card.databinding.ActivityMainBinding
import com.virtualaudio.card.service.VirtualAudioService
import com.virtualaudio.card.service.AudioProcessingService

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 1001
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.apply {
            // 启动虚拟音频服务
            btnStartVirtualAudio.setOnClickListener {
                startVirtualAudioService()
            }
            
            // 停止虚拟音频服务
            btnStopVirtualAudio.setOnClickListener {
                stopVirtualAudioService()
            }
            
            // 启动音频处理
            btnStartProcessing.setOnClickListener {
                startAudioProcessing()
            }
            
            // 停止音频处理
            btnStopProcessing.setOnClickListener {
                stopAudioProcessing()
            }
            
            // 打开无障碍设置
            btnAccessibilitySettings.setOnClickListener {
                openAccessibilitySettings()
            }
            
            // 测试音频
            btnTestAudio.setOnClickListener {
                testAudioSetup()
            }
        }
    }
    
    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun startVirtualAudioService() {
        val intent = Intent(this, VirtualAudioService::class.java)
        startForegroundService(intent)
        binding.tvStatus.text = "虚拟音频服务已启动"
    }
    
    private fun stopVirtualAudioService() {
        val intent = Intent(this, VirtualAudioService::class.java)
        stopService(intent)
        binding.tvStatus.text = "虚拟音频服务已停止"
    }
    
    private fun startAudioProcessing() {
        val intent = Intent(this, AudioProcessingService::class.java)
        startForegroundService(intent)
        binding.tvProcessingStatus.text = "音频处理已启动"
    }
    
    private fun stopAudioProcessing() {
        val intent = Intent(this, AudioProcessingService::class.java)
        stopService(intent)
        binding.tvProcessingStatus.text = "音频处理已停止"
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "请启用微信音频辅助服务", Toast.LENGTH_LONG).show()
    }
    
    private fun testAudioSetup() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val info = StringBuilder()
        
        info.append("音频模式: ${audioManager.mode}\n")
        info.append("扬声器状态: ${audioManager.isSpeakerphoneOn}\n")
        info.append("有线耳机: ${audioManager.isWiredHeadsetOn}\n")
        info.append("蓝牙A2DP: ${audioManager.isBluetoothA2dpOn}\n")
        
        binding.tvAudioInfo.text = info.toString()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要音频权限才能正常工作", Toast.LENGTH_LONG).show()
            }
        }
    }
}