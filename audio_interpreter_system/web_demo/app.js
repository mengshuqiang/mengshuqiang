// 同声传译音频拦截系统 - 核心JavaScript实现

class AudioInterpreterSystem {
    constructor() {
        this.audioContext = null;
        this.mediaStream = null;
        this.mediaRecorder = null;
        this.analyser = null;
        this.recognition = null;
        this.isCapturing = false;
        this.audioSource = 'microphone';
        this.audioChunks = [];
        
        this.init();
    }

    init() {
        this.setupEventListeners();
        this.setupSpeechRecognition();
        this.initAudioContext();
    }

    initAudioContext() {
        // 初始化Web Audio API上下文
        this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        this.analyser = this.audioContext.createAnalyser();
        this.analyser.fftSize = 2048;
    }

    setupEventListeners() {
        // 音频源选择
        document.querySelectorAll('.source-option').forEach(option => {
            option.addEventListener('click', (e) => {
                document.querySelectorAll('.source-option').forEach(o => o.classList.remove('active'));
                option.classList.add('active');
                this.audioSource = option.dataset.source;
                document.getElementById('audioSource').textContent = option.querySelector('h4').textContent;
            });
        });

        // 控制按钮
        document.getElementById('startBtn').addEventListener('click', () => this.startCapture());
        document.getElementById('stopBtn').addEventListener('click', () => this.stopCapture());
        document.getElementById('testBtn').addEventListener('click', () => this.playTestAudio());
    }

    setupSpeechRecognition() {
        // 设置Web Speech API语音识别
        if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
            const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
            this.recognition = new SpeechRecognition();
            this.recognition.continuous = true;
            this.recognition.interimResults = true;
            this.recognition.lang = document.getElementById('sourceLang').value;

            this.recognition.onresult = (event) => {
                let finalTranscript = '';
                let interimTranscript = '';

                for (let i = event.resultIndex; i < event.results.length; i++) {
                    const transcript = event.results[i][0].transcript;
                    if (event.results[i].isFinal) {
                        finalTranscript += transcript + ' ';
                    } else {
                        interimTranscript += transcript;
                    }
                }

                // 更新原文显示
                const originalText = document.getElementById('originalText');
                if (finalTranscript) {
                    originalText.innerHTML += `<p><strong>[${new Date().toLocaleTimeString()}]</strong> ${finalTranscript}</p>`;
                    // 触发翻译
                    this.translateText(finalTranscript);
                }
                if (interimTranscript) {
                    originalText.innerHTML += `<p style="color: #999;"><em>识别中: ${interimTranscript}</em></p>`;
                }
                originalText.scrollTop = originalText.scrollHeight;
            };

            this.recognition.onerror = (event) => {
                console.error('语音识别错误:', event.error);
                this.showError(`语音识别错误: ${event.error}`);
            };
        } else {
            this.showError('您的浏览器不支持语音识别API');
        }
    }

    async startCapture() {
        try {
            let constraints = {};
            
            // 根据选择的音频源设置不同的捕获方式
            switch(this.audioSource) {
                case 'microphone':
                    // 捕获麦克风音频
                    constraints = { audio: true };
                    this.mediaStream = await navigator.mediaDevices.getUserMedia(constraints);
                    break;
                    
                case 'system':
                    // 捕获系统音频（需要屏幕共享API）
                    // 注意：这会弹出屏幕选择对话框，用户需要选择"共享系统音频"
                    this.mediaStream = await navigator.mediaDevices.getDisplayMedia({
                        video: false,
                        audio: {
                            echoCancellation: false,
                            noiseSuppression: false,
                            autoGainControl: false
                        }
                    });
                    break;
                    
                case 'tab':
                    // 捕获标签页音频
                    // 需要使用Chrome扩展API或者通过屏幕共享选择特定标签
                    this.mediaStream = await navigator.mediaDevices.getDisplayMedia({
                        video: true, // 需要video来选择标签
                        audio: {
                            echoCancellation: false,
                            noiseSuppression: false,
                            autoGainControl: false
                        }
                    });
                    // 移除视频轨道，只保留音频
                    this.mediaStream.getVideoTracks().forEach(track => {
                        track.stop();
                        this.mediaStream.removeTrack(track);
                    });
                    break;
            }

            // 连接音频流到分析器
            const source = this.audioContext.createMediaStreamSource(this.mediaStream);
            source.connect(this.analyser);

            // 设置MediaRecorder录制音频
            this.setupMediaRecorder();

            // 开始语音识别
            if (this.recognition && this.audioSource === 'microphone') {
                this.recognition.lang = document.getElementById('sourceLang').value;
                this.recognition.start();
            }

            // 更新UI状态
            this.isCapturing = true;
            document.getElementById('startBtn').disabled = true;
            document.getElementById('stopBtn').disabled = false;
            document.getElementById('captureStatus').textContent = '正在捕获';
            document.getElementById('sampleRate').textContent = `${this.audioContext.sampleRate} Hz`;

            // 开始可视化
            this.startVisualization();

            // 开始音频级别监控
            this.startAudioLevelMonitoring();

        } catch (error) {
            console.error('捕获音频失败:', error);
            this.showError(`捕获音频失败: ${error.message}`);
        }
    }

    setupMediaRecorder() {
        // 设置MediaRecorder进行音频录制
        const options = {
            mimeType: 'audio/webm;codecs=opus'
        };

        // 检查浏览器支持的MIME类型
        if (!MediaRecorder.isTypeSupported(options.mimeType)) {
            options.mimeType = 'audio/webm';
            if (!MediaRecorder.isTypeSupported(options.mimeType)) {
                options.mimeType = 'audio/ogg;codecs=opus';
                if (!MediaRecorder.isTypeSupported(options.mimeType)) {
                    options.mimeType = '';
                }
            }
        }

        this.mediaRecorder = new MediaRecorder(this.mediaStream, options);

        this.mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                this.audioChunks.push(event.data);
                // 这里可以实时处理音频数据块
                this.processAudioChunk(event.data);
            }
        };

        this.mediaRecorder.onstart = () => {
            console.log('开始录制音频');
            this.audioChunks = [];
        };

        this.mediaRecorder.onstop = () => {
            console.log('停止录制音频');
            // 创建完整的音频Blob
            const audioBlob = new Blob(this.audioChunks, { type: 'audio/webm' });
            // 这里可以保存或处理完整的音频
            this.handleCompleteAudio(audioBlob);
        };

        // 开始录制，每秒产生一个数据块
        this.mediaRecorder.start(1000);
    }

    processAudioChunk(audioChunk) {
        // 处理实时音频数据块
        // 这里可以：
        // 1. 发送到服务器进行实时语音识别
        // 2. 进行本地音频处理
        // 3. 实时转码或压缩
        
        console.log('处理音频块，大小:', audioChunk.size);
        
        // 如果不是麦克风输入，需要使用其他方式进行语音识别
        if (this.audioSource !== 'microphone') {
            // 这里可以将音频发送到后端进行处理
            // this.sendAudioToServer(audioChunk);
        }
    }

    handleCompleteAudio(audioBlob) {
        // 处理完整的录制音频
        console.log('完整音频大小:', audioBlob.size);
        
        // 创建下载链接（可选）
        const url = URL.createObjectURL(audioBlob);
        console.log('音频URL:', url);
    }

    startVisualization() {
        const canvas = document.getElementById('visualizer');
        const canvasContext = canvas.getContext('2d');
        canvas.width = canvas.offsetWidth;
        canvas.height = canvas.offsetHeight;

        const bufferLength = this.analyser.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);

        const draw = () => {
            if (!this.isCapturing) return;

            requestAnimationFrame(draw);

            this.analyser.getByteTimeDomainData(dataArray);

            canvasContext.fillStyle = '#1a202c';
            canvasContext.fillRect(0, 0, canvas.width, canvas.height);

            canvasContext.lineWidth = 2;
            canvasContext.strokeStyle = '#667eea';
            canvasContext.beginPath();

            const sliceWidth = canvas.width / bufferLength;
            let x = 0;

            for (let i = 0; i < bufferLength; i++) {
                const v = dataArray[i] / 128.0;
                const y = v * canvas.height / 2;

                if (i === 0) {
                    canvasContext.moveTo(x, y);
                } else {
                    canvasContext.lineTo(x, y);
                }

                x += sliceWidth;
            }

            canvasContext.lineTo(canvas.width, canvas.height / 2);
            canvasContext.stroke();
        };

        draw();
    }

    startAudioLevelMonitoring() {
        const bufferLength = this.analyser.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);

        const monitor = () => {
            if (!this.isCapturing) return;

            this.analyser.getByteFrequencyData(dataArray);
            
            // 计算平均音量
            let sum = 0;
            for (let i = 0; i < bufferLength; i++) {
                sum += dataArray[i];
            }
            const average = sum / bufferLength;
            const db = 20 * Math.log10(average / 255);
            
            document.getElementById('audioLevel').textContent = `${db.toFixed(1)} dB`;

            setTimeout(monitor, 100);
        };

        monitor();
    }

    stopCapture() {
        // 停止媒体录制
        if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
            this.mediaRecorder.stop();
        }

        // 停止语音识别
        if (this.recognition) {
            this.recognition.stop();
        }

        // 停止所有音频轨道
        if (this.mediaStream) {
            this.mediaStream.getTracks().forEach(track => track.stop());
        }

        // 更新UI状态
        this.isCapturing = false;
        document.getElementById('startBtn').disabled = false;
        document.getElementById('stopBtn').disabled = true;
        document.getElementById('captureStatus').textContent = '已停止';
        document.getElementById('audioLevel').textContent = '0 dB';
    }

    async translateText(text) {
        const sourceLang = document.getElementById('sourceLang').value;
        const targetLang = document.getElementById('targetLang').value;

        // 这里使用模拟翻译，实际应用中应该调用翻译API
        // 例如：Google Translate API, DeepL API, 百度翻译API等
        
        const translatedText = document.getElementById('translatedText');
        
        // 模拟翻译延迟
        translatedText.innerHTML += `<p style="color: #999;"><em>正在翻译...</em></p>`;
        
        setTimeout(() => {
            // 模拟翻译结果
            const mockTranslation = this.mockTranslate(text, sourceLang, targetLang);
            translatedText.innerHTML += `<p><strong>[${new Date().toLocaleTimeString()}]</strong> ${mockTranslation}</p>`;
            translatedText.scrollTop = translatedText.scrollHeight;
            
            // 语音合成播放翻译结果（可选）
            this.speakTranslation(mockTranslation, targetLang);
        }, 500);
    }

    mockTranslate(text, sourceLang, targetLang) {
        // 模拟翻译功能
        const translations = {
            'zh-CN': {
                'en-US': 'This is a simulated translation to English.',
                'ja-JP': 'これは日本語への模擬翻訳です。',
                'ko-KR': '이것은 한국어로의 모의 번역입니다.',
                'fr-FR': 'Ceci est une traduction simulée en français.'
            },
            'en-US': {
                'zh-CN': '这是模拟翻译成中文的结果。',
                'ja-JP': 'これは日本語への模擬翻訳です。',
                'ko-KR': '이것은 한국어로의 모의 번역입니다.',
                'fr-FR': 'Ceci est une traduction simulée en français.'
            }
        };

        if (translations[sourceLang] && translations[sourceLang][targetLang]) {
            return translations[sourceLang][targetLang] + ` (原文: ${text.substring(0, 50)}...)`;
        }
        
        return `[模拟翻译] ${text}`;
    }

    speakTranslation(text, lang) {
        // 使用Web Speech API进行语音合成
        if ('speechSynthesis' in window) {
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = lang;
            utterance.rate = 1.0;
            utterance.pitch = 1.0;
            utterance.volume = 0.8;
            
            // 播放合成语音
            // window.speechSynthesis.speak(utterance);
        }
    }

    playTestAudio() {
        // 播放测试音频
        const audio = new Audio();
        audio.src = 'data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBSuBzvLZiTYIG2m98OScTgwOUarm7blmFgU7k9n1unEiBC13yO/eizEIHWq+8+OWT';
        audio.play();
        
        // 显示提示
        const originalText = document.getElementById('originalText');
        originalText.innerHTML += '<p style="color: #667eea;"><strong>播放测试音频...</strong></p>';
    }

    showError(message) {
        const errorElement = document.getElementById('errorMessage');
        errorElement.textContent = message;
        errorElement.style.display = 'block';
        
        setTimeout(() => {
            errorElement.style.display = 'none';
        }, 5000);
    }
}

// 初始化系统
document.addEventListener('DOMContentLoaded', () => {
    const system = new AudioInterpreterSystem();
    
    // 语言选择变更处理
    document.getElementById('sourceLang').addEventListener('change', (e) => {
        if (system.recognition) {
            system.recognition.lang = e.target.value;
        }
    });
});

// 浏览器兼容性检查
window.addEventListener('load', () => {
    const warnings = [];
    
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        warnings.push('您的浏览器不支持媒体设备API');
    }
    
    if (!window.AudioContext && !window.webkitAudioContext) {
        warnings.push('您的浏览器不支持Web Audio API');
    }
    
    if (!window.SpeechRecognition && !window.webkitSpeechRecognition) {
        warnings.push('您的浏览器不支持语音识别API（仅麦克风模式受影响）');
    }
    
    if (warnings.length > 0) {
        console.warn('兼容性警告:', warnings);
    }
});