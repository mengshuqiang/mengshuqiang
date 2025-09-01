package com.virtualaudio.card.wechat

import android.media.AudioRecord
import android.media.AudioTrack
import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed模块 - 用于Hook微信音频API
 * 注意：这需要Xposed框架和root权限
 */
class XposedModule : IXposedHookLoadPackage {
    
    companion object {
        private const val TAG = "XposedModule"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
    }
    
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam?.packageName != WECHAT_PACKAGE) return
        
        Log.d(TAG, "开始Hook微信音频API")
        
        try {
            hookAudioRecord(lpparam)
            hookAudioTrack(lpparam)
        } catch (e: Exception) {
            Log.e(TAG, "Hook失败", e)
        }
    }
    
    private fun hookAudioRecord(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook AudioRecord的read方法
        XposedHelpers.findAndHookMethod(
            AudioRecord::class.java,
            "read",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    // 在读取音频数据之前
                    Log.d(TAG, "Hook AudioRecord.read - before")
                }
                
                override fun afterHookedMethod(param: MethodHookParam?) {
                    // 在读取音频数据之后
                    param?.let { hookParam ->
                        val audioData = hookParam.args[0] as ByteArray
                        val length = hookParam.result as Int
                        
                        if (length > 0) {
                            // 处理录制的音频数据
                            processRecordedAudio(audioData, length)
                        }
                    }
                }
            }
        )
    }
    
    private fun hookAudioTrack(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook AudioTrack的write方法
        XposedHelpers.findAndHookMethod(
            AudioTrack::class.java,
            "write",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    // 在写入音频数据之前
                    param?.let { hookParam ->
                        val audioData = hookParam.args[0] as ByteArray
                        val length = hookParam.args[2] as Int
                        
                        // 处理要播放的音频数据
                        val processedData = processPlaybackAudio(audioData, length)
                        
                        // 替换原始数据
                        hookParam.args[0] = processedData
                    }
                }
            }
        )
    }
    
    private fun processRecordedAudio(audioData: ByteArray, length: Int) {
        // 处理录制的音频数据（麦克风 -> 微信）
        Log.d(TAG, "处理录制音频: ${length}字节")
        
        // 这里可以调用我们的音频处理器
        // 例如：降噪、变声等
        
        // 通知音频处理服务
        notifyAudioInput(audioData, length)
    }
    
    private fun processPlaybackAudio(audioData: ByteArray, length: Int): ByteArray {
        // 处理播放的音频数据（微信 -> 扬声器）
        Log.d(TAG, "处理播放音频: ${length}字节")
        
        // 这里可以调用我们的音频处理器
        // 例如：均衡器、3D音效等
        
        val processedData = audioData.copyOf()
        
        // 通知音频处理服务
        notifyAudioOutput(processedData, length)
        
        return processedData
    }
    
    private fun notifyAudioInput(data: ByteArray, length: Int) {
        // 通过广播或其他方式通知音频处理服务
        // 这里可以使用Intent或直接调用服务方法
    }
    
    private fun notifyAudioOutput(data: ByteArray, length: Int) {
        // 通知音频输出处理
    }
}

/**
 * 无需Root的替代方案 - 使用音频焦点和重定向
 */
class WeChatAudioRedirector(private val context: Context) {
    
    companion object {
        private const val TAG = "WeChatAudioRedirector"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var virtualAudioDevice: VirtualAudioDevice? = null
    
    fun setupAudioRedirection(): Boolean {
        return try {
            // 请求音频焦点
            requestAudioFocus()
            
            // 初始化虚拟音频设备
            virtualAudioDevice = VirtualAudioDevice()
            virtualAudioDevice?.initialize() ?: false
            
            // 设置音频路由策略
            setupAudioRoutingPolicy()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "设置音频重定向失败", e)
            false
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            { focusChange ->
                handleAudioFocusChange(focusChange)
            },
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.AUDIOFOCUS_GAIN
        )
        
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "获得音频焦点")
                virtualAudioDevice?.start()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "失去音频焦点")
                virtualAudioDevice?.stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "暂时失去音频焦点")
            }
        }
    }
    
    private fun setupAudioRoutingPolicy() {
        // 设置音频路由策略
        // 这里可以使用AudioManager的setMode和其他方法
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // 强制使用有线耳机（如果连接）
        if (audioManager.isWiredHeadsetOn) {
            audioManager.isSpeakerphoneOn = false
        }
    }
    
    fun release() {
        virtualAudioDevice?.release()
        audioManager.abandonAudioFocus(null)
    }
}