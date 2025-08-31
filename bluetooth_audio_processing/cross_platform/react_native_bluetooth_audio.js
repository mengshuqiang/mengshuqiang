import {
  NativeModules,
  NativeEventEmitter,
  Platform,
  PermissionsAndroid,
} from 'react-native';
import BleManager from 'react-native-ble-manager';
import Sound from 'react-native-sound';
import AudioRecord from 'react-native-audio-record';
import { Buffer } from 'buffer';

// 原生模块
const { BluetoothAudioModule } = NativeModules;
const bluetoothEventEmitter = new NativeEventEmitter(BluetoothAudioModule);

/**
 * React Native跨平台蓝牙音频处理管理器
 */
class BluetoothAudioManager {
  constructor() {
    this.bleManager = BleManager;
    this.connectedDevice = null;
    this.isRecording = false;
    this.audioBuffer = [];
    this.audioProcessor = new AudioProcessor();
    
    // 音频配置
    this.audioConfig = {
      sampleRate: 16000,
      channels: 1,
      bitsPerSample: 16,
      audioSource: Platform.select({
        ios: 'MIC',
        android: 'VOICE_COMMUNICATION',
      }),
    };
    
    // 事件监听器
    this.listeners = {};
    
    this.initialize();
  }
  
  /**
   * 初始化蓝牙音频管理器
   */
  async initialize() {
    // 请求权限
    await this.requestPermissions();
    
    // 初始化BLE管理器
    await BleManager.start({ showAlert: false });
    
    // 设置音频录制
    this.setupAudioRecording();
    
    // 注册事件监听
    this.registerEventListeners();
    
    console.log('蓝牙音频管理器初始化完成');
  }
  
  /**
   * 请求必要权限
   */
  async requestPermissions() {
    if (Platform.OS === 'android') {
      const permissions = [
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
      ];
      
      const granted = await PermissionsAndroid.requestMultiple(permissions);
      
      Object.keys(granted).forEach(permission => {
        if (granted[permission] !== PermissionsAndroid.RESULTS.GRANTED) {
          console.warn(`权限未授予: ${permission}`);
        }
      });
    }
    // iOS权限在Info.plist中配置
  }
  
  /**
   * 设置音频录制
   */
  setupAudioRecording() {
    AudioRecord.init(this.audioConfig);
    
    AudioRecord.on('data', (data) => {
      // 处理音频数据
      this.handleAudioData(data);
    });
  }
  
  /**
   * 注册事件监听器
   */
  registerEventListeners() {
    // 蓝牙状态变化
    this.listeners.stateChange = bluetoothEventEmitter.addListener(
      'BluetoothStateChanged',
      (state) => {
        console.log('蓝牙状态变化:', state);
        this.onBluetoothStateChange(state);
      }
    );
    
    // 设备连接状态
    this.listeners.connectionChange = bluetoothEventEmitter.addListener(
      'DeviceConnectionChanged',
      (device) => {
        console.log('设备连接状态变化:', device);
        this.onDeviceConnectionChange(device);
      }
    );
    
    // 音频路由变化
    this.listeners.audioRouteChange = bluetoothEventEmitter.addListener(
      'AudioRouteChanged',
      (route) => {
        console.log('音频路由变化:', route);
        this.onAudioRouteChange(route);
      }
    );
    
    // BLE事件
    BleManager.addListener(
      'BleManagerDiscoverPeripheral',
      this.handleDiscoverPeripheral.bind(this)
    );
    
    BleManager.addListener(
      'BleManagerStopScan',
      this.handleStopScan.bind(this)
    );
    
    BleManager.addListener(
      'BleManagerDisconnectPeripheral',
      this.handleDisconnectedPeripheral.bind(this)
    );
  }
  
  /**
   * 扫描蓝牙音频设备
   */
  async scanForAudioDevices(duration = 10) {
    console.log('开始扫描蓝牙音频设备...');
    
    const audioDevices = [];
    
    // 音频服务UUID
    const serviceUUIDs = [
      '0000110B-0000-1000-8000-00805F9B34FB', // A2DP
      '0000111E-0000-1000-8000-00805F9B34FB', // HFP
      '00001108-0000-1000-8000-00805F9B34FB', // HSP
    ];
    
    // 开始扫描
    await BleManager.scan(serviceUUIDs, duration, true);
    
    return new Promise((resolve) => {
      setTimeout(() => {
        BleManager.stopScan();
        resolve(this.discoveredDevices);
      }, duration * 1000);
    });
  }
  
  /**
   * 处理发现的设备
   */
  handleDiscoverPeripheral(peripheral) {
    console.log('发现设备:', peripheral.name || 'Unknown', peripheral.id);
    
    // 检查是否是音频设备
    if (this.isAudioDevice(peripheral)) {
      if (!this.discoveredDevices) {
        this.discoveredDevices = [];
      }
      
      // 避免重复
      const exists = this.discoveredDevices.find(d => d.id === peripheral.id);
      if (!exists) {
        this.discoveredDevices.push(peripheral);
      }
    }
  }
  
  /**
   * 检查是否是音频设备
   */
  isAudioDevice(peripheral) {
    const name = (peripheral.name || '').toLowerCase();
    const audioKeywords = ['headphone', 'earphone', 'speaker', 'headset', 'airpods', 'buds'];
    
    // 检查设备名称
    for (const keyword of audioKeywords) {
      if (name.includes(keyword)) {
        return true;
      }
    }
    
    // 检查广播的服务UUID
    if (peripheral.advertising && peripheral.advertising.serviceUUIDs) {
      const audioUUIDs = ['110B', '111E', '1108'];
      for (const uuid of peripheral.advertising.serviceUUIDs) {
        for (const audioUUID of audioUUIDs) {
          if (uuid.toUpperCase().includes(audioUUID)) {
            return true;
          }
        }
      }
    }
    
    return false;
  }
  
  /**
   * 连接蓝牙设备
   */
  async connectDevice(deviceId) {
    try {
      console.log('连接设备:', deviceId);
      
      // 连接设备
      await BleManager.connect(deviceId);
      
      // 获取设备信息
      const deviceInfo = await BleManager.retrieveServices(deviceId);
      console.log('设备信息:', deviceInfo);
      
      this.connectedDevice = {
        id: deviceId,
        info: deviceInfo,
      };
      
      // 分析服务和特征
      this.analyzeServices(deviceInfo);
      
      // 配置音频路由
      await this.configureAudioRoute(deviceId);
      
      return true;
    } catch (error) {
      console.error('连接设备失败:', error);
      throw error;
    }
  }
  
  /**
   * 分析蓝牙服务
   */
  analyzeServices(deviceInfo) {
    console.log('分析蓝牙服务...');
    
    if (deviceInfo.services) {
      deviceInfo.services.forEach(service => {
        console.log('服务UUID:', service);
        
        // 检查音频相关服务
        if (service.includes('110B')) {
          console.log('- A2DP音频分发服务');
        } else if (service.includes('111E')) {
          console.log('- HFP免提服务');
        } else if (service.includes('1108')) {
          console.log('- HSP耳机服务');
        }
      });
    }
    
    if (deviceInfo.characteristics) {
      deviceInfo.characteristics.forEach(char => {
        console.log('特征:', char.characteristic);
        console.log('- 服务:', char.service);
        console.log('- 属性:', char.properties);
      });
    }
  }
  
  /**
   * 配置音频路由到蓝牙
   */
  async configureAudioRoute(deviceId) {
    try {
      // 调用原生模块配置音频路由
      const result = await BluetoothAudioModule.configureAudioRoute({
        deviceId: deviceId,
        preferredCodec: 'AAC', // 首选编解码器
      });
      
      console.log('音频路由配置结果:', result);
      return result;
    } catch (error) {
      console.error('配置音频路由失败:', error);
    }
  }
  
  /**
   * 开始音频捕获
   */
  async startAudioCapture() {
    if (this.isRecording) {
      console.log('已经在录音中');
      return;
    }
    
    console.log('开始音频捕获...');
    
    this.isRecording = true;
    this.audioBuffer = [];
    
    // 开始录音
    AudioRecord.start();
    
    // 如果使用原生模块进行高级音频处理
    if (Platform.OS === 'android') {
      await this.startNativeAudioCapture();
    }
  }
  
  /**
   * 启动原生音频捕获（Android）
   */
  async startNativeAudioCapture() {
    try {
      await BluetoothAudioModule.startAudioCapture({
        sampleRate: this.audioConfig.sampleRate,
        channels: this.audioConfig.channels,
        encoding: 'PCM_16BIT',
        audioSource: 'VOICE_COMMUNICATION',
      });
    } catch (error) {
      console.error('启动原生音频捕获失败:', error);
    }
  }
  
  /**
   * 处理音频数据
   */
  handleAudioData(base64Data) {
    // 将base64转换为Buffer
    const buffer = Buffer.from(base64Data, 'base64');
    
    // 处理音频
    const processedBuffer = this.audioProcessor.process(buffer);
    
    // 添加到缓冲区
    this.audioBuffer.push(processedBuffer);
    
    // 实时处理（如语音识别）
    this.processRealtimeAudio(processedBuffer);
    
    // 可选：发送到蓝牙耳机
    this.sendAudioToBluetooth(processedBuffer);
  }
  
  /**
   * 实时音频处理
   */
  processRealtimeAudio(audioBuffer) {
    // 语音活动检测
    const hasVoice = this.audioProcessor.detectVoiceActivity(audioBuffer);
    
    if (hasVoice) {
      console.log('检测到语音活动');
      
      // 这里可以触发语音识别
      // this.triggerSpeechRecognition(audioBuffer);
    }
  }
  
  /**
   * 发送音频到蓝牙耳机
   */
  async sendAudioToBluetooth(audioBuffer) {
    if (!this.connectedDevice) {
      return;
    }
    
    try {
      // 通过原生模块发送音频
      await BluetoothAudioModule.sendAudioData({
        deviceId: this.connectedDevice.id,
        audioData: audioBuffer.toString('base64'),
      });
    } catch (error) {
      console.error('发送音频失败:', error);
    }
  }
  
  /**
   * 停止音频捕获
   */
  async stopAudioCapture() {
    if (!this.isRecording) {
      return;
    }
    
    console.log('停止音频捕获');
    
    this.isRecording = false;
    
    // 停止录音
    AudioRecord.stop();
    
    // 停止原生音频捕获
    if (Platform.OS === 'android') {
      await BluetoothAudioModule.stopAudioCapture();
    }
    
    // 处理缓冲的音频
    if (this.audioBuffer.length > 0) {
      const fullBuffer = Buffer.concat(this.audioBuffer);
      // 保存或处理完整音频
      this.saveAudioFile(fullBuffer);
    }
  }
  
  /**
   * 保存音频文件
   */
  saveAudioFile(audioBuffer) {
    // 实现音频文件保存逻辑
    console.log('保存音频文件, 大小:', audioBuffer.length);
  }
  
  /**
   * 播放音频
   */
  playAudio(audioData) {
    // 创建Sound实例
    const sound = new Sound(audioData, '', (error) => {
      if (error) {
        console.error('加载音频失败:', error);
        return;
      }
      
      // 播放音频
      sound.play((success) => {
        if (success) {
          console.log('音频播放完成');
        } else {
          console.error('音频播放失败');
        }
        
        // 释放资源
        sound.release();
      });
    });
  }
  
  /**
   * 获取编解码器信息
   */
  async getCodecInfo() {
    if (!this.connectedDevice) {
      throw new Error('没有连接的设备');
    }
    
    try {
      const codecInfo = await BluetoothAudioModule.getCodecInfo({
        deviceId: this.connectedDevice.id,
      });
      
      console.log('编解码器信息:', codecInfo);
      return codecInfo;
    } catch (error) {
      console.error('获取编解码器信息失败:', error);
      return null;
    }
  }
  
  /**
   * 设置音频编解码器
   */
  async setAudioCodec(codecType) {
    if (!this.connectedDevice) {
      throw new Error('没有连接的设备');
    }
    
    try {
      const result = await BluetoothAudioModule.setAudioCodec({
        deviceId: this.connectedDevice.id,
        codecType: codecType,
      });
      
      console.log('设置编解码器结果:', result);
      return result;
    } catch (error) {
      console.error('设置编解码器失败:', error);
      throw error;
    }
  }
  
  /**
   * 断开设备连接
   */
  async disconnectDevice() {
    if (!this.connectedDevice) {
      return;
    }
    
    try {
      await BleManager.disconnect(this.connectedDevice.id);
      this.connectedDevice = null;
      console.log('设备已断开连接');
    } catch (error) {
      console.error('断开连接失败:', error);
    }
  }
  
  /**
   * 清理资源
   */
  dispose() {
    // 停止音频捕获
    this.stopAudioCapture();
    
    // 断开设备
    this.disconnectDevice();
    
    // 移除事件监听
    Object.values(this.listeners).forEach(listener => {
      if (listener) {
        listener.remove();
      }
    });
  }
  
  // 事件处理方法
  onBluetoothStateChange(state) {
    // 处理蓝牙状态变化
  }
  
  onDeviceConnectionChange(device) {
    // 处理设备连接状态变化
  }
  
  onAudioRouteChange(route) {
    // 处理音频路由变化
  }
  
  handleStopScan() {
    console.log('扫描停止');
  }
  
  handleDisconnectedPeripheral(data) {
    console.log('设备断开连接:', data.peripheral);
    if (this.connectedDevice && this.connectedDevice.id === data.peripheral) {
      this.connectedDevice = null;
    }
  }
}

/**
 * 音频处理器类
 */
class AudioProcessor {
  constructor() {
    this.noiseThreshold = -40; // dB
    this.gainLevel = 1.5;
  }
  
  /**
   * 处理音频缓冲区
   */
  process(audioBuffer) {
    // 转换为16位采样数组
    const samples = new Int16Array(audioBuffer.buffer);
    
    // 应用处理
    for (let i = 0; i < samples.length; i++) {
      // 应用增益
      let sample = samples[i] * this.gainLevel;
      
      // 限制范围
      sample = Math.max(-32768, Math.min(32767, sample));
      
      samples[i] = sample;
    }
    
    // 应用降噪
    const denoisedSamples = this.applyNoiseReduction(samples);
    
    return Buffer.from(denoisedSamples.buffer);
  }
  
  /**
   * 应用降噪
   */
  applyNoiseReduction(samples) {
    // 简单的噪声门实现
    const rms = this.calculateRMS(samples);
    const rmsDb = 20 * Math.log10(rms / 32768);
    
    if (rmsDb < this.noiseThreshold) {
      // 静音处理
      return new Int16Array(samples.length);
    }
    
    return samples;
  }
  
  /**
   * 计算RMS值
   */
  calculateRMS(samples) {
    let sum = 0;
    for (let i = 0; i < samples.length; i++) {
      sum += samples[i] * samples[i];
    }
    return Math.sqrt(sum / samples.length);
  }
  
  /**
   * 语音活动检测
   */
  detectVoiceActivity(audioBuffer) {
    const samples = new Int16Array(audioBuffer.buffer);
    const rms = this.calculateRMS(samples);
    const rmsDb = 20 * Math.log10(rms / 32768);
    
    return rmsDb > this.noiseThreshold;
  }
}

export default BluetoothAudioManager;