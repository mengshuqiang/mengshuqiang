package com.example.bluetootha2dpsink;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A2DP Sink音频处理器
 * 处理接收到的蓝牙音频流
 */
public class A2dpSinkAudioHandler {
    private static final String TAG = "A2dpSinkAudioHandler";
    
    // 音频参数
    private static final int SAMPLE_RATE = 44100; // 44.1kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    
    // SBC编解码器参数
    private static final String SBC_MIME_TYPE = "audio/sbc";
    private static final int SBC_SAMPLE_RATE = 44100;
    private static final int SBC_CHANNEL_COUNT = 2;
    private static final int SBC_BIT_RATE = 328000; // 328 kbps
    
    private AudioTrack mAudioTrack;
    private MediaCodec mDecoder;
    private boolean mIsPlaying = false;
    private Thread mPlaybackThread;
    
    // 音频缓冲区
    private static final int BUFFER_SIZE_FACTOR = 4;
    private byte[] mAudioBuffer;
    private int mBufferSize;
    
    public A2dpSinkAudioHandler() {
        initializeAudioTrack();
    }
    
    /**
     * 初始化AudioTrack用于播放音频
     */
    private void initializeAudioTrack() {
        // 计算缓冲区大小
        mBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, 
            AudioFormat.CHANNEL_OUT_STEREO,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR;
        
        mAudioBuffer = new byte[mBufferSize];
        
        // 创建AudioTrack
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            
            AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AUDIO_FORMAT)
                .build();
            
            mAudioTrack = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(mBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        } else {
            mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AUDIO_FORMAT,
                mBufferSize,
                AudioTrack.MODE_STREAM
            );
        }
        
        Log.d(TAG, "AudioTrack initialized with buffer size: " + mBufferSize);
    }
    
    /**
     * 初始化SBC解码器
     */
    private void initializeSbcDecoder() {
        try {
            // 创建SBC解码器
            mDecoder = MediaCodec.createDecoderByType(SBC_MIME_TYPE);
            
            // 配置解码器
            MediaFormat format = MediaFormat.createAudioFormat(
                SBC_MIME_TYPE,
                SBC_SAMPLE_RATE,
                SBC_CHANNEL_COUNT
            );
            format.setInteger(MediaFormat.KEY_BIT_RATE, SBC_BIT_RATE);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024);
            
            mDecoder.configure(format, null, null, 0);
            mDecoder.start();
            
            Log.d(TAG, "SBC decoder initialized");
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize SBC decoder", e);
            // 如果SBC解码器不可用，回退到PCM
            mDecoder = null;
        }
    }
    
    /**
     * 开始音频播放
     */
    public void startPlayback() {
        if (mIsPlaying) {
            Log.w(TAG, "Already playing");
            return;
        }
        
        mIsPlaying = true;
        
        if (mAudioTrack != null) {
            mAudioTrack.play();
        }
        
        // 启动播放线程
        mPlaybackThread = new Thread(new Runnable() {
            @Override
            public void run() {
                playbackLoop();
            }
        });
        mPlaybackThread.start();
        
        Log.d(TAG, "Playback started");
    }
    
    /**
     * 停止音频播放
     */
    public void stopPlayback() {
        mIsPlaying = false;
        
        if (mPlaybackThread != null) {
            try {
                mPlaybackThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Playback thread interrupted", e);
            }
            mPlaybackThread = null;
        }
        
        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.flush();
        }
        
        Log.d(TAG, "Playback stopped");
    }
    
    /**
     * 播放循环
     */
    private void playbackLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        
        while (mIsPlaying) {
            // 这里应该从蓝牙音频流中读取数据
            // 实际实现需要与BluetoothA2dpSink的音频数据回调集成
            
            // 模拟音频数据处理
            processAudioData();
            
            try {
                Thread.sleep(10); // 简单的延迟，实际应该基于音频缓冲区状态
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * 处理音频数据
     * 这个方法应该被蓝牙音频数据接收回调调用
     */
    public void processAudioData(byte[] audioData, int offset, int length) {
        if (!mIsPlaying || mAudioTrack == null) {
            return;
        }
        
        // 如果是SBC编码的数据，先解码
        if (mDecoder != null && isSbcData(audioData, offset)) {
            byte[] pcmData = decodeSbc(audioData, offset, length);
            if (pcmData != null) {
                writeAudioData(pcmData, 0, pcmData.length);
            }
        } else {
            // 直接写入PCM数据
            writeAudioData(audioData, offset, length);
        }
    }
    
    /**
     * 模拟音频数据处理（用于测试）
     */
    private void processAudioData() {
        // 生成测试音频数据（静音）
        byte[] testData = new byte[1024];
        writeAudioData(testData, 0, testData.length);
    }
    
    /**
     * 写入音频数据到AudioTrack
     */
    private void writeAudioData(byte[] data, int offset, int length) {
        if (mAudioTrack != null && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            int written = mAudioTrack.write(data, offset, length);
            if (written < 0) {
                Log.e(TAG, "AudioTrack write error: " + written);
            }
        }
    }
    
    /**
     * 检查是否是SBC编码的数据
     */
    private boolean isSbcData(byte[] data, int offset) {
        // SBC帧头检测（简化版）
        // 实际的SBC帧头为0x9C或其他值，取决于配置
        if (data.length > offset && (data[offset] & 0xFF) == 0x9C) {
            return true;
        }
        return false;
    }
    
    /**
     * 解码SBC数据
     */
    private byte[] decodeSbc(byte[] sbcData, int offset, int length) {
        if (mDecoder == null) {
            return null;
        }
        
        try {
            // 获取输入缓冲区
            int inputBufferIndex = mDecoder.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(sbcData, offset, length);
                
                mDecoder.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
            }
            
            // 获取输出缓冲区
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int outputBufferIndex = mDecoder.dequeueOutputBuffer(info, 0);
            
            if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = mDecoder.getOutputBuffer(outputBufferIndex);
                byte[] pcmData = new byte[info.size];
                outputBuffer.get(pcmData);
                
                mDecoder.releaseOutputBuffer(outputBufferIndex, false);
                return pcmData;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "SBC decode error", e);
        }
        
        return null;
    }
    
    /**
     * 设置音量
     */
    public void setVolume(float volume) {
        if (mAudioTrack != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mAudioTrack.setVolume(volume);
            } else {
                mAudioTrack.setStereoVolume(volume, volume);
            }
        }
    }
    
    /**
     * 获取当前播放位置（毫秒）
     */
    public long getPlaybackPosition() {
        if (mAudioTrack != null) {
            return (mAudioTrack.getPlaybackHeadPosition() * 1000L) / SAMPLE_RATE;
        }
        return 0;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopPlayback();
        
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
        
        if (mDecoder != null) {
            mDecoder.stop();
            mDecoder.release();
            mDecoder = null;
        }
        
        Log.d(TAG, "Audio handler released");
    }
}