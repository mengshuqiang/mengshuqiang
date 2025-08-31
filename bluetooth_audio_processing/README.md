# 蓝牙耳机音频处理技术实现指南

本项目详细介绍Android和iOS平台上蓝牙耳机音频处理的具体实现方式。

## 项目结构

```
bluetooth_audio_processing/
├── android/                # Android实现
│   ├── AudioCapture/       # 音频捕获模块
│   ├── BluetoothManager/   # 蓝牙管理
│   └── AudioProcessor/     # 音频处理
├── ios/                    # iOS实现
│   ├── AudioEngine/        # 音频引擎
│   ├── BluetoothHandler/   # 蓝牙处理
│   └── AudioEffects/       # 音频效果
├── common/                 # 共享代码
│   ├── protocols/          # 蓝牙协议
│   └── codecs/            # 编解码器
└── docs/                   # 文档资料
```

## 核心技术栈

### Android
- AudioRecord/AudioTrack API
- Bluetooth A2DP/HFP/HSP
- AAudio/OpenSL ES
- MediaCodec

### iOS
- AVAudioEngine
- Core Bluetooth
- Core Audio
- AudioToolbox

## 蓝牙音频协议

### 主要协议
1. **A2DP** (Advanced Audio Distribution Profile) - 高质量立体声
2. **HFP** (Hands-Free Profile) - 免提通话
3. **HSP** (Headset Profile) - 基础耳机功能
4. **LE Audio** - 低功耗音频（蓝牙5.2+）

### 音频编解码器
- **SBC** - 标准编解码器
- **AAC** - 高质量编解码器
- **aptX/aptX HD** - 高通专有
- **LDAC** - 索尼高清音频
- **LC3** - LE Audio编解码器

## 快速开始

### Android
```bash
cd android
./gradlew build
```

### iOS
```bash
cd ios
pod install
open BluetoothAudio.xcworkspace
```