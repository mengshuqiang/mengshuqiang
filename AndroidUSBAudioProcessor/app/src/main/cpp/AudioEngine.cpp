#include "AudioEngine.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    for (auto& band : mEqualizerBands) {
        band = 0.0f;
    }
}

AudioEngine::~AudioEngine() {
    release();
}

bool AudioEngine::initialize(int32_t sampleRate, int32_t channelCount, int32_t framesPerBurst) {
    mSampleRate = sampleRate;
    mChannelCount = channelCount;
    mFramesPerBurst = framesPerBurst;
    
    // 初始化音频效果处理器
    mAudioEffect = std::make_unique<AudioEffect>(sampleRate, channelCount);
    
    // 分配缓冲区
    int32_t bufferSize = framesPerBurst * channelCount;
    mInputBuffer.resize(bufferSize);
    mOutputBuffer.resize(bufferSize);
    mProcessBuffer.resize(bufferSize);
    
    LOGI("Audio engine initialized: %d Hz, %d channels, %d frames/burst",
         sampleRate, channelCount, framesPerBurst);
    
    return true;
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(mMutex);
    
    if (mIsRunning) {
        LOGI("Audio engine already running");
        return true;
    }
    
    // 创建输入流
    if (!createInputStream()) {
        LOGE("Failed to create input stream");
        return false;
    }
    
    // 创建输出流
    if (!createOutputStream()) {
        LOGE("Failed to create output stream");
        return false;
    }
    
    // 启动流
    oboe::Result result = mInputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start input stream: %s", oboe::convertToText(result));
        return false;
    }
    
    result = mOutputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start output stream: %s", oboe::convertToText(result));
        mInputStream->requestStop();
        return false;
    }
    
    mIsRunning = true;
    LOGI("Audio engine started successfully");
    return true;
}

bool AudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mMutex);
    
    if (!mIsRunning) {
        return true;
    }
    
    mIsRunning = false;
    
    if (mInputStream) {
        mInputStream->requestStop();
        mInputStream->close();
        mInputStream.reset();
    }
    
    if (mOutputStream) {
        mOutputStream->requestStop();
        mOutputStream->close();
        mOutputStream.reset();
    }
    
    LOGI("Audio engine stopped");
    return true;
}

void AudioEngine::release() {
    stop();
    mAudioEffect.reset();
}

bool AudioEngine::createInputStream() {
    oboe::AudioStreamBuilder builder;
    
    builder.setDirection(oboe::Direction::Input)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setSampleRate(mSampleRate)
           ->setChannelCount(mChannelCount);
    
    if (mFramesPerBurst > 0) {
        builder.setFramesPerDataCallback(mFramesPerBurst);
    }
    
    // 尝试使用USB设备作为输入
    // 注意：这需要Android系统支持USB音频路由
    builder.setDeviceId(oboe::kUnspecified);
    
    oboe::Result result = builder.openStream(mInputStream);
    
    if (result != oboe::Result::OK) {
        LOGE("Failed to create input stream: %s", oboe::convertToText(result));
        return false;
    }
    
    LOGI("Input stream created: %d Hz, %d channels",
         mInputStream->getSampleRate(),
         mInputStream->getChannelCount());
    
    return true;
}

bool AudioEngine::createOutputStream() {
    oboe::AudioStreamBuilder builder;
    
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setSampleRate(mSampleRate)
           ->setChannelCount(mChannelCount)
           ->setDataCallback(this);
    
    if (mFramesPerBurst > 0) {
        builder.setFramesPerDataCallback(mFramesPerBurst);
    }
    
    oboe::Result result = builder.openStream(mOutputStream);
    
    if (result != oboe::Result::OK) {
        LOGE("Failed to create output stream: %s", oboe::convertToText(result));
        return false;
    }
    
    LOGI("Output stream created: %d Hz, %d channels",
         mOutputStream->getSampleRate(),
         mOutputStream->getChannelCount());
    
    return true;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {
    
    if (!mIsRunning) {
        return oboe::DataCallbackResult::Stop;
    }
    
    float* outputData = static_cast<float*>(audioData);
    int32_t totalSamples = numFrames * mChannelCount;
    
    // 从输入流读取数据
    if (mInputStream) {
        oboe::ResultWithValue<int32_t> result = mInputStream->read(
            mInputBuffer.data(), numFrames, 0);
        
        if (result) {
            // 处理音频数据
            processAudioData(mInputBuffer.data(), outputData, numFrames);
            
            // 计算音频电平
            float level = calculateRMS(outputData, totalSamples);
            mCurrentLevel.store(level);
        } else {
            // 如果读取失败，输出静音
            std::fill_n(outputData, totalSamples, 0.0f);
        }
    } else {
        // 没有输入流，输出静音
        std::fill_n(outputData, totalSamples, 0.0f);
    }
    
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::processAudioData(float* inputData, float* outputData, int32_t numFrames) {
    int32_t totalSamples = numFrames * mChannelCount;
    
    // 复制输入到处理缓冲区
    std::copy_n(inputData, totalSamples, mProcessBuffer.data());
    
    // 应用音频效果
    EffectType effect = mCurrentEffect.load();
    if (effect != EffectType::NONE && mAudioEffect) {
        mAudioEffect->process(mProcessBuffer.data(), numFrames, effect);
    }
    
    // 应用均衡器
    if (effect == EffectType::EQUALIZER && mAudioEffect) {
        std::array<float, 10> bands;
        for (size_t i = 0; i < bands.size(); ++i) {
            bands[i] = mEqualizerBands[i].load();
        }
        mAudioEffect->applyEqualizer(mProcessBuffer.data(), numFrames, bands);
    }
    
    // 应用音量
    float volume = mVolume.load();
    for (int32_t i = 0; i < totalSamples; ++i) {
        outputData[i] = mProcessBuffer[i] * volume;
        
        // 限幅
        outputData[i] = std::max(-1.0f, std::min(1.0f, outputData[i]));
    }
}

float AudioEngine::calculateRMS(const float* data, int32_t numSamples) {
    float sum = 0.0f;
    for (int32_t i = 0; i < numSamples; ++i) {
        sum += data[i] * data[i];
    }
    return std::sqrt(sum / numSamples);
}

void AudioEngine::setEffect(EffectType effect) {
    mCurrentEffect.store(effect);
    LOGI("Effect set to: %d", static_cast<int>(effect));
}

void AudioEngine::setVolume(float volume) {
    mVolume.store(std::max(0.0f, std::min(2.0f, volume)));
}

void AudioEngine::setEqualizerBand(int band, float gain) {
    if (band >= 0 && band < mEqualizerBands.size()) {
        mEqualizerBands[band].store(gain);
    }
}

int32_t AudioEngine::getLatency() const {
    if (mOutputStream) {
        auto result = mOutputStream->calculateLatencyMillis();
        if (result) {
            return static_cast<int32_t>(result.value());
        }
    }
    return -1;
}

void AudioEngine::onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error) {
    LOGE("Error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) {
    LOGE("Error after close: %s", oboe::convertToText(error));
    
    // 尝试重新启动流
    if (mIsRunning) {
        LOGI("Attempting to restart audio stream...");
        stop();
        start();
    }
}