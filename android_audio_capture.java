// Android音频捕获和处理示例
public class RealTimeTranslationService {
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    
    // 音频录制参数
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    public void startAudioCapture() {
        // 1. 检查权限
        if (ContextCompat.checkSelfPermission(context, 
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // 请求录音权限
            return;
        }
        
        // 2. 初始化AudioRecord来捕获音频
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, 
                CHANNEL_CONFIG, AUDIO_FORMAT);
        
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,  // 或使用VOICE_COMMUNICATION
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
        );
        
        // 3. 开始录制
        audioRecord.startRecording();
        isRecording = true;
        
        // 4. 在后台线程中处理音频数据
        new Thread(this::processAudioData).start();
    }
    
    private void processAudioData() {
        byte[] audioBuffer = new byte[1024];
        
        while (isRecording) {
            // 读取音频数据
            int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
            
            if (bytesRead > 0) {
                // 发送到语音识别服务
                processAudioChunk(audioBuffer, bytesRead);
            }
        }
    }
    
    private void processAudioChunk(byte[] audioData, int length) {
        // 1. 语音识别 (ASR)
        String recognizedText = speechToText(audioData);
        
        // 2. 文本翻译
        String translatedText = translateText(recognizedText);
        
        // 3. 语音合成 (TTS)
        byte[] synthesizedAudio = textToSpeech(translatedText);
        
        // 4. 播放翻译后的音频
        playTranslatedAudio(synthesizedAudio);
    }
    
    private void playTranslatedAudio(byte[] audioData) {
        // 初始化AudioTrack用于音频播放
        int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
                
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AUDIO_FORMAT,
                bufferSize,
                AudioTrack.MODE_STREAM
        );
        
        audioTrack.play();
        audioTrack.write(audioData, 0, audioData.length);
    }
    
    // 音频路由管理
    private void configureAudioRouting() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        // 检查耳机连接状态
        if (audioManager.isWiredHeadsetOn()) {
            // 配置音频路由到耳机
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);
        }
        
        // 监听耳机连接状态变化
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_HEADSET_PLUG);
        registerReceiver(headsetReceiver, filter);
    }
    
    private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                int state = intent.getIntExtra("state", -1);
                if (state == 1) {
                    // 耳机已连接
                    configureHeadsetAudio();
                } else if (state == 0) {
                    // 耳机已断开
                    configureDeviceAudio();
                }
            }
        }
    };
}