package com.example.bluetootha2dpsink;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * BluetoothA2dpSink 实现示例
 * 注意：BluetoothA2dpSink 是隐藏API，需要通过反射访问
 * 需要系统签名或Root权限才能正常工作
 */
public class BluetoothA2dpSinkDemo {
    private static final String TAG = "A2dpSinkDemo";
    
    // BluetoothA2dpSink的类名（隐藏API）
    private static final String A2DP_SINK_CLASS = "android.bluetooth.BluetoothA2dpSink";
    
    // A2DP Sink Profile ID (值为11)
    private static final int A2DP_SINK_PROFILE = 11;
    
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private Object mA2dpSinkProxy; // BluetoothA2dpSink实例
    private AudioManager mAudioManager;
    private AudioFocusRequest mFocusRequest;
    
    // 反射相关的Method对象
    private Method mConnectMethod;
    private Method mDisconnectMethod;
    private Method mGetConnectionStateMethod;
    private Method mGetConnectedDevicesMethod;
    private Method mSetPriorityMethod;
    
    public BluetoothA2dpSinkDemo(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
    
    /**
     * 初始化A2DP Sink服务
     */
    public void initializeA2dpSink() {
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            return;
        }
        
        if (!mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            return;
        }
        
        try {
            // 获取BluetoothA2dpSink类
            Class<?> a2dpSinkClass = Class.forName(A2DP_SINK_CLASS);
            
            // 准备反射方法
            prepareReflectionMethods(a2dpSinkClass);
            
            // 获取代理对象
            boolean success = getProfileProxy();
            if (success) {
                Log.d(TAG, "A2DP Sink initialization started");
            } else {
                Log.e(TAG, "Failed to get A2DP Sink proxy");
            }
            
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "BluetoothA2dpSink class not found", e);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing A2DP Sink", e);
        }
    }
    
    /**
     * 准备反射方法
     */
    private void prepareReflectionMethods(Class<?> a2dpSinkClass) throws NoSuchMethodException {
        // 获取connect方法
        mConnectMethod = a2dpSinkClass.getMethod("connect", BluetoothDevice.class);
        
        // 获取disconnect方法
        mDisconnectMethod = a2dpSinkClass.getMethod("disconnect", BluetoothDevice.class);
        
        // 获取getConnectionState方法
        mGetConnectionStateMethod = a2dpSinkClass.getMethod("getConnectionState", BluetoothDevice.class);
        
        // 获取getConnectedDevices方法
        mGetConnectedDevicesMethod = a2dpSinkClass.getMethod("getConnectedDevices");
        
        // 获取setPriority方法（某些版本可能是setConnectionPolicy）
        try {
            mSetPriorityMethod = a2dpSinkClass.getMethod("setPriority", 
                BluetoothDevice.class, int.class);
        } catch (NoSuchMethodException e) {
            // Android 10+使用setConnectionPolicy
            mSetPriorityMethod = a2dpSinkClass.getMethod("setConnectionPolicy", 
                BluetoothDevice.class, int.class);
        }
    }
    
    /**
     * 获取BluetoothA2dpSink代理对象
     */
    private boolean getProfileProxy() {
        try {
            // 创建ServiceListener
            BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    Log.d(TAG, "A2DP Sink service connected");
                    mA2dpSinkProxy = proxy;
                    
                    // 服务连接后的初始化
                    onA2dpSinkReady();
                }
                
                @Override
                public void onServiceDisconnected(int profile) {
                    Log.d(TAG, "A2DP Sink service disconnected");
                    mA2dpSinkProxy = null;
                }
            };
            
            // 使用反射调用getProfileProxy，传入A2DP_SINK profile
            Method getProfileProxyMethod = BluetoothAdapter.class.getMethod(
                "getProfileProxy", Context.class, BluetoothProfile.ServiceListener.class, int.class);
            
            Boolean result = (Boolean) getProfileProxyMethod.invoke(
                mBluetoothAdapter, mContext, listener, A2DP_SINK_PROFILE);
            
            return result != null && result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting profile proxy", e);
            return false;
        }
    }
    
    /**
     * A2DP Sink服务准备就绪后的处理
     */
    private void onA2dpSinkReady() {
        // 注册广播接收器
        registerReceivers();
        
        // 请求音频焦点
        requestAudioFocus();
        
        // 设置设备可被发现
        makeDiscoverable();
        
        // 获取已连接的设备
        getConnectedDevices();
    }
    
    /**
     * 连接到指定的蓝牙设备（作为A2DP Sink）
     */
    public boolean connectToDevice(BluetoothDevice device) {
        if (mA2dpSinkProxy == null || device == null) {
            Log.e(TAG, "A2DP Sink not ready or device is null");
            return false;
        }
        
        try {
            // 设置设备优先级为自动连接
            mSetPriorityMethod.invoke(mA2dpSinkProxy, device, 1000); // PRIORITY_AUTO_CONNECT
            
            // 连接设备
            Boolean result = (Boolean) mConnectMethod.invoke(mA2dpSinkProxy, device);
            Log.d(TAG, "Connect to device " + device.getAddress() + ": " + result);
            return result != null && result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error connecting to device", e);
            return false;
        }
    }
    
    /**
     * 断开与指定设备的连接
     */
    public boolean disconnectDevice(BluetoothDevice device) {
        if (mA2dpSinkProxy == null || device == null) {
            return false;
        }
        
        try {
            Boolean result = (Boolean) mDisconnectMethod.invoke(mA2dpSinkProxy, device);
            Log.d(TAG, "Disconnect from device " + device.getAddress() + ": " + result);
            return result != null && result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error disconnecting device", e);
            return false;
        }
    }
    
    /**
     * 获取设备连接状态
     */
    public int getConnectionState(BluetoothDevice device) {
        if (mA2dpSinkProxy == null || device == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        
        try {
            Integer state = (Integer) mGetConnectionStateMethod.invoke(mA2dpSinkProxy, device);
            return state != null ? state : BluetoothProfile.STATE_DISCONNECTED;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting connection state", e);
            return BluetoothProfile.STATE_DISCONNECTED;
        }
    }
    
    /**
     * 获取已连接的设备列表
     */
    @SuppressWarnings("unchecked")
    public List<BluetoothDevice> getConnectedDevices() {
        if (mA2dpSinkProxy == null) {
            return null;
        }
        
        try {
            List<BluetoothDevice> devices = (List<BluetoothDevice>) 
                mGetConnectedDevicesMethod.invoke(mA2dpSinkProxy);
            
            if (devices != null) {
                for (BluetoothDevice device : devices) {
                    Log.d(TAG, "Connected device: " + device.getName() + 
                        " (" + device.getAddress() + ")");
                }
            }
            return devices;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting connected devices", e);
            return null;
        }
    }
    
    /**
     * 请求音频焦点
     */
    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            
            mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        handleAudioFocusChange(focusChange);
                    }
                })
                .build();
            
            int result = mAudioManager.requestAudioFocus(mFocusRequest);
            Log.d(TAG, "Audio focus request result: " + result);
        } else {
            // Android 8.0以下版本
            int result = mAudioManager.requestAudioFocus(
                new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        handleAudioFocusChange(focusChange);
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            );
            Log.d(TAG, "Audio focus request result: " + result);
        }
    }
    
    /**
     * 处理音频焦点变化
     */
    private void handleAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "Audio focus gained");
                // 恢复播放
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                Log.d(TAG, "Audio focus lost");
                // 停止播放
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "Audio focus lost transient");
                // 暂停播放
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.d(TAG, "Audio focus lost transient can duck");
                // 降低音量
                break;
        }
    }
    
    /**
     * 使设备可被发现
     */
    private void makeDiscoverable() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(discoverableIntent);
    }
    
    /**
     * 注册广播接收器
     */
    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        
        // A2DP Sink连接状态变化
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        
        // 音频配置变化
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.AUDIO_CONFIG_CHANGED");
        
        // 播放状态变化
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED");
        
        // 蓝牙设备连接状态
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        
        mContext.registerReceiver(mA2dpSinkReceiver, filter);
    }
    
    /**
     * 广播接收器
     */
    private final BroadcastReceiver mA2dpSinkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if ("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                
                Log.d(TAG, "A2DP Sink connection state changed: " + state);
                if (device != null) {
                    Log.d(TAG, "Device: " + device.getName() + " (" + device.getAddress() + ")");
                }
                
                switch (state) {
                    case BluetoothProfile.STATE_CONNECTED:
                        onDeviceConnected(device);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        onDeviceDisconnected(device);
                        break;
                }
                
            } else if ("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED".equals(action)) {
                int state = intent.getIntExtra("android.bluetooth.a2dp-sink.extra.PLAYING_STATE", -1);
                Log.d(TAG, "Playing state changed: " + state);
                
            } else if ("android.bluetooth.a2dp-sink.profile.action.AUDIO_CONFIG_CHANGED".equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int sampleRate = intent.getIntExtra("android.bluetooth.a2dp-sink.extra.SAMPLE_RATE", 0);
                int channelCount = intent.getIntExtra("android.bluetooth.a2dp-sink.extra.CHANNEL_COUNT", 0);
                
                Log.d(TAG, "Audio config changed - Sample rate: " + sampleRate + 
                    ", Channels: " + channelCount);
            }
        }
    };
    
    /**
     * 设备连接成功回调
     */
    private void onDeviceConnected(BluetoothDevice device) {
        Log.d(TAG, "Device connected as audio source: " + device.getName());
        // 可以在这里开始音频播放处理
    }
    
    /**
     * 设备断开连接回调
     */
    private void onDeviceDisconnected(BluetoothDevice device) {
        Log.d(TAG, "Device disconnected: " + device.getName());
        // 停止音频播放处理
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            // 注销广播接收器
            mContext.unregisterReceiver(mA2dpSinkReceiver);
            
            // 释放音频焦点
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mFocusRequest);
            } else {
                mAudioManager.abandonAudioFocus(null);
            }
            
            // 关闭代理连接
            if (mA2dpSinkProxy != null && mBluetoothAdapter != null) {
                Method closeProxyMethod = BluetoothAdapter.class.getMethod(
                    "closeProfileProxy", int.class, BluetoothProfile.class);
                closeProxyMethod.invoke(mBluetoothAdapter, A2DP_SINK_PROFILE, mA2dpSinkProxy);
                mA2dpSinkProxy = null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }
}