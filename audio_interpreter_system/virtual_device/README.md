# 虚拟音频设备配置指南

本目录包含创建和管理虚拟音频设备的工具和说明，用于实现系统级音频拦截。

## 什么是虚拟音频设备？

虚拟音频设备是软件层面的音频接口，可以：
- 拦截系统音频输出
- 重定向音频流
- 实现音频录制和处理
- 创建音频回环（Loopback）

## 工作原理

```
应用程序音频输出
        ↓
   虚拟音频设备（Sink/Output）
        ↓
   ┌────┴────┐
   ↓         ↓
真实扬声器  虚拟麦克风（Source/Input）
            ↓
        同声传译系统
            ↓
        语音识别→翻译→语音合成
```

## 各平台解决方案

### Linux (PulseAudio)

最灵活的解决方案，原生支持虚拟设备：

```bash
# 运行设置脚本
./virtual_audio_setup.sh create

# 或手动创建
pactl load-module module-null-sink sink_name=virtual_speaker
pactl load-module module-remap-source source_name=virtual_mic master=virtual_speaker.monitor
```

### Linux (ALSA)

使用snd-aloop内核模块：

```bash
# 加载loopback模块
sudo modprobe snd-aloop

# 配置在 ~/.asoundrc
```

### macOS

需要第三方软件：

1. **BlackHole** (推荐)
   - 免费开源
   - 支持2通道和16通道
   - 低延迟

2. **Soundflower**
   - 经典选择
   - 开源项目
   - 可能需要安全设置调整

3. **Loopback**
   - 商业软件
   - 功能最强大
   - 图形化配置

### Windows

1. **VB-Cable**
   - 免费版本可用
   - 简单易用
   - 单虚拟线缆

2. **Virtual Audio Cable**
   - 付费软件
   - 支持多条虚拟线缆
   - 更多配置选项

3. **立体声混音**
   - Windows内置（部分声卡）
   - 无需额外软件
   - 可能被禁用

## 使用示例

### 1. 捕获浏览器音频

```python
# Python示例
import pyaudio
import wave

# 选择虚拟麦克风设备
p = pyaudio.PyAudio()
for i in range(p.get_device_count()):
    info = p.get_device_info_by_index(i)
    if 'virtual' in info['name'].lower():
        device_index = i
        break

# 开始录制
stream = p.open(
    format=pyaudio.paInt16,
    channels=2,
    rate=44100,
    input=True,
    input_device_index=device_index,
    frames_per_buffer=1024
)
```

### 2. 实时音频处理

```javascript
// Web Audio API示例
navigator.mediaDevices.getDisplayMedia({
    video: false,
    audio: {
        echoCancellation: false,
        noiseSuppression: false,
        autoGainControl: false
    }
}).then(stream => {
    const audioContext = new AudioContext();
    const source = audioContext.createMediaStreamSource(stream);
    // 处理音频...
});
```

## 常见问题

### Q: 为什么需要虚拟音频设备？

A: 大多数操作系统不允许直接捕获其他应用的音频输出（出于安全和隐私考虑）。虚拟音频设备提供了一个合法的中间层。

### Q: 会影响正常音频播放吗？

A: 使用组合输出（Combined Output）或聚合设备（Aggregate Device）可以同时输出到真实扬声器和虚拟设备，不影响正常收听。

### Q: 延迟问题如何解决？

A: 
- 降低缓冲区大小
- 使用低延迟模式
- 选择合适的采样率
- 避免不必要的音频处理

### Q: 音质损失？

A: 虚拟设备本身不会造成音质损失，但要注意：
- 采样率匹配
- 位深度设置
- 避免多次重采样

## 安全注意事项

1. **隐私保护**: 音频拦截涉及隐私，确保合法合规使用
2. **系统权限**: 某些操作需要管理员权限
3. **应用兼容**: 部分应用可能检测并阻止虚拟音频设备
4. **资源占用**: 音频处理会消耗CPU和内存资源

## 故障排除

### Linux
```bash
# 检查PulseAudio状态
systemctl --user status pulseaudio

# 重启PulseAudio
pulseaudio -k && pulseaudio --start

# 查看音频模块
pactl list short modules
```

### macOS
```bash
# 重置Core Audio
sudo killall coreaudiod

# 检查音频设备
system_profiler SPAudioDataType
```

### Windows
```powershell
# 重启音频服务
net stop audiosrv && net start audiosrv

# 检查音频设备
Get-PnpDevice -Class AudioEndpoint
```