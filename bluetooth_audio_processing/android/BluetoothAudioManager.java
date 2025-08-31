package com.example.bluetoothaudio;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android蓝牙音频管理器
 * 处理蓝牙耳机的音频捕获、处理和播放
 */
public class BluetoothAudioManager {
    private static final String TAG = "BluetoothAudioManager";
    
    // 音频参数
    private static final int SAMPLE_RATE = 16000; // HFP通常使用16kHz
    private static final int CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    private Context context;
    private AudioManager audioManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHeadset bluetoothHeadset;
    private BluetoothA2dp bluetoothA2dp;
    private BluetoothDevice connectedDevice;
    
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    private ExecutorService executorService;
    
    // 音频缓冲区
    private int bufferSize;
    private byte[] audioBuffer;
    
    public BluetoothAudioManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.executorService = Executors.newCachedThreadPool();
        
        initializeAudio();
        setupBluetoothProfiles();
        registerReceivers();
    }
    
    /**
     * 初始化音频组件
     */
    private void initializeAudio() {
        // 计算最小缓冲区大小
        bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT
        );
        
        // 确保缓冲区大小足够
        if (bufferSize < 0) {
            bufferSize = SAMPLE_RATE * 2; // 1秒的数据
        }
        
        audioBuffer = new byte[bufferSize];
        
        Log.d(TAG, "音频缓冲区大小: " + bufferSize);
    }
    
    /**
     * 设置蓝牙配置文件
     */
    private void setupBluetoothProfiles() {
        // 获取蓝牙耳机配置文件（用于通话）
        bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.HEADSET) {
                    bluetoothHeadset = (BluetoothHeadset) proxy;
                    List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
                    if (!devices.isEmpty()) {
                        connectedDevice = devices.get(0);
                        Log.d(TAG, "已连接HFP设备: " + connectedDevice.getName());
                    }
                }
            }
            
            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.HEADSET) {
                    bluetoothHeadset = null;
                }
            }
        }, BluetoothProfile.HEADSET);
        
        // 获取A2DP配置文件（用于音乐播放）
        bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) {
                    bluetoothA2dp = (BluetoothA2dp) proxy;
                    List<BluetoothDevice> devices = bluetoothA2dp.getConnectedDevices();
                    if (!devices.isEmpty()) {
                        connectedDevice = devices.get(0);
                        Log.d(TAG, "已连接A2DP设备: " + connectedDevice.getName());
                        
                        // 检查编解码器
                        checkCodecSupport();
                    }
                }
            }
            
            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == BluetoothProfile.A2DP) {
                    bluetoothA2dp = null;
                }
            }
        }, BluetoothProfile.A2DP);
    }
    
    /**
     * 检查支持的编解码器
     */
    private void checkCodecSupport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && bluetoothA2dp != null && connectedDevice != null) {
            // 获取编解码器配置（需要系统权限）
            try {
                // 通过反射获取编解码器信息
                java.lang.reflect.Method getCodecStatus = bluetoothA2dp.getClass()
                    .getDeclaredMethod("getCodecStatus", BluetoothDevice.class);
                Object codecStatus = getCodecStatus.invoke(bluetoothA2dp, connectedDevice);
                Log.d(TAG, "编解码器状态: " + codecStatus);
            } catch (Exception e) {
                Log.e(TAG, "获取编解码器状态失败", e);
            }
        }
    }
    
    /**
     * 注册广播接收器
     */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        
        context.registerReceiver(bluetoothReceiver, filter);
    }
    
    /**
     * 蓝牙状态广播接收器
     */
    private BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "蓝牙设备已连接: " + device.getName());
                connectedDevice = device;
                
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "蓝牙设备已断开: " + device.getName());
                if (device.equals(connectedDevice)) {
                    connectedDevice = null;
                    stopAudioCapture();
                }
                
            } else if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                handleScoStateChange(state);
            }
        }
    };
    
    /**
     * 处理SCO状态变化
     */
    private void handleScoStateChange(int state) {
        switch (state) {
            case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                Log.d(TAG, "SCO音频已连接");
                // 可以开始音频捕获
                break;
                
            case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                Log.d(TAG, "SCO音频已断开");
                stopAudioCapture();
                break;
                
            case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                Log.d(TAG, "SCO音频连接中...");
                break;
        }
    }
    
    /**
     * 开始蓝牙音频捕获（用于通话/语音识别）
     */
    public void startBluetoothAudioCapture() {
        if (connectedDevice == null) {
            Log.e(TAG, "没有连接的蓝牙设备");
            return;
        }
        
        // 请求音频焦点
        requestAudioFocus();
        
        // 启动蓝牙SCO（用于双向音频）
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.startBluetoothSco();
        audioManager.setBluetoothScoOn(true);
        
        // 等待SCO连接后开始录音
        executorService.execute(() -> {
            try {
                Thread.sleep(1000); // 等待SCO连接
                startRecording();
            } catch (InterruptedException e) {
                Log.e(TAG, "等待SCO连接被中断", e);
            }
        });
    }
    
    /**
     * 开始录音
     */
    private void startRecording() {
        if (isRecording) {
            return;
        }
        
        try {
            // 创建AudioRecord实例
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // 使用通信音频源
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            );
            
            // 创建AudioTrack用于播放处理后的音频
            audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            
            audioRecord.startRecording();
            audioTrack.play();
            isRecording = true;
            
            // 开始音频处理循环
            executorService.execute(this::audioProcessingLoop);
            
            Log.d(TAG, "开始录音");
            
        } catch (SecurityException e) {
            Log.e(TAG, "没有录音权限", e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "音频参数无效", e);
        }
    }
    
    /**
     * 音频处理循环
     */
    private void audioProcessingLoop() {
        while (isRecording) {
            // 从蓝牙耳机读取音频
            int bytesRead = audioRecord.read(audioBuffer, 0, bufferSize);
            
            if (bytesRead > 0) {
                // 处理音频数据
                byte[] processedAudio = processAudio(audioBuffer, bytesRead);
                
                // 播放处理后的音频（可选）
                if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.write(processedAudio, 0, processedAudio.length);
                }
                
                // 这里可以将音频发送到语音识别服务
                sendToSpeechRecognition(processedAudio);
            }
        }
    }
    
    /**
     * 音频处理（降噪、增益等）
     */
    private byte[] processAudio(byte[] audioData, int length) {
        byte[] processed = new byte[length];
        
        // 转换为16位采样
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            
            // 应用增益
            sample = (short) (sample * 1.5);
            
            // 防止削波
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            
            // 转换回字节
            processed[i] = (byte) (sample & 0xFF);
            processed[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        
        return processed;
    }
    
    /**
     * 发送音频到语音识别服务
     */
    private void sendToSpeechRecognition(byte[] audioData) {
        // 这里实现发送到语音识别API
        // 例如：Google Speech API, 百度语音识别等
    }
    
    /**
     * 停止音频捕获
     */
    public void stopAudioCapture() {
        isRecording = false;
        
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
        
        // 停止蓝牙SCO
        audioManager.setBluetoothScoOn(false);
        audioManager.stopBluetoothSco();
        audioManager.setMode(AudioManager.MODE_NORMAL);
        
        Log.d(TAG, "停止音频捕获");
    }
    
    /**
     * 请求音频焦点
     */
    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
        }
    }
    
    /**
     * 获取已连接的蓝牙设备信息
     */
    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }
    
    /**
     * 检查是否有蓝牙耳机连接
     */
    public boolean isBluetoothHeadsetConnected() {
        if (bluetoothHeadset != null) {
            List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
            return !devices.isEmpty();
        }
        return false;
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        stopAudioCapture();
        
        if (bluetoothHeadset != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
        }
        
        if (bluetoothA2dp != null) {
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp);
        }
        
        context.unregisterReceiver(bluetoothReceiver);
        executorService.shutdown();
    }
}