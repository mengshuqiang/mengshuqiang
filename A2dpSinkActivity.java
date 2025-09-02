package com.example.bluetootha2dpsink;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A2DP Sink演示Activity
 * 展示如何使用BluetoothA2dpSink API
 */
public class A2dpSinkActivity extends Activity {
    private static final String TAG = "A2dpSinkActivity";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    
    private BluetoothA2dpSinkDemo mA2dpSinkDemo;
    private BluetoothAdapter mBluetoothAdapter;
    
    private TextView mStatusText;
    private ListView mDevicesList;
    private Button mEnableButton;
    private Button mScanButton;
    private Button mDisconnectButton;
    
    private ArrayAdapter<String> mDevicesAdapter;
    private List<BluetoothDevice> mPairedDevices = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 初始化UI（这里使用代码创建，实际项目中应使用XML布局）
        initializeUI();
        
        // 检查并请求权限
        checkPermissions();
        
        // 初始化蓝牙
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            showMessage("设备不支持蓝牙");
            finish();
            return;
        }
        
        // 初始化A2DP Sink
        mA2dpSinkDemo = new BluetoothA2dpSinkDemo(this);
    }
    
    /**
     * 检查和请求必要的权限
     */
    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        
        // 蓝牙基础权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) 
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) 
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        
        // Android 6.0+需要位置权限来扫描蓝牙设备
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        
        // Android 12+需要新的蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissions.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }
    
    /**
     * 初始化UI（简化版，实际应使用XML布局）
     */
    private void initializeUI() {
        // 这里应该使用setContentView(R.layout.activity_a2dp_sink)
        // 以下代码仅作演示
        
        mStatusText = new TextView(this);
        mStatusText.setText("A2DP Sink状态：未初始化");
        
        mEnableButton = new Button(this);
        mEnableButton.setText("启用A2DP Sink");
        mEnableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableA2dpSink();
            }
        });
        
        mScanButton = new Button(this);
        mScanButton.setText("扫描已配对设备");
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanPairedDevices();
            }
        });
        
        mDisconnectButton = new Button(this);
        mDisconnectButton.setText("断开所有连接");
        mDisconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnectAll();
            }
        });
        
        mDevicesList = new ListView(this);
        mDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mDevicesList.setAdapter(mDevicesAdapter);
        
        // 设置设备列表点击事件
        mDevicesList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < mPairedDevices.size()) {
                BluetoothDevice device = mPairedDevices.get(position);
                connectToDevice(device);
            }
        });
    }
    
    /**
     * 启用A2DP Sink
     */
    private void enableA2dpSink() {
        if (!mBluetoothAdapter.isEnabled()) {
            showMessage("请先开启蓝牙");
            // 请求开启蓝牙
            mBluetoothAdapter.enable();
            return;
        }
        
        mA2dpSinkDemo.initializeA2dpSink();
        updateStatus("A2DP Sink已启用，等待连接...");
        
        // 延迟扫描已配对设备
        mDevicesList.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanPairedDevices();
            }
        }, 1000);
    }
    
    /**
     * 扫描已配对的设备
     */
    private void scanPairedDevices() {
        if (!mBluetoothAdapter.isEnabled()) {
            showMessage("蓝牙未开启");
            return;
        }
        
        mPairedDevices.clear();
        mDevicesAdapter.clear();
        
        // 获取已配对设备
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevices.add(device);
                String deviceInfo = device.getName() + "\n" + device.getAddress();
                
                // 检查设备是否支持A2DP
                if (isA2dpDevice(device)) {
                    deviceInfo += " (A2DP)";
                }
                
                mDevicesAdapter.add(deviceInfo);
            }
            updateStatus("找到 " + pairedDevices.size() + " 个已配对设备");
        } else {
            updateStatus("没有找到已配对设备");
        }
        
        // 获取当前连接的设备
        List<BluetoothDevice> connectedDevices = mA2dpSinkDemo.getConnectedDevices();
        if (connectedDevices != null && !connectedDevices.isEmpty()) {
            updateStatus("已连接 " + connectedDevices.size() + " 个设备");
        }
    }
    
    /**
     * 检查设备是否支持A2DP
     */
    private boolean isA2dpDevice(BluetoothDevice device) {
        if (device == null) return false;
        
        // 获取设备类型
        int deviceClass = device.getBluetoothClass().getDeviceClass();
        
        // 检查是否是音频设备
        return (deviceClass == 0x240404 ||  // Headphones
                deviceClass == 0x240408 ||  // Headset
                deviceClass == 0x240418 ||  // Loudspeaker
                deviceClass == 0x240414);   // Car Audio
    }
    
    /**
     * 连接到指定设备
     */
    private void connectToDevice(BluetoothDevice device) {
        if (device == null) return;
        
        updateStatus("正在连接到 " + device.getName() + "...");
        
        boolean success = mA2dpSinkDemo.connectToDevice(device);
        if (success) {
            updateStatus("连接请求已发送");
        } else {
            updateStatus("连接失败");
        }
    }
    
    /**
     * 断开所有连接
     */
    private void disconnectAll() {
        List<BluetoothDevice> connectedDevices = mA2dpSinkDemo.getConnectedDevices();
        if (connectedDevices != null && !connectedDevices.isEmpty()) {
            for (BluetoothDevice device : connectedDevices) {
                mA2dpSinkDemo.disconnectDevice(device);
            }
            updateStatus("已断开所有连接");
        } else {
            updateStatus("没有活动连接");
        }
    }
    
    /**
     * 更新状态显示
     */
    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mStatusText != null) {
                    mStatusText.setText("状态：" + status);
                }
                Log.d(TAG, status);
            }
        });
    }
    
    /**
     * 显示消息
     */
    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                showMessage("权限已授予");
                enableA2dpSink();
            } else {
                showMessage("需要蓝牙权限才能使用此功能");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 清理资源
        if (mA2dpSinkDemo != null) {
            mA2dpSinkDemo.cleanup();
        }
    }
}