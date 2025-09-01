#ifndef AUDIO_PROCESSING_H
#define AUDIO_PROCESSING_H

#include <cstdint>
#include <vector>
#include <memory>

/**
 * 高性能音频处理类
 */
class AudioProcessor {
public:
    explicit AudioProcessor(int sampleRate);
    ~AudioProcessor();
    
    // 主要处理函数
    void processAudio(const int16_t* input, int16_t* output, int numSamples);
    
    // 参数设置
    void setNoiseGate(float threshold);
    void setCompressorRatio(float ratio);
    void setCompressorThreshold(float threshold);
    void setEQBands(const std::vector<float>& gains);
    
    // 处理模式
    void setInputProcessingMode(bool enabled);
    void setOutputProcessingMode(bool enabled);

private:
    int m_sampleRate;
    bool m_inputProcessingEnabled;
    bool m_outputProcessingEnabled;
    
    // 音频处理参数
    float m_noiseGate;
    float m_compressorRatio;
    float m_compressorThreshold;
    
    // 滤波器状态
    std::vector<float> m_highPassState;
    std::vector<float> m_lowPassState;
    std::vector<float> m_bandPassState;
    
    // 噪音抑制
    std::vector<float> m_noiseProfile;
    std::vector<float> m_spectralBuffer;
    
    // AGC状态
    float m_agcGain;
    float m_agcTarget;
    
    // 内部处理函数
    void applyNoiseGate(int16_t* samples, int numSamples);
    void applyHighPassFilter(int16_t* samples, int numSamples);
    void applyLowPassFilter(int16_t* samples, int numSamples);
    void applyCompressor(int16_t* samples, int numSamples);
    void applyAGC(int16_t* samples, int numSamples);
    void applyEqualizer(int16_t* samples, int numSamples);
    
    // 工具函数
    float calculateRMS(const int16_t* samples, int numSamples);
    void normalizeAudio(int16_t* samples, int numSamples);
    
    // 频域处理
    void performSpectralProcessing(int16_t* samples, int numSamples);
};

/**
 * 实时噪音抑制器
 */
class NoiseSupressor {
public:
    NoiseSupressor(int sampleRate, int frameSize);
    ~NoiseSupressor();
    
    void process(const float* input, float* output, int numSamples);
    void updateNoiseProfile(const float* noiseSamples, int numSamples);
    
private:
    int m_sampleRate;
    int m_frameSize;
    std::vector<float> m_noiseSpectrum;
    std::vector<float> m_smoothingBuffer;
    
    void spectralSubtraction(const float* input, float* output, int numSamples);
};

/**
 * 语音增强器
 */
class VoiceEnhancer {
public:
    VoiceEnhancer(int sampleRate);
    ~VoiceEnhancer();
    
    void enhance(const int16_t* input, int16_t* output, int numSamples);
    void setEnhancementLevel(float level); // 0.0 - 1.0
    
private:
    int m_sampleRate;
    float m_enhancementLevel;
    
    // 语音特征增强
    void emphasizeVoiceFrequencies(int16_t* samples, int numSamples);
    void applyPreEmphasis(int16_t* samples, int numSamples);
    void applyDeEmphasis(int16_t* samples, int numSamples);
    
    // 滤波器状态
    float m_preEmphasisState;
    float m_deEmphasisState;
};

/**
 * 回声消除器
 */
class EchoCanceller {
public:
    EchoCanceller(int sampleRate, float maxDelayMs = 500.0f);
    ~EchoCanceller();
    
    void process(const int16_t* input, const int16_t* reference, 
                int16_t* output, int numSamples);
    
private:
    int m_sampleRate;
    int m_maxDelaySamples;
    std::vector<int16_t> m_delayBuffer;
    int m_delayIndex;
    
    // 自适应滤波器
    std::vector<float> m_adaptiveFilter;
    float m_stepSize;
    
    void adaptiveFilter(const int16_t* input, const int16_t* reference,
                       int16_t* output, int numSamples);
};

#endif // AUDIO_PROCESSING_H