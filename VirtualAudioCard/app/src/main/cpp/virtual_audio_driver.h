#ifndef VIRTUAL_AUDIO_DRIVER_H
#define VIRTUAL_AUDIO_DRIVER_H

#include <aaudio/AAudio.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <atomic>
#include <thread>
#include <memory>

/**
 * 虚拟音频驱动 - 使用AAudio和OpenSL ES实现
 */
class VirtualAudioDriver {
public:
    VirtualAudioDriver(int sampleRate, int bufferSize);
    ~VirtualAudioDriver();
    
    bool initialize();
    bool start();
    void stop();
    void release();
    
    // 音频回调设置
    void setAudioCallback(std::function<void(const int16_t*, int16_t*, int)> callback);
    
    // 状态查询
    bool isRunning() const { return m_isRunning.load(); }
    int getSampleRate() const { return m_sampleRate; }
    int getBufferSize() const { return m_bufferSize; }

private:
    // 配置参数
    int m_sampleRate;
    int m_bufferSize;
    
    // AAudio组件
    AAudioStream* m_inputStream;
    AAudioStream* m_outputStream;
    
    // OpenSL ES组件（备用方案）
    SLObjectItf m_engineObject;
    SLEngineItf m_engineEngine;
    SLObjectItf m_recorderObject;
    SLRecordItf m_recorderRecord;
    SLObjectItf m_playerObject;
    SLPlayItf m_playerPlay;
    
    // 控制状态
    std::atomic<bool> m_isRunning;
    std::atomic<bool> m_useAAudio;
    
    // 音频处理线程
    std::unique_ptr<std::thread> m_audioThread;
    
    // 音频缓冲区
    std::vector<int16_t> m_inputBuffer;
    std::vector<int16_t> m_outputBuffer;
    
    // 回调函数
    std::function<void(const int16_t*, int16_t*, int)> m_audioCallback;
    
    // AAudio初始化
    bool initializeAAudio();
    bool createAAudioInputStream();
    bool createAAudioOutputStream();
    
    // OpenSL ES初始化（备用）
    bool initializeOpenSLES();
    bool createOpenSLESRecorder();
    bool createOpenSLESPlayer();
    
    // 音频处理循环
    void audioProcessingLoop();
    
    // AAudio回调
    static aaudio_data_callback_result_t aaudioInputCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames);
        
    static aaudio_data_callback_result_t aaudioOutputCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames);
    
    // OpenSL ES回调
    static void openSLESRecorderCallback(SLAndroidSimpleBufferQueueItf bq, void* context);
    static void openSLESPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void* context);
    
    // 工具函数
    void logAAudioError(aaudio_result_t result, const char* operation);
    void logOpenSLESError(SLresult result, const char* operation);
};

#endif // VIRTUAL_AUDIO_DRIVER_H