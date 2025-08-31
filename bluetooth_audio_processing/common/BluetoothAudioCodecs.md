# 蓝牙音频编解码器详解

## 1. SBC (Sub-Band Codec)
**标准蓝牙编解码器**

### 特性
- 比特率: 128-320 kbps
- 采样率: 16/32/44.1/48 kHz
- 延迟: ~150-250ms
- 强制支持: 所有A2DP设备必须支持

### Android实现
```java
// Android中配置SBC编解码器
BluetoothCodecConfig codecConfig = new BluetoothCodecConfig(
    BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
    BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
    BluetoothCodecConfig.SAMPLE_RATE_44100,
    BluetoothCodecConfig.BITS_PER_SAMPLE_16,
    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
    0, 0, 0, 0  // 特定编解码器参数
);
```

### iOS实现
```swift
// iOS通过AVAudioSession自动协商编解码器
let audioSession = AVAudioSession.sharedInstance()
// iOS会自动选择最佳可用编解码器
```

## 2. AAC (Advanced Audio Coding)
**高质量编解码器**

### 特性
- 比特率: 128-320 kbps
- 采样率: 8-96 kHz
- 延迟: ~120-150ms
- 支持: iOS原生支持，Android 8.0+

### 实现示例
```java
// Android AAC编码
MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
MediaFormat format = MediaFormat.createAudioFormat(
    MediaFormat.MIMETYPE_AUDIO_AAC,
    44100,  // 采样率
    2       // 通道数
);
format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
```

## 3. aptX / aptX HD
**高通专有编解码器**

### aptX标准版
- 比特率: 352 kbps (固定)
- 采样率: 44.1/48 kHz
- 延迟: ~40ms
- 压缩比: 4:1

### aptX HD
- 比特率: 576 kbps
- 采样率: 48 kHz
- 位深: 24-bit
- 延迟: ~40ms

### Android实现
```java
// 检查aptX支持
public boolean isAptXSupported(BluetoothDevice device) {
    BluetoothCodecStatus codecStatus = getCodecStatus(device);
    for (BluetoothCodecConfig config : codecStatus.getCodecsSelectableCapabilities()) {
        if (config.getCodecType() == BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX) {
            return true;
        }
    }
    return false;
}
```

## 4. LDAC
**索尼高清音频编解码器**

### 特性
- 比特率: 330/660/990 kbps (自适应)
- 采样率: 44.1/48/88.2/96 kHz
- 位深: 16/24-bit
- 延迟: ~30ms

### 质量模式
```java
// LDAC质量优先级设置
public static final int LDAC_QUALITY_LOW = 0;      // 330kbps - 连接优先
public static final int LDAC_QUALITY_MID = 1;      // 660kbps - 标准
public static final int LDAC_QUALITY_HIGH = 2;     // 990kbps - 质量优先
public static final int LDAC_QUALITY_ABR = 3;      // 自适应比特率

// 设置LDAC质量
BluetoothCodecConfig ldacConfig = new BluetoothCodecConfig(
    BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
    BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST,
    BluetoothCodecConfig.SAMPLE_RATE_96000,
    BluetoothCodecConfig.BITS_PER_SAMPLE_24,
    BluetoothCodecConfig.CHANNEL_MODE_STEREO,
    1000 * LDAC_QUALITY_HIGH, 0, 0, 0
);
```

## 5. LC3 (Low Complexity Communication Codec)
**LE Audio新标准编解码器**

### 特性
- 比特率: 160-320 kbps
- 采样率: 8-48 kHz
- 延迟: 20ms以下
- 功耗: 比SBC低50%

### 实现框架
```c
// LC3编码器初始化
struct LC3_Encoder {
    int sample_rate;
    int frame_duration;  // 7.5ms or 10ms
    int bitrate;
};

void lc3_encode_frame(
    LC3_Encoder* encoder,
    const int16_t* pcm_input,
    uint8_t* encoded_output,
    int* output_size
) {
    // LC3编码实现
    // 1. 时频变换(MDCT)
    // 2. 频谱量化
    // 3. 算术编码
    // 4. 打包输出
}
```

## 6. 编解码器协商流程

### Android实现
```java
public class CodecNegotiation {
    
    // 获取设备支持的编解码器
    public List<BluetoothCodecConfig> getSupportedCodecs(BluetoothDevice device) {
        BluetoothA2dp a2dp = getA2dpProfile();
        BluetoothCodecStatus status = a2dp.getCodecStatus(device);
        return status.getCodecsSelectableCapabilities();
    }
    
    // 选择最佳编解码器
    public BluetoothCodecConfig selectBestCodec(List<BluetoothCodecConfig> codecs) {
        // 优先级: LDAC > aptX HD > aptX > AAC > SBC
        int[] priority = {
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD,
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX,
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC
        };
        
        for (int codecType : priority) {
            for (BluetoothCodecConfig codec : codecs) {
                if (codec.getCodecType() == codecType) {
                    return optimizeCodecConfig(codec);
                }
            }
        }
        return null;
    }
    
    // 优化编解码器配置
    private BluetoothCodecConfig optimizeCodecConfig(BluetoothCodecConfig codec) {
        // 选择最高采样率和位深
        int sampleRate = selectHighestSampleRate(codec);
        int bitsPerSample = selectHighestBitsPerSample(codec);
        
        return new BluetoothCodecConfig(
            codec.getCodecType(),
            BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST,
            sampleRate,
            bitsPerSample,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0
        );
    }
}
```

### iOS实现
```swift
class CodecManager {
    
    // iOS自动处理编解码器协商，但可以通过配置优化
    func configureForHighQualityAudio() {
        let session = AVAudioSession.sharedInstance()
        
        do {
            // 设置高质量音频模式
            try session.setMode(.default)
            try session.setCategory(.playback, options: [.allowBluetoothA2DP])
            
            // 设置首选采样率
            try session.setPreferredSampleRate(48000)
            
            // 激活会话
            try session.setActive(true)
            
        } catch {
            print("音频配置失败: \(error)")
        }
    }
    
    // 监听音频格式变化
    func observeAudioFormatChanges() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(audioFormatChanged),
            name: AVAudioSession.routeChangeNotification,
            object: nil
        )
    }
    
    @objc func audioFormatChanged() {
        let session = AVAudioSession.sharedInstance()
        print("当前采样率: \(session.sampleRate)")
        print("IO缓冲区: \(session.ioBufferDuration)")
        
        // 检查当前路由
        for output in session.currentRoute.outputs {
            print("输出设备: \(output.portName)")
            // iOS不直接暴露编解码器信息
        }
    }
}
```

## 7. 实时编解码器切换

### 动态切换策略
```java
public class DynamicCodecSwitcher {
    
    private int signalStrength;
    private int batteryLevel;
    private boolean isMoving;
    
    public BluetoothCodecConfig selectOptimalCodec() {
        // 根据环境条件选择编解码器
        
        if (batteryLevel < 20) {
            // 低电量，使用SBC节省功耗
            return createSBCConfig();
        }
        
        if (signalStrength < -80) {  // dBm
            // 信号弱，使用低比特率
            return createLowBitrateConfig();
        }
        
        if (isMoving) {
            // 移动中，使用自适应比特率
            return createAdaptiveConfig();
        }
        
        // 正常情况，使用最高质量
        return createHighQualityConfig();
    }
    
    private BluetoothCodecConfig createAdaptiveConfig() {
        // LDAC自适应模式
        return new BluetoothCodecConfig(
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
            BluetoothCodecConfig.CODEC_PRIORITY_DEFAULT,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            1000 * 3, 0, 0, 0  // ABR模式
        );
    }
}
```

## 8. 音频同步与延迟补偿

### 延迟测量与补偿
```swift
class AudioSyncManager {
    
    private var codecLatency: TimeInterval = 0
    private var transmissionLatency: TimeInterval = 0
    
    func measureLatency(codec: String) -> TimeInterval {
        switch codec {
        case "SBC":
            codecLatency = 0.200  // 200ms
        case "AAC":
            codecLatency = 0.140  // 140ms
        case "aptX":
            codecLatency = 0.040  // 40ms
        case "LDAC":
            codecLatency = 0.030  // 30ms
        default:
            codecLatency = 0.150
        }
        
        // 添加传输延迟
        transmissionLatency = measureTransmissionLatency()
        
        return codecLatency + transmissionLatency
    }
    
    func compensateAudioVideo(videoTimestamp: TimeInterval) -> TimeInterval {
        // 补偿音视频同步
        let totalLatency = codecLatency + transmissionLatency
        return videoTimestamp + totalLatency
    }
    
    private func measureTransmissionLatency() -> TimeInterval {
        // 实际测量方法：发送时间戳包并计算往返时间
        return 0.010  // 10ms示例值
    }
}
```

## 9. 故障排除

### 常见问题与解决方案

1. **音频断续**
   - 检查信号强度
   - 降低编解码器质量
   - 增加缓冲区大小

2. **延迟过高**
   - 切换到低延迟编解码器(aptX)
   - 减小音频缓冲区
   - 禁用音频后处理

3. **音质差**
   - 升级到高质量编解码器
   - 检查蓝牙干扰
   - 调整采样率和位深

4. **连接不稳定**
   - 使用自适应比特率
   - 实现重连机制
   - 监控蓝牙状态变化