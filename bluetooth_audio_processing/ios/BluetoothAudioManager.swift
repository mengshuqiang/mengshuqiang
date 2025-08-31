import AVFoundation
import CoreBluetooth
import CoreAudio
import MediaPlayer

/**
 * iOS蓝牙音频管理器
 * 处理蓝牙耳机的音频捕获、处理和播放
 */
class BluetoothAudioManager: NSObject {
    
    // MARK: - Properties
    
    private let audioEngine = AVAudioEngine()
    private let audioSession = AVAudioSession.sharedInstance()
    private var inputNode: AVAudioInputNode!
    private var outputNode: AVAudioOutputNode!
    
    // 音频处理节点
    private let mixerNode = AVAudioMixerNode()
    private let playerNode = AVAudioPlayerNode()
    private let reverbNode = AVAudioUnitReverb()
    private let eqNode = AVAudioUnitEQ(numberOfBands: 10)
    
    // 蓝牙管理
    private var centralManager: CBCentralManager!
    private var connectedPeripheral: CBPeripheral?
    private var audioRoute: AVAudioSession.RouteDescription?
    
    // 音频参数
    private let sampleRate: Double = 16000.0  // HFP采样率
    private let channels: AVAudioChannelCount = 1
    private var audioFormat: AVAudioFormat!
    
    // 状态标志
    private var isRecording = false
    private var isProcessing = false
    
    // 音频缓冲区
    private var audioBuffer: AVAudioPCMBuffer?
    private let bufferSize: AVAudioFrameCount = 4096
    
    // 回调
    var onAudioDataReceived: ((Data) -> Void)?
    var onBluetoothStateChanged: ((Bool) -> Void)?
    
    // MARK: - Initialization
    
    override init() {
        super.init()
        setupAudioSession()
        setupAudioEngine()
        setupBluetoothManager()
        registerNotifications()
    }
    
    // MARK: - Audio Session Setup
    
    private func setupAudioSession() {
        do {
            // 配置音频会话类别
            try audioSession.setCategory(
                .playAndRecord,
                mode: .voiceChat,  // 优化语音通信
                options: [.allowBluetooth, .allowBluetoothA2DP]
            )
            
            // 设置首选采样率
            try audioSession.setPreferredSampleRate(sampleRate)
            
            // 设置首选IO缓冲区持续时间（降低延迟）
            try audioSession.setPreferredIOBufferDuration(0.005) // 5ms
            
            // 激活音频会话
            try audioSession.setActive(true)
            
            // 获取当前音频路由
            audioRoute = audioSession.currentRoute
            checkBluetoothConnection()
            
            print("音频会话配置完成")
            print("采样率: \(audioSession.sampleRate)")
            print("IO缓冲区: \(audioSession.ioBufferDuration * 1000)ms")
            
        } catch {
            print("音频会话配置失败: \(error)")
        }
    }
    
    // MARK: - Audio Engine Setup
    
    private func setupAudioEngine() {
        inputNode = audioEngine.inputNode
        outputNode = audioEngine.outputNode
        
        // 创建音频格式
        audioFormat = AVAudioFormat(
            standardFormatWithSampleRate: sampleRate,
            channels: channels
        )
        
        // 连接音频节点
        audioEngine.attach(playerNode)
        audioEngine.attach(mixerNode)
        audioEngine.attach(reverbNode)
        audioEngine.attach(eqNode)
        
        // 设置混响参数
        reverbNode.loadFactoryPreset(.smallRoom)
        reverbNode.wetDryMix = 20
        
        // 设置均衡器
        setupEqualizer()
        
        // 创建音频处理链
        // 输入 -> 混音器 -> EQ -> 混响 -> 输出
        audioEngine.connect(inputNode, to: mixerNode, format: audioFormat)
        audioEngine.connect(mixerNode, to: eqNode, format: audioFormat)
        audioEngine.connect(eqNode, to: reverbNode, format: audioFormat)
        audioEngine.connect(reverbNode, to: outputNode, format: audioFormat)
        
        // 播放节点连接到混音器
        audioEngine.connect(playerNode, to: mixerNode, format: audioFormat)
    }
    
    private func setupEqualizer() {
        // 配置10段均衡器
        let frequencies: [Float] = [32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]
        
        for (index, frequency) in frequencies.enumerated() {
            let band = eqNode.bands[index]
            band.frequency = frequency
            band.bandwidth = 1.0
            band.gain = 0.0  // 初始增益为0
            band.bypass = false
            band.filterType = .parametric
        }
    }
    
    // MARK: - Bluetooth Management
    
    private func setupBluetoothManager() {
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    private func checkBluetoothConnection() {
        guard let audioRoute = audioRoute else { return }
        
        for output in audioRoute.outputs {
            print("输出设备: \(output.portName) - \(output.portType.rawValue)")
            
            if output.portType == .bluetoothA2DP ||
               output.portType == .bluetoothHFP ||
               output.portType == .bluetoothLE {
                print("检测到蓝牙音频设备: \(output.portName)")
                onBluetoothStateChanged?(true)
                
                // 获取蓝牙设备信息
                if let uid = output.uid {
                    print("设备UID: \(uid)")
                }
                
                // 检查支持的数据源
                if let dataSources = output.dataSources {
                    for dataSource in dataSources {
                        print("数据源: \(dataSource.dataSourceName)")
                    }
                }
            }
        }
        
        for input in audioRoute.inputs {
            print("输入设备: \(input.portName) - \(input.portType.rawValue)")
            
            if input.portType == .bluetoothHFP {
                print("检测到蓝牙HFP输入: \(input.portName)")
            }
        }
    }
    
    // MARK: - Audio Capture
    
    func startAudioCapture() {
        guard !isRecording else { return }
        
        do {
            // 准备音频引擎
            audioEngine.prepare()
            try audioEngine.start()
            
            isRecording = true
            isProcessing = true
            
            // 安装音频tap以捕获音频数据
            installAudioTap()
            
            print("开始音频捕获")
            
        } catch {
            print("启动音频引擎失败: \(error)")
        }
    }
    
    private func installAudioTap() {
        // 在输入节点上安装tap
        inputNode.installTap(
            onBus: 0,
            bufferSize: bufferSize,
            format: audioFormat
        ) { [weak self] buffer, time in
            self?.processAudioBuffer(buffer, time: time)
        }
        
        // 在混音器输出上安装tap（用于监听处理后的音频）
        mixerNode.installTap(
            onBus: 0,
            bufferSize: bufferSize,
            format: audioFormat
        ) { [weak self] buffer, time in
            self?.monitorProcessedAudio(buffer)
        }
    }
    
    private func processAudioBuffer(_ buffer: AVAudioPCMBuffer, time: AVAudioTime) {
        guard isProcessing else { return }
        
        // 获取音频数据
        let frameLength = Int(buffer.frameLength)
        let channels = Int(buffer.format.channelCount)
        
        guard let floatData = buffer.floatChannelData else { return }
        
        // 转换为Data对象
        var audioData = Data()
        
        for frame in 0..<frameLength {
            for channel in 0..<channels {
                let sample = floatData[channel][frame]
                
                // 应用音频处理
                let processedSample = processAudioSample(sample)
                
                // 转换为16位PCM
                let pcmSample = Int16(processedSample * Float(Int16.max))
                withUnsafeBytes(of: pcmSample) { bytes in
                    audioData.append(contentsOf: bytes)
                }
            }
        }
        
        // 回调处理后的音频数据
        onAudioDataReceived?(audioData)
        
        // 分析音频特征
        analyzeAudioFeatures(buffer)
    }
    
    private func processAudioSample(_ sample: Float) -> Float {
        var processed = sample
        
        // 应用增益
        processed *= 1.5
        
        // 应用压缩
        if abs(processed) > 0.8 {
            processed = processed > 0 ? 0.8 + (processed - 0.8) * 0.3 : -0.8 + (processed + 0.8) * 0.3
        }
        
        // 限制范围
        processed = max(-1.0, min(1.0, processed))
        
        return processed
    }
    
    private func analyzeAudioFeatures(_ buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData?[0] else { return }
        
        let frameLength = Int(buffer.frameLength)
        var sum: Float = 0
        var maxValue: Float = 0
        
        // 计算RMS和峰值
        for i in 0..<frameLength {
            let sample = channelData[i]
            sum += sample * sample
            maxValue = max(maxValue, abs(sample))
        }
        
        let rms = sqrt(sum / Float(frameLength))
        let rmsDB = 20 * log10(rms)
        let peakDB = 20 * log10(maxValue)
        
        // 语音活动检测（VAD）
        let isSpeech = rmsDB > -40
        
        if isSpeech {
            print("检测到语音活动 - RMS: \(rmsDB)dB, Peak: \(peakDB)dB")
        }
    }
    
    private func monitorProcessedAudio(_ buffer: AVAudioPCMBuffer) {
        // 监听处理后的音频，可用于实时反馈或录制
    }
    
    // MARK: - Audio Playback
    
    func playAudio(data: Data) {
        guard let pcmBuffer = dataToPCMBuffer(data: data) else { return }
        
        playerNode.scheduleBuffer(pcmBuffer, completionHandler: nil)
        
        if !playerNode.isPlaying {
            playerNode.play()
        }
    }
    
    private func dataToPCMBuffer(data: Data) -> AVAudioPCMBuffer? {
        let frameCount = data.count / 2  // 16位采样
        
        guard let buffer = AVAudioPCMBuffer(
            pcmFormat: audioFormat,
            frameCapacity: AVAudioFrameCount(frameCount)
        ) else { return nil }
        
        buffer.frameLength = AVAudioFrameCount(frameCount)
        
        // 转换数据到浮点格式
        data.withUnsafeBytes { bytes in
            let int16Pointer = bytes.bindMemory(to: Int16.self)
            guard let floatData = buffer.floatChannelData?[0] else { return }
            
            for i in 0..<frameCount {
                floatData[i] = Float(int16Pointer[i]) / Float(Int16.max)
            }
        }
        
        return buffer
    }
    
    // MARK: - Stop Audio
    
    func stopAudioCapture() {
        guard isRecording else { return }
        
        isRecording = false
        isProcessing = false
        
        // 移除音频taps
        inputNode.removeTap(onBus: 0)
        mixerNode.removeTap(onBus: 0)
        
        // 停止音频引擎
        audioEngine.stop()
        
        print("停止音频捕获")
    }
    
    // MARK: - Audio Effects
    
    func setReverbLevel(_ level: Float) {
        reverbNode.wetDryMix = level * 100
    }
    
    func setEQBand(index: Int, gain: Float) {
        guard index < eqNode.bands.count else { return }
        eqNode.bands[index].gain = gain
    }
    
    func enableNoiseReduction(_ enable: Bool) {
        // iOS内置降噪通过音频会话配置
        do {
            if enable {
                try audioSession.setCategory(
                    .playAndRecord,
                    mode: .voiceChat,
                    options: [.allowBluetooth, .defaultToSpeaker, .mixWithOthers]
                )
            }
        } catch {
            print("设置降噪失败: \(error)")
        }
    }
    
    // MARK: - Notifications
    
    private func registerNotifications() {
        // 音频路由变化通知
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(audioRouteChanged),
            name: AVAudioSession.routeChangeNotification,
            object: nil
        )
        
        // 音频中断通知
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(audioInterrupted),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
        
        // 媒体服务重置通知
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(mediaServicesReset),
            name: AVAudioSession.mediaServicesWereResetNotification,
            object: nil
        )
    }
    
    @objc private func audioRouteChanged(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSession.routeChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        
        audioRoute = audioSession.currentRoute
        
        switch reason {
        case .newDeviceAvailable:
            print("新音频设备可用")
            checkBluetoothConnection()
            
        case .oldDeviceUnavailable:
            print("音频设备不可用")
            if let previousRoute = userInfo[AVAudioSession.routeChangePreviousRouteKey] as? AVAudioSessionRouteDescription {
                print("断开设备: \(previousRoute.outputs.first?.portName ?? "Unknown")")
            }
            onBluetoothStateChanged?(false)
            
        case .categoryChange:
            print("音频类别改变")
            
        default:
            print("音频路由改变: \(reason)")
        }
    }
    
    @objc private func audioInterrupted(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSession.interruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }
        
        switch type {
        case .began:
            print("音频中断开始")
            if isRecording {
                audioEngine.pause()
            }
            
        case .ended:
            print("音频中断结束")
            if let optionsValue = userInfo[AVAudioSession.interruptionOptionKey] as? UInt {
                let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
                if options.contains(.shouldResume) {
                    do {
                        try audioEngine.start()
                    } catch {
                        print("恢复音频引擎失败: \(error)")
                    }
                }
            }
            
        @unknown default:
            break
        }
    }
    
    @objc private func mediaServicesReset() {
        print("媒体服务重置")
        // 重新配置音频会话和引擎
        setupAudioSession()
        setupAudioEngine()
    }
    
    // MARK: - Cleanup
    
    deinit {
        stopAudioCapture()
        NotificationCenter.default.removeObserver(self)
    }
}

// MARK: - CBCentralManagerDelegate

extension BluetoothAudioManager: CBCentralManagerDelegate {
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            print("蓝牙已开启")
            // 可以开始扫描蓝牙设备
            scanForBluetoothDevices()
            
        case .poweredOff:
            print("蓝牙已关闭")
            onBluetoothStateChanged?(false)
            
        case .unauthorized:
            print("蓝牙未授权")
            
        case .unsupported:
            print("设备不支持蓝牙")
            
        case .resetting:
            print("蓝牙重置中")
            
        case .unknown:
            print("蓝牙状态未知")
            
        @unknown default:
            break
        }
    }
    
    private func scanForBluetoothDevices() {
        // 扫描支持音频服务的蓝牙设备
        let audioServiceUUIDs = [
            CBUUID(string: "0000110B-0000-1000-8000-00805F9B34FB"), // A2DP
            CBUUID(string: "0000111E-0000-1000-8000-00805F9B34FB"), // HFP
            CBUUID(string: "00001108-0000-1000-8000-00805F9B34FB")  // HSP
        ]
        
        centralManager.scanForPeripherals(
            withServices: audioServiceUUIDs,
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
        )
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        print("发现蓝牙设备: \(peripheral.name ?? "Unknown") - RSSI: \(RSSI)")
        
        // 这里可以选择连接设备
        // central.connect(peripheral, options: nil)
    }
}