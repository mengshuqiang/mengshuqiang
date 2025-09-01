# Android虚拟声卡音频处理系统

这是一个Android应用程序，实现了虚拟声卡功能，可以对微信语音输入和输出进行实时音频处理。

## 功能特性

### 🎵 虚拟音频设备
- 模拟音频输入/输出设备
- 支持实时音频流处理
- 低延迟音频传输
- 支持AAudio和OpenSL ES

### 🔧 音频处理功能
- **噪音抑制**: 智能去除背景噪音
- **语音增强**: 突出人声频段，提高清晰度
- **回声消除**: 消除通话回声
- **自动增益控制**: 自动调节音量
- **动态压缩**: 平衡音频动态范围
- **均衡器**: 可调节的频率响应

### 📱 微信集成
- 监听微信语音事件
- 拦截音频输入/输出
- 实时处理语音通话
- 支持语音消息处理

## 技术架构

### 应用层
- **MainActivity**: 主界面控制
- **VirtualAudioService**: 虚拟音频服务
- **AudioProcessingService**: 音频处理服务

### 音频处理层
- **AudioProcessor**: 核心音频处理算法
- **VirtualAudioDevice**: 虚拟音频设备抽象
- **NoiseReducer**: 噪音抑制器
- **VoiceEnhancer**: 语音增强器

### 微信集成层
- **WeChatAudioInterceptor**: 音频拦截器
- **WeChatAudioAccessibilityService**: 无障碍服务
- **XposedModule**: Xposed Hook模块（可选）

### 原生层 (C++)
- **VirtualAudioDriver**: 底层音频驱动
- **AudioProcessor**: 高性能音频处理
- **NoiseSupressor**: 实时噪音抑制

## 安装和使用

### 系统要求
- Android 6.0+ (API 23+)
- 音频录制权限
- 无障碍服务权限
- Root权限（可选，用于高级功能）

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd VirtualAudioCard
```

2. **编译应用**
```bash
./gradlew assembleDebug
```

3. **安装APK**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 使用方法

1. **授予权限**
   - 启动应用
   - 授予音频录制权限
   - 启用无障碍服务

2. **启动虚拟声卡**
   - 点击"启动虚拟声卡"
   - 确认音频设备状态

3. **开始音频处理**
   - 点击"开始处理"
   - 调整处理参数

4. **使用微信**
   - 正常使用微信语音功能
   - 系统会自动处理音频

## 实现方案

### 方案一：无障碍服务 (推荐)
- 使用AccessibilityService监听微信
- 通过AudioManager重定向音频
- 无需Root权限
- 兼容性好

### 方案二：Xposed Hook
- 直接Hook微信音频API
- 需要Root权限和Xposed框架
- 功能更强大
- 兼容性有限

### 方案三：系统级修改
- 修改AudioFlinger或音频HAL
- 需要系统级权限
- 功能最完整
- 实现复杂度高

## 音频处理流程

```
麦克风输入 → 噪音抑制 → 语音增强 → 回声消除 → AGC → 微信发送
微信接收 → 噪音抑制 → 语音清晰化 → 均衡器 → 音量控制 → 耳机输出
```

## 配置参数

### 噪音抑制
- 噪音门限: -40dB
- 抑制强度: 70%
- 频谱减法算法

### 语音增强
- 人声频段: 300Hz - 3400Hz
- 增强级别: 可调 (0-100%)
- 预加重滤波

### 动态压缩
- 阈值: -20dB
- 压缩比: 4:1
- 攻击时间: 10ms
- 释放时间: 100ms

## 注意事项

### 权限要求
- 某些功能需要Root权限
- 无障碍服务需要用户手动启用
- 音频权限必须授予

### 兼容性
- 不同Android版本可能有差异
- 某些设备厂商的定制系统可能不兼容
- 微信版本更新可能影响功能

### 性能考虑
- 实时音频处理消耗CPU资源
- 建议在性能较好的设备上使用
- 可根据设备性能调整处理参数

## 开发和调试

### 编译环境
- Android Studio Arctic Fox+
- NDK 25.0+
- CMake 3.22.1+
- Kotlin 1.9.20+

### 调试工具
- adb logcat查看日志
- Android Studio Profiler分析性能
- 音频分析工具验证效果

## 法律声明

本项目仅用于技术研究和学习目的。使用时请遵守相关法律法规和平台服务条款。

## 许可证

MIT License - 详见LICENSE文件

## 贡献

欢迎提交Issue和Pull Request来改进这个项目。