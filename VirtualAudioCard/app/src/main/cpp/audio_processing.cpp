#include "audio_processing.h"
#include <android/log.h>
#include <cmath>
#include <algorithm>
#include <cstring>

#define LOG_TAG "AudioProcessingNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// AudioProcessor实现
AudioProcessor::AudioProcessor(int sampleRate) 
    : m_sampleRate(sampleRate)
    , m_inputProcessingEnabled(true)
    , m_outputProcessingEnabled(true)
    , m_noiseGate(-40.0f)
    , m_compressorRatio(4.0f)
    , m_compressorThreshold(-20.0f)
    , m_agcGain(1.0f)
    , m_agcTarget(0.7f) {
    
    // 初始化滤波器状态
    m_highPassState.resize(2, 0.0f);
    m_lowPassState.resize(2, 0.0f);
    m_bandPassState.resize(4, 0.0f);
    
    // 初始化噪音profile
    m_noiseProfile.resize(1024, 0.0f);
    m_spectralBuffer.resize(2048, 0.0f);
    
    LOGI("音频处理器初始化完成，采样率: %d Hz", sampleRate);
}

AudioProcessor::~AudioProcessor() {
    LOGI("音频处理器已销毁");
}

void AudioProcessor::processAudio(const int16_t* input, int16_t* output, int numSamples) {
    // 复制输入到输出
    std::memcpy(output, input, numSamples * sizeof(int16_t));
    
    if (m_inputProcessingEnabled || m_outputProcessingEnabled) {
        // 1. 噪音门限
        applyNoiseGate(output, numSamples);
        
        // 2. 高通滤波（去除低频噪音）
        applyHighPassFilter(output, numSamples);
        
        // 3. 低通滤波（去除高频噪音）
        applyLowPassFilter(output, numSamples);
        
        // 4. 动态范围压缩
        applyCompressor(output, numSamples);
        
        // 5. 自动增益控制
        applyAGC(output, numSamples);
        
        // 6. 最终标准化
        normalizeAudio(output, numSamples);
    }
}

void AudioProcessor::applyNoiseGate(int16_t* samples, int numSamples) {
    const float threshold = std::pow(10.0f, m_noiseGate / 20.0f) * 32767.0f;
    
    for (int i = 0; i < numSamples; ++i) {
        if (std::abs(samples[i]) < threshold) {
            samples[i] = static_cast<int16_t>(samples[i] * 0.1f); // 大幅衰减而不是完全静音
        }
    }
}

void AudioProcessor::applyHighPassFilter(int16_t* samples, int numSamples) {
    // 简单的IIR高通滤波器 (80Hz截止频率)
    const float cutoff = 80.0f;
    const float rc = 1.0f / (cutoff * 2.0f * M_PI);
    const float dt = 1.0f / m_sampleRate;
    const float alpha = rc / (rc + dt);
    
    for (int i = 0; i < numSamples; ++i) {
        float input = static_cast<float>(samples[i]);
        float output = alpha * (m_highPassState[1] + input - m_highPassState[0]);
        
        m_highPassState[0] = input;
        m_highPassState[1] = output;
        
        samples[i] = static_cast<int16_t>(std::clamp(output, -32768.0f, 32767.0f));
    }
}

void AudioProcessor::applyLowPassFilter(int16_t* samples, int numSamples) {
    // 简单的IIR低通滤波器 (8000Hz截止频率)
    const float cutoff = 8000.0f;
    const float rc = 1.0f / (cutoff * 2.0f * M_PI);
    const float dt = 1.0f / m_sampleRate;
    const float alpha = dt / (rc + dt);
    
    for (int i = 0; i < numSamples; ++i) {
        float input = static_cast<float>(samples[i]);
        m_lowPassState[0] = alpha * input + (1.0f - alpha) * m_lowPassState[0];
        
        samples[i] = static_cast<int16_t>(std::clamp(m_lowPassState[0], -32768.0f, 32767.0f));
    }
}

void AudioProcessor::applyCompressor(int16_t* samples, int numSamples) {
    const float threshold = std::pow(10.0f, m_compressorThreshold / 20.0f) * 32767.0f;
    
    for (int i = 0; i < numSamples; ++i) {
        float sample = static_cast<float>(samples[i]);
        float amplitude = std::abs(sample);
        
        if (amplitude > threshold) {
            float excess = amplitude - threshold;
            float compressedExcess = excess / m_compressorRatio;
            float newAmplitude = threshold + compressedExcess;
            
            sample = (sample >= 0) ? newAmplitude : -newAmplitude;
        }
        
        samples[i] = static_cast<int16_t>(std::clamp(sample, -32768.0f, 32767.0f));
    }
}

void AudioProcessor::applyAGC(int16_t* samples, int numSamples) {
    // 计算RMS
    float rms = calculateRMS(samples, numSamples);
    
    if (rms > 0.0f) {
        float targetGain = (m_agcTarget * 32767.0f) / rms;
        
        // 平滑增益变化
        const float alpha = 0.01f; // 攻击时间
        m_agcGain += alpha * (targetGain - m_agcGain);
        
        // 限制增益范围
        m_agcGain = std::clamp(m_agcGain, 0.1f, 10.0f);
        
        // 应用增益
        for (int i = 0; i < numSamples; ++i) {
            float sample = static_cast<float>(samples[i]) * m_agcGain;
            samples[i] = static_cast<int16_t>(std::clamp(sample, -32768.0f, 32767.0f));
        }
    }
}

float AudioProcessor::calculateRMS(const int16_t* samples, int numSamples) {
    float sum = 0.0f;
    for (int i = 0; i < numSamples; ++i) {
        float sample = static_cast<float>(samples[i]);
        sum += sample * sample;
    }
    return std::sqrt(sum / numSamples);
}

void AudioProcessor::normalizeAudio(int16_t* samples, int numSamples) {
    // 找到最大振幅
    int16_t maxAmplitude = 0;
    for (int i = 0; i < numSamples; ++i) {
        maxAmplitude = std::max(maxAmplitude, static_cast<int16_t>(std::abs(samples[i])));
    }
    
    if (maxAmplitude > 0) {
        // 标准化到80%最大值，避免削波
        float normalizationFactor = (32767.0f * 0.8f) / maxAmplitude;
        
        for (int i = 0; i < numSamples; ++i) {
            float sample = static_cast<float>(samples[i]) * normalizationFactor;
            samples[i] = static_cast<int16_t>(std::clamp(sample, -32768.0f, 32767.0f));
        }
    }
}

void AudioProcessor::setNoiseGate(float threshold) {
    m_noiseGate = threshold;
}

void AudioProcessor::setCompressorRatio(float ratio) {
    m_compressorRatio = ratio;
}

void AudioProcessor::setCompressorThreshold(float threshold) {
    m_compressorThreshold = threshold;
}

void AudioProcessor::setInputProcessingMode(bool enabled) {
    m_inputProcessingEnabled = enabled;
}

void AudioProcessor::setOutputProcessingMode(bool enabled) {
    m_outputProcessingEnabled = enabled;
}

// VoiceEnhancer实现
VoiceEnhancer::VoiceEnhancer(int sampleRate) 
    : m_sampleRate(sampleRate)
    , m_enhancementLevel(0.7f)
    , m_preEmphasisState(0.0f)
    , m_deEmphasisState(0.0f) {
}

VoiceEnhancer::~VoiceEnhancer() {
}

void VoiceEnhancer::enhance(const int16_t* input, int16_t* output, int numSamples) {
    // 复制输入到输出
    std::memcpy(output, input, numSamples * sizeof(int16_t));
    
    // 应用预加重
    applyPreEmphasis(output, numSamples);
    
    // 强调语音频率
    emphasizeVoiceFrequencies(output, numSamples);
    
    // 应用去加重
    applyDeEmphasis(output, numSamples);
}

void VoiceEnhancer::applyPreEmphasis(int16_t* samples, int numSamples) {
    const float alpha = 0.97f;
    
    for (int i = 0; i < numSamples; ++i) {
        float current = static_cast<float>(samples[i]);
        float output = current - alpha * m_preEmphasisState;
        m_preEmphasisState = current;
        
        samples[i] = static_cast<int16_t>(std::clamp(output, -32768.0f, 32767.0f));
    }
}

void VoiceEnhancer::emphasizeVoiceFrequencies(int16_t* samples, int numSamples) {
    // 简单的语音频段增强 (300Hz - 3400Hz)
    // 这里使用简化的带通滤波器
    
    for (int i = 0; i < numSamples; ++i) {
        // 简单的增强算法
        float sample = static_cast<float>(samples[i]);
        sample *= (1.0f + m_enhancementLevel * 0.2f); // 轻微增强
        
        samples[i] = static_cast<int16_t>(std::clamp(sample, -32768.0f, 32767.0f));
    }
}

void VoiceEnhancer::applyDeEmphasis(int16_t* samples, int numSamples) {
    const float alpha = 0.97f;
    
    for (int i = 0; i < numSamples; ++i) {
        float input = static_cast<float>(samples[i]);
        m_deEmphasisState = input + alpha * m_deEmphasisState;
        
        samples[i] = static_cast<int16_t>(std::clamp(m_deEmphasisState, -32768.0f, 32767.0f));
    }
}

void VoiceEnhancer::setEnhancementLevel(float level) {
    m_enhancementLevel = std::clamp(level, 0.0f, 1.0f);
}

// EchoCanceller实现
EchoCanceller::EchoCanceller(int sampleRate, float maxDelayMs) 
    : m_sampleRate(sampleRate)
    , m_delayIndex(0)
    , m_stepSize(0.01f) {
    
    m_maxDelaySamples = static_cast<int>(sampleRate * maxDelayMs / 1000.0f);
    m_delayBuffer.resize(m_maxDelaySamples, 0);
    m_adaptiveFilter.resize(64, 0.0f); // 64阶自适应滤波器
}

EchoCanceller::~EchoCanceller() {
}

void EchoCanceller::process(const int16_t* input, const int16_t* reference, 
                           int16_t* output, int numSamples) {
    
    for (int i = 0; i < numSamples; ++i) {
        // 获取延迟的参考信号
        int16_t delayedRef = m_delayBuffer[m_delayIndex];
        
        // 简单的回声消除：输入 - 延迟参考信号的一部分
        float result = static_cast<float>(input[i]) - delayedRef * 0.3f;
        
        // 更新延迟缓冲区
        m_delayBuffer[m_delayIndex] = reference[i];
        m_delayIndex = (m_delayIndex + 1) % m_maxDelaySamples;
        
        output[i] = static_cast<int16_t>(std::clamp(result, -32768.0f, 32767.0f));
    }
}

// NoiseSupressor实现
NoiseSupressor::NoiseSupressor(int sampleRate, int frameSize) 
    : m_sampleRate(sampleRate)
    , m_frameSize(frameSize) {
    
    m_noiseSpectrum.resize(frameSize / 2 + 1, 0.0f);
    m_smoothingBuffer.resize(frameSize, 0.0f);
}

NoiseSupressor::~NoiseSupressor() {
}

void NoiseSupressor::process(const float* input, float* output, int numSamples) {
    // 简化的噪音抑制实现
    for (int i = 0; i < numSamples; ++i) {
        output[i] = input[i];
        
        // 简单的噪音门限
        if (std::abs(input[i]) < 0.01f) {
            output[i] *= 0.1f; // 衰减小信号
        }
    }
}