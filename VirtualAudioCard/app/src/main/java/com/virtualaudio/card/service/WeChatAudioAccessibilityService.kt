package com.virtualaudio.card.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WeChatAudioAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "WeChatAudioService"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
    }
    
    private var audioProcessingService: AudioProcessingService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // 这里可以获取AudioProcessingService的引用
            isServiceBound = true
            Log.d(TAG, "音频处理服务已连接")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            audioProcessingService = null
            Log.d(TAG, "音频处理服务已断开")
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "微信音频辅助服务已连接")
        
        // 绑定音频处理服务
        val intent = Intent(this, AudioProcessingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { handleAccessibilityEvent(it) }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "辅助服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
    
    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        // 只处理微信的事件
        if (event.packageName != WECHAT_PACKAGE) return
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChanged(event)
            }
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val className = event.className?.toString()
        Log.d(TAG, "窗口状态变化: $className")
        
        // 检测是否进入语音通话界面
        if (className?.contains("VoiceUI") == true || 
            className?.contains("VoipUI") == true) {
            Log.d(TAG, "检测到语音通话界面")
            enableVoiceProcessing()
        }
    }
    
    private fun handleViewClicked(event: AccessibilityEvent) {
        val source = event.source
        source?.let { node ->
            // 检查是否点击了语音按钮
            if (isVoiceButton(node)) {
                Log.d(TAG, "检测到语音按钮点击")
                prepareVoiceRecording()
            }
        }
    }
    
    private fun handleContentChanged(event: AccessibilityEvent) {
        // 监听内容变化，可能包含语音消息
        val source = event.source
        source?.let { node ->
            checkForVoiceMessages(node)
        }
    }
    
    private fun isVoiceButton(node: AccessibilityNodeInfo): Boolean {
        // 检查节点是否为语音按钮
        val contentDesc = node.contentDescription?.toString()
        val text = node.text?.toString()
        
        return contentDesc?.contains("语音") == true ||
               contentDesc?.contains("voice") == true ||
               text?.contains("按住说话") == true ||
               text?.contains("录音") == true
    }
    
    private fun checkForVoiceMessages(node: AccessibilityNodeInfo) {
        // 递归检查子节点，寻找语音消息
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let {
                val contentDesc = it.contentDescription?.toString()
                if (contentDesc?.contains("语音") == true || 
                    contentDesc?.contains("voice") == true) {
                    Log.d(TAG, "检测到语音消息: $contentDesc")
                    handleVoiceMessage(it)
                }
                checkForVoiceMessages(it)
                it.recycle()
            }
        }
    }
    
    private fun enableVoiceProcessing() {
        Log.d(TAG, "启用语音处理模式")
        // 这里可以调整音频处理参数，针对语音通话优化
    }
    
    private fun prepareVoiceRecording() {
        Log.d(TAG, "准备语音录制")
        // 可以在这里预处理录音参数
    }
    
    private fun handleVoiceMessage(node: AccessibilityNodeInfo) {
        Log.d(TAG, "处理语音消息")
        // 可以在这里触发语音消息的音频处理
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "微信音频监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听微信音频事件"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("微信音频监听中")
            .setContentText("正在监听微信语音事件")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }
}