#ifndef AUDIO_ENGINE_H
#define AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <mutex>
#include <atomic>
#include "AudioEffect.h"

enum class EffectType {
    NONE = 0,
    ECHO = 1,
    REVERB = 2,
    EQUALIZER = 3,
    PITCH_SHIFT = 4,
    DISTORTION = 5,
    CHORUS = 6
};

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    AudioEngine();
    ~AudioEngine();
    
    bool initialize(int32_t sampleRate, int32_t channelCount, int32_t framesPerBurst);
    bool start();
    bool stop();
    void release();
    
    // 音频效果控制
    void setEffect(EffectType effect);
    void setVolume(float volume);
    void setEqualizerBand(int band, float gain);
    
    // 获取音频信息
    float getAudioLevel() const { return mCurrentLevel.load(); }
    int32_t getLatency() const;
    
    // Oboe回调
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;
        
    void onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    // 音频流
    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;
    
    // 音频参数
    int32_t mSampleRate;
    int32_t mChannelCount;
    int32_t mFramesPerBurst;
    
    // 音频效果处理器
    std::unique_ptr<AudioEffect> mAudioEffect;
    
    // 控制参数
    std::atomic<EffectType> mCurrentEffect{EffectType::NONE};
    std::atomic<float> mVolume{1.0f};
    std::atomic<float> mCurrentLevel{0.0f};
    
    // 均衡器参数
    std::array<std::atomic<float>, 10> mEqualizerBands;
    
    // 缓冲区
    std::vector<float> mInputBuffer;
    std::vector<float> mOutputBuffer;
    std::vector<float> mProcessBuffer;
    
    // 同步
    std::mutex mMutex;
    std::atomic<bool> mIsRunning{false};
    
    // 私有方法
    bool createInputStream();
    bool createOutputStream();
    void processAudioData(float* inputData, float* outputData, int32_t numFrames);
    float calculateRMS(const float* data, int32_t numFrames);
}; #endif // AUDIO_ENGINE_H