# Android虚拟耳机 - 音频处理应用

这是一个Android应用程序，通过创建虚拟音频设备来拦截和处理微信等应用的音频输入输出，实现实时音频效果处理。

## 功能特性

### 核心功能
- **虚拟音频设备**: 创建虚拟耳机设备，让其他应用识别为音频输入/输出设备
- **实时音频处理**: 拦截麦克风输入和音频输出，进行实时处理
- **多种音频效果**: 
  - 回声 (Echo)
  - 混响 (Reverb)
  - 变调 (Pitch Shift)
  - 机器人音效 (Robot)
  - 失真 (Distortion)
  - 合唱 (Chorus)
  - 镶边 (Flanger)

### 技术实现

#### 1. 虚拟音频设备实现方案

**方案A: 使用Android AudioRecord/AudioTrack (已实现)**
- 通过AudioRecord捕获麦克风输入
- 使用AudioTrack播放处理后的音频
- 模拟蓝牙SCO设备进行音频路由

**方案B: 系统级实现 (需要Root)**
```bash
# 通过ALSA创建虚拟声卡
modprobe snd-aloop
# 创建虚拟音频设备节点
```

**方案C: Xposed框架 (需要Root)**
- Hook AudioPolicyService
- 拦截音频流创建
- 重定向音频数据

#### 2. 音频处理流程

```
麦克风输入 → AudioRecord → 音频处理器 → AudioTrack → 虚拟输出
                ↓                ↑
            微信语音输入    微信语音播放
```

## 项目结构

```
/workspace/
├── AndroidManifest.xml          # 应用清单文件
├── build.gradle                 # 项目构建配置
├── app/
│   ├── build.gradle            # 应用构建配置
│   └── src/main/
│       ├── java/com/virtualheadset/audioprocessor/
│       │   ├── MainActivity.kt           # 主界面
│       │   ├── service/
│       │   │   └── VirtualAudioService.kt   # 虚拟音频服务
│       │   ├── audio/
│       │   │   ├── AudioProcessor.kt        # 音频处理器
│       │   │   └── AudioRoutingManager.kt   # 音频路由管理
│       │   ├── viewmodel/
│       │   │   └── MainViewModel.kt         # 视图模型
│       │   └── ui/theme/
│       │       ├── Theme.kt                 # 主题配置
│       │       └── Type.kt                  # 字体配置
│       └── res/
│           ├── values/
│           │   └── strings.xml              # 字符串资源
│           └── xml/
│               └── accessibility_service_config.xml  # 无障碍服务配置
```

## 使用说明

### 环境要求
- Android 6.0 (API 26) 或更高版本
- 需要以下权限：
  - 录音权限 (RECORD_AUDIO)
  - 音频设置权限 (MODIFY_AUDIO_SETTINGS)
  - 蓝牙权限 (BLUETOOTH相关)
  - 通知权限 (POST_NOTIFICATIONS)

### 安装步骤

1. **构建应用**
```bash
./gradlew assembleDebug
```

2. **安装APK**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

3. **授予权限**
- 首次启动时授予所需权限
- 在系统设置中启用无障碍服务

4. **使用应用**
- 打开应用，点击"开始处理"
- 选择想要的音频效果
- 打开微信进行语音通话测试

### 高级配置

#### 获取系统级权限（可选）

如需完整的虚拟声卡功能，需要root权限：

```bash
# 1. 获取root权限
su

# 2. 安装为系统应用
mount -o remount,rw /system
cp app.apk /system/priv-app/
chmod 644 /system/priv-app/app.apk
reboot
```

## 技术细节

### 音频处理算法

1. **回声效果**
   - 使用循环缓冲区存储延迟样本
   - 混合原始信号和延迟信号

2. **混响效果**
   - 多个延迟线并行处理
   - 梳状滤波器实现

3. **变调效果**
   - 重采样方法改变音高
   - 保持时长不变

4. **机器人效果**
   - 环形调制技术
   - 载波频率调制

### 性能优化

- 使用协程进行异步处理
- 缓冲区大小优化
- 低延迟音频路径
- 前台服务保活

## 限制和注意事项

1. **系统限制**
   - 标准Android系统无法创建真正的系统级虚拟声卡
   - 需要root权限或系统签名才能完全实现
   - 部分功能依赖设备制造商的音频实现

2. **兼容性**
   - 不同Android版本的音频API有差异
   - 某些设备可能不支持特定的音频路由
   - 蓝牙音频可能有额外延迟

3. **法律声明**
   - 请遵守当地法律法规
   - 不要用于非法录音或窃听
   - 尊重他人隐私

## 后续优化方向

1. **功能增强**
   - 添加更多音频效果
   - 实现实时频谱显示
   - 支持音频录制和保存
   - 添加均衡器功能

2. **性能优化**
   - 使用NDK实现音频处理
   - 优化DSP算法
   - 减少音频延迟

3. **用户体验**
   - 添加效果预设
   - 支持自定义效果参数
   - 改进UI设计

## 开发者说明

### 编译环境
- Android Studio Arctic Fox或更高版本
- Kotlin 1.9.0
- Gradle 8.1.0
- Android SDK 34

### 调试技巧

1. **查看音频日志**
```bash
adb logcat | grep -i audio
```

2. **检查音频设备**
```bash
adb shell dumpsys media.audio_policy
```

3. **监控服务状态**
```bash
adb shell dumpsys activity services | grep VirtualAudioService
```

## 许可证

本项目仅供学习和研究使用。使用时请遵守相关法律法规。

## 联系方式

如有问题或建议，请提交Issue。