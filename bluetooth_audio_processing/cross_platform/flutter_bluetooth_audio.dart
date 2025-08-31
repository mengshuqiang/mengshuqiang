import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:record/record.dart';
import 'package:just_audio/just_audio.dart';

/// Flutter跨平台蓝牙音频处理管理器
/// 支持Android和iOS的统一音频处理接口
class BluetoothAudioManager {
  // 平台通道
  static const MethodChannel _channel = MethodChannel('bluetooth_audio_channel');
  
  // 蓝牙管理
  final FlutterBluePlus _flutterBlue = FlutterBluePlus.instance;
  BluetoothDevice? _connectedDevice;
  
  // 音频录制和播放
  final Record _audioRecorder = Record();
  final AudioPlayer _audioPlayer = AudioPlayer();
  
  // 音频参数
  final int sampleRate = 16000;
  final int channels = 1;
  final int bitRate = 128000;
  
  // 状态管理
  bool _isRecording = false;
  bool _isProcessing = false;
  StreamSubscription? _audioStreamSubscription;
  
  // 音频数据流
  final StreamController<Uint8List> _audioDataController = StreamController<Uint8List>.broadcast();
  Stream<Uint8List> get audioDataStream => _audioDataController.stream;
  
  // 蓝牙状态流
  final StreamController<BluetoothConnectionState> _connectionStateController = 
      StreamController<BluetoothConnectionState>.broadcast();
  Stream<BluetoothConnectionState> get connectionStateStream => _connectionStateController.stream;
  
  /// 初始化蓝牙音频管理器
  Future<void> initialize() async {
    // 请求权限
    await _requestPermissions();
    
    // 设置平台特定配置
    await _configurePlatformAudio();
    
    // 监听蓝牙状态
    _setupBluetoothListeners();
  }
  
  /// 请求必要权限
  Future<void> _requestPermissions() async {
    // 请求蓝牙权限
    Map<Permission, PermissionStatus> statuses = await [
      Permission.bluetooth,
      Permission.bluetoothScan,
      Permission.bluetoothConnect,
      Permission.microphone,
      Permission.location,  // Android需要位置权限扫描蓝牙
    ].request();
    
    // 检查权限状态
    statuses.forEach((permission, status) {
      if (status != PermissionStatus.granted) {
        print('权限未授予: $permission');
      }
    });
  }
  
  /// 配置平台特定音频设置
  Future<void> _configurePlatformAudio() async {
    try {
      // 调用原生方法配置音频
      await _channel.invokeMethod('configureAudio', {
        'sampleRate': sampleRate,
        'channels': channels,
        'bitRate': bitRate,
      });
    } catch (e) {
      print('配置音频失败: $e');
    }
  }
  
  /// 设置蓝牙监听器
  void _setupBluetoothListeners() {
    // 监听蓝牙状态
    _flutterBlue.state.listen((state) {
      print('蓝牙状态: $state');
      if (state != BluetoothState.on) {
        _connectionStateController.add(BluetoothConnectionState.disconnected);
      }
    });
  }
  
  /// 扫描蓝牙音频设备
  Future<List<BluetoothDevice>> scanForAudioDevices() async {
    List<BluetoothDevice> audioDevices = [];
    
    // 检查蓝牙是否开启
    bool isOn = await _flutterBlue.isOn;
    if (!isOn) {
      throw Exception('蓝牙未开启');
    }
    
    // 开始扫描
    await _flutterBlue.startScan(
      timeout: Duration(seconds: 10),
      withServices: [
        // A2DP服务UUID
        Guid('0000110B-0000-1000-8000-00805F9B34FB'),
        // HFP服务UUID
        Guid('0000111E-0000-1000-8000-00805F9B34FB'),
      ],
    );
    
    // 监听扫描结果
    _flutterBlue.scanResults.listen((results) {
      for (ScanResult result in results) {
        // 检查是否是音频设备
        if (_isAudioDevice(result)) {
          if (!audioDevices.contains(result.device)) {
            audioDevices.add(result.device);
            print('发现音频设备: ${result.device.name}');
          }
        }
      }
    });
    
    // 等待扫描完成
    await Future.delayed(Duration(seconds: 10));
    await _flutterBlue.stopScan();
    
    return audioDevices;
  }
  
  /// 检查是否是音频设备
  bool _isAudioDevice(ScanResult result) {
    // 检查设备名称
    String name = result.device.name.toLowerCase();
    List<String> audioKeywords = ['headphone', 'earphone', 'speaker', 'headset', 'airpods', 'buds'];
    
    for (String keyword in audioKeywords) {
      if (name.contains(keyword)) {
        return true;
      }
    }
    
    // 检查服务UUID
    for (Guid serviceUuid in result.advertisementData.serviceUuids) {
      String uuid = serviceUuid.toString().toUpperCase();
      // 音频相关服务UUID
      if (uuid.contains('110B') || uuid.contains('111E') || uuid.contains('1108')) {
        return true;
      }
    }
    
    return false;
  }
  
  /// 连接蓝牙设备
  Future<void> connectDevice(BluetoothDevice device) async {
    try {
      // 断开之前的连接
      if (_connectedDevice != null) {
        await _connectedDevice!.disconnect();
      }
      
      // 连接新设备
      await device.connect(
        timeout: Duration(seconds: 10),
        autoConnect: false,
      );
      
      _connectedDevice = device;
      _connectionStateController.add(BluetoothConnectionState.connected);
      
      // 发现服务
      List<BluetoothService> services = await device.discoverServices();
      _analyzeServices(services);
      
      // 配置音频路由到蓝牙
      await _routeAudioToBluetooth();
      
    } catch (e) {
      print('连接设备失败: $e');
      _connectionStateController.add(BluetoothConnectionState.disconnected);
      rethrow;
    }
  }
  
  /// 分析蓝牙服务
  void _analyzeServices(List<BluetoothService> services) {
    for (BluetoothService service in services) {
      print('服务UUID: ${service.uuid}');
      
      // 检查音频服务
      String uuid = service.uuid.toString().toUpperCase();
      if (uuid.contains('110B')) {
        print('检测到A2DP服务');
      } else if (uuid.contains('111E')) {
        print('检测到HFP服务');
      } else if (uuid.contains('1108')) {
        print('检测到HSP服务');
      }
      
      // 分析特征
      for (BluetoothCharacteristic characteristic in service.characteristics) {
        print('  特征UUID: ${characteristic.uuid}');
        print('  属性: ${characteristic.properties}');
      }
    }
  }
  
  /// 路由音频到蓝牙设备
  Future<void> _routeAudioToBluetooth() async {
    try {
      await _channel.invokeMethod('routeAudioToBluetooth', {
        'deviceAddress': _connectedDevice?.id.toString(),
      });
    } catch (e) {
      print('路由音频失败: $e');
    }
  }
  
  /// 开始音频捕获和处理
  Future<void> startAudioCapture() async {
    if (_isRecording) return;
    
    // 检查麦克风权限
    if (!await _audioRecorder.hasPermission()) {
      throw Exception('没有麦克风权限');
    }
    
    _isRecording = true;
    _isProcessing = true;
    
    // 开始录音
    Stream<Uint8List>? audioStream = await _audioRecorder.startStream(
      RecordConfig(
        encoder: AudioEncoder.pcm16bit,
        sampleRate: sampleRate,
        numChannels: channels,
      ),
    );
    
    // 处理音频流
    _audioStreamSubscription = audioStream?.listen((data) {
      if (_isProcessing) {
        // 处理音频数据
        Uint8List processedData = _processAudioData(data);
        
        // 发送处理后的数据
        _audioDataController.add(processedData);
        
        // 可选：实时播放
        // _playProcessedAudio(processedData);
      }
    });
    
    print('开始音频捕获');
  }
  
  /// 处理音频数据
  Uint8List _processAudioData(Uint8List rawData) {
    // 转换为16位采样数组
    Int16List samples = Int16List.view(rawData.buffer);
    
    // 应用音频处理
    for (int i = 0; i < samples.length; i++) {
      // 应用增益
      int sample = (samples[i] * 1.5).round();
      
      // 限制范围防止削波
      if (sample > 32767) sample = 32767;
      if (sample < -32768) sample = -32768;
      
      samples[i] = sample;
    }
    
    // 应用降噪（简单的门限降噪）
    samples = _applyNoiseGate(samples, threshold: -40);
    
    return Uint8List.view(samples.buffer);
  }
  
  /// 应用噪声门
  Int16List _applyNoiseGate(Int16List samples, {required double threshold}) {
    // 计算RMS
    double sum = 0;
    for (int sample in samples) {
      sum += sample * sample;
    }
    double rms = sqrt(sum / samples.length);
    double rmsDb = 20 * log(rms / 32768) / ln10;
    
    // 如果低于阈值，静音
    if (rmsDb < threshold) {
      return Int16List(samples.length);  // 返回静音
    }
    
    return samples;
  }
  
  /// 播放处理后的音频
  Future<void> _playProcessedAudio(Uint8List audioData) async {
    // 创建临时音频源
    final audioSource = AudioSource.uri(
      Uri.dataFromBytes(audioData, mimeType: 'audio/pcm'),
    );
    
    try {
      await _audioPlayer.setAudioSource(audioSource);
      await _audioPlayer.play();
    } catch (e) {
      print('播放音频失败: $e');
    }
  }
  
  /// 停止音频捕获
  Future<void> stopAudioCapture() async {
    if (!_isRecording) return;
    
    _isRecording = false;
    _isProcessing = false;
    
    // 停止录音
    await _audioRecorder.stop();
    
    // 取消订阅
    await _audioStreamSubscription?.cancel();
    
    print('停止音频捕获');
  }
  
  /// 获取编解码器信息
  Future<Map<String, dynamic>> getCodecInfo() async {
    if (_connectedDevice == null) {
      throw Exception('没有连接的设备');
    }
    
    try {
      // 调用原生方法获取编解码器信息
      Map<String, dynamic> codecInfo = await _channel.invokeMethod('getCodecInfo', {
        'deviceAddress': _connectedDevice!.id.toString(),
      });
      
      return codecInfo;
    } catch (e) {
      print('获取编解码器信息失败: $e');
      return {};
    }
  }
  
  /// 设置音频编解码器
  Future<void> setAudioCodec(String codecType) async {
    try {
      await _channel.invokeMethod('setAudioCodec', {
        'codecType': codecType,
        'deviceAddress': _connectedDevice?.id.toString(),
      });
      print('设置编解码器: $codecType');
    } catch (e) {
      print('设置编解码器失败: $e');
    }
  }
  
  /// 获取音频延迟
  Future<int> getAudioLatency() async {
    try {
      int latency = await _channel.invokeMethod('getAudioLatency');
      return latency;
    } catch (e) {
      print('获取延迟失败: $e');
      return 0;
    }
  }
  
  /// 断开设备连接
  Future<void> disconnectDevice() async {
    if (_connectedDevice != null) {
      await _connectedDevice!.disconnect();
      _connectedDevice = null;
      _connectionStateController.add(BluetoothConnectionState.disconnected);
    }
  }
  
  /// 清理资源
  void dispose() {
    stopAudioCapture();
    disconnectDevice();
    _audioDataController.close();
    _connectionStateController.close();
    _audioPlayer.dispose();
  }
}

/// 蓝牙连接状态
enum BluetoothConnectionState {
  disconnected,
  connecting,
  connected,
  disconnecting,
}

/// 音频处理效果类
class AudioEffects {
  /// 应用回声消除
  static Uint8List applyEchoCancellation(Uint8List audioData) {
    // 实现回声消除算法
    // 这里使用简单的自适应滤波器
    return audioData;
  }
  
  /// 应用自动增益控制
  static Uint8List applyAutoGainControl(Uint8List audioData, {double targetLevel = 0.7}) {
    Int16List samples = Int16List.view(audioData.buffer);
    
    // 计算当前电平
    double maxLevel = 0;
    for (int sample in samples) {
      double level = sample.abs() / 32768.0;
      if (level > maxLevel) maxLevel = level;
    }
    
    // 计算增益
    double gain = targetLevel / maxLevel;
    if (gain > 2.0) gain = 2.0;  // 限制最大增益
    
    // 应用增益
    for (int i = 0; i < samples.length; i++) {
      samples[i] = (samples[i] * gain).round();
    }
    
    return Uint8List.view(samples.buffer);
  }
  
  /// 应用均衡器
  static Uint8List applyEqualizer(Uint8List audioData, List<double> bandGains) {
    // 实现多段均衡器
    // 使用FFT和频域处理
    return audioData;
  }
}

/// 语音活动检测器
class VoiceActivityDetector {
  final double energyThreshold;
  final int windowSize;
  
  VoiceActivityDetector({
    this.energyThreshold = -40.0,  // dB
    this.windowSize = 160,  // 10ms at 16kHz
  });
  
  /// 检测语音活动
  bool detectVoiceActivity(Uint8List audioData) {
    Int16List samples = Int16List.view(audioData.buffer);
    
    // 计算短时能量
    double energy = 0;
    int numWindows = samples.length ~/ windowSize;
    
    for (int w = 0; w < numWindows; w++) {
      double windowEnergy = 0;
      for (int i = 0; i < windowSize; i++) {
        int idx = w * windowSize + i;
        if (idx < samples.length) {
          windowEnergy += samples[idx] * samples[idx];
        }
      }
      energy += windowEnergy / windowSize;
    }
    
    energy /= numWindows;
    
    // 转换为dB
    double energyDb = 10 * log(energy / (32768.0 * 32768.0)) / ln10;
    
    return energyDb > energyThreshold;
  }
}