// iOS音频捕获和处理示例
import AVFoundation
import Speech

class RealTimeTranslationService {
    private var audioEngine: AVAudioEngine!
    private var speechRecognizer: SFSpeechRecognizer!
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest!
    private var recognitionTask: SFSpeechRecognitionTask!
    private var audioSession: AVAudioSession!
    
    init() {
        setupAudioSession()
        setupAudioEngine()
    }
    
    // 配置音频会话
    private func setupAudioSession() {
        audioSession = AVAudioSession.sharedInstance()
        
        do {
            // 设置音频会话类别为播放和录制
            try audioSession.setCategory(.playAndRecord, 
                                       mode: .voiceChat, 
                                       options: [.defaultToSpeaker, .allowBluetooth])
            
            // 激活音频会话
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
            
            // 监听音频路由变化
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(audioRouteChanged),
                name: AVAudioSession.routeChangeNotification,
                object: audioSession
            )
            
        } catch {
            print("音频会话配置失败: \(error)")
        }
    }
    
    // 监听音频路由变化（耳机连接/断开）
    @objc private func audioRouteChanged(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        
        switch reason {
        case .newDeviceAvailable:
            // 新音频设备连接（如耳机）
            configureForHeadphones()
        case .oldDeviceUnavailable:
            // 音频设备断开
            configureForBuiltInSpeaker()
        default:
            break
        }
    }
    
    // 配置音频引擎
    private func setupAudioEngine() {
        audioEngine = AVAudioEngine()
        speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "zh-CN"))
    }
    
    // 开始实时翻译
    func startRealTimeTranslation() {
        // 1. 请求语音识别权限
        SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
            DispatchQueue.main.async {
                if authStatus == .authorized {
                    self?.startAudioCapture()
                }
            }
        }
    }
    
    private func startAudioCapture() {
        // 重置之前的任务
        if recognitionTask != nil {
            recognitionTask.cancel()
            recognitionTask = nil
        }
        
        // 创建识别请求
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        recognitionRequest.shouldReportPartialResults = true
        
        // 获取输入节点
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        // 安装音频tap来捕获音频
        inputNode.installTap(onBus: 0, 
                           bufferSize: 1024, 
                           format: recordingFormat) { [weak self] buffer, time in
            // 将音频缓冲区添加到识别请求
            self?.recognitionRequest.append(buffer)
            
            // 同时处理原始音频数据进行实时翻译
            self?.processAudioBuffer(buffer)
        }
        
        // 启动音频引擎
        audioEngine.prepare()
        try? audioEngine.start()
        
        // 开始语音识别
        recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { [weak self] result, error in
            if let result = result {
                let recognizedText = result.bestTranscription.formattedString
                // 进行翻译处理
                self?.translateAndSpeak(text: recognizedText)
            }
        }
    }
    
    private func processAudioBuffer(_ buffer: AVAudioPCMBuffer) {
        // 处理音频缓冲区数据
        guard let channelData = buffer.floatChannelData else { return }
        
        let channelDataValue = channelData.pointee
        let channelDataValueArray = stride(from: 0, 
                                         to: Int(buffer.frameLength), 
                                         by: buffer.stride).map { channelDataValue[$0] }
        
        // 这里可以对音频数据进行实时处理
        // 例如降噪、音量调节等
    }
    
    private func translateAndSpeak(text: String) {
        // 1. 调用翻译服务
        translateText(text) { [weak self] translatedText in
            // 2. 语音合成
            self?.synthesizeSpeech(translatedText)
        }
    }
    
    private func synthesizeSpeech(_ text: String) {
        let synthesizer = AVSpeechSynthesizer()
        let utterance = AVSpeechUtterance(string: text)
        
        // 配置语音参数
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US")
        utterance.rate = 0.5
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        // 播放合成语音
        synthesizer.speak(utterance)
    }
    
    // 配置耳机音频
    private func configureForHeadphones() {
        do {
            try audioSession.overrideOutputAudioPort(.none)
            // 音频将自动路由到耳机
        } catch {
            print("配置耳机音频失败: \(error)")
        }
    }
    
    // 配置内置扬声器
    private func configureForBuiltInSpeaker() {
        do {
            try audioSession.overrideOutputAudioPort(.speaker)
        } catch {
            print("配置扬声器失败: \(error)")
        }
    }
    
    // 停止音频捕获
    func stopAudioCapture() {
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        
        try? audioSession.setActive(false)
    }
    
    // 翻译文本的占位符方法
    private func translateText(_ text: String, completion: @escaping (String) -> Void) {
        // 这里调用翻译API（如Google Translate、百度翻译等）
        // 示例：
        DispatchQueue.global().async {
            // 模拟翻译处理
            let translatedText = "Translated: \(text)"
            DispatchQueue.main.async {
                completion(translatedText)
            }
        }
    }
}