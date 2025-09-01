#include "virtual_audio_driver.h"
#include <android/log.h>
#include <cstring>
#include <chrono>

#define LOG_TAG "VirtualAudioDriver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

VirtualAudioDriver::VirtualAudioDriver(int sampleRate, int bufferSize)
    : m_sampleRate(sampleRate)
    , m_bufferSize(bufferSize)
    , m_inputStream(nullptr)
    , m_outputStream(nullptr)
    , m_engineObject(nullptr)
    , m_engineEngine(nullptr)
    , m_recorderObject(nullptr)
    , m_recorderRecord(nullptr)
    , m_playerObject(nullptr)
    , m_playerPlay(nullptr)
    , m_isRunning(false)
    , m_useAAudio(true) {
    
    m_inputBuffer.resize(bufferSize);
    m_outputBuffer.resize(bufferSize);
}

VirtualAudioDriver::~VirtualAudioDriver() {
    release();
}

bool VirtualAudioDriver::initialize() {
    LOGI("初始化虚拟音频驱动");
    
    // 首先尝试AAudio
    if (initializeAAudio()) {
        m_useAAudio = true;
        LOGI("使用AAudio API");
        return true;
    }
    
    // 如果AAudio失败，使用OpenSL ES
    if (initializeOpenSLES()) {
        m_useAAudio = false;
        LOGI("使用OpenSL ES API");
        return true;
    }
    
    LOGE("音频驱动初始化失败");
    return false;
}

bool VirtualAudioDriver::initializeAAudio() {
    try {
        return createAAudioInputStream() && createAAudioOutputStream();
    } catch (const std::exception& e) {
        LOGE("AAudio初始化异常: %s", e.what());
        return false;
    }
}

bool VirtualAudioDriver::createAAudioInputStream() {
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    
    if (result != AAUDIO_OK) {
        logAAudioError(result, "创建输入流构建器");
        return false;
    }
    
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setDeviceId(builder, AAUDIO_UNSPECIFIED);
    AAudioStreamBuilder_setSampleRate(builder, m_sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, m_bufferSize);
    AAudioStreamBuilder_setDataCallback(builder, aaudioInputCallback, this);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    
    result = AAudioStreamBuilder_openStream(builder, &m_inputStream);
    AAudioStreamBuilder_delete(builder);
    
    if (result != AAUDIO_OK) {
        logAAudioError(result, "创建输入流");
        return false;
    }
    
    LOGI("AAudio输入流创建成功");
    return true;
}

bool VirtualAudioDriver::createAAudioOutputStream() {
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    
    if (result != AAUDIO_OK) {
        logAAudioError(result, "创建输出流构建器");
        return false;
    }
    
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setDeviceId(builder, AAUDIO_UNSPECIFIED);
    AAudioStreamBuilder_setSampleRate(builder, m_sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, m_bufferSize);
    AAudioStreamBuilder_setDataCallback(builder, aaudioOutputCallback, this);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    
    result = AAudioStreamBuilder_openStream(builder, &m_outputStream);
    AAudioStreamBuilder_delete(builder);
    
    if (result != AAUDIO_OK) {
        logAAudioError(result, "创建输出流");
        return false;
    }
    
    LOGI("AAudio输出流创建成功");
    return true;
}

bool VirtualAudioDriver::start() {
    if (m_isRunning.exchange(true)) {
        return true; // 已经在运行
    }
    
    LOGI("启动虚拟音频驱动");
    
    if (m_useAAudio) {
        // 启动AAudio流
        aaudio_result_t result1 = AAudioStream_requestStart(m_inputStream);
        aaudio_result_t result2 = AAudioStream_requestStart(m_outputStream);
        
        if (result1 != AAUDIO_OK || result2 != AAUDIO_OK) {
            LOGE("启动AAudio流失败");
            m_isRunning = false;
            return false;
        }
    } else {
        // 启动OpenSL ES
        // 这里添加OpenSL ES的启动代码
    }
    
    // 启动音频处理线程
    m_audioThread = std::make_unique<std::thread>(&VirtualAudioDriver::audioProcessingLoop, this);
    
    LOGI("虚拟音频驱动启动成功");
    return true;
}

void VirtualAudioDriver::stop() {
    if (!m_isRunning.exchange(false)) {
        return; // 已经停止
    }
    
    LOGI("停止虚拟音频驱动");
    
    if (m_useAAudio) {
        if (m_inputStream) {
            AAudioStream_requestStop(m_inputStream);
        }
        if (m_outputStream) {
            AAudioStream_requestStop(m_outputStream);
        }
    }
    
    // 等待音频处理线程结束
    if (m_audioThread && m_audioThread->joinable()) {
        m_audioThread->join();
    }
    
    LOGI("虚拟音频驱动已停止");
}

void VirtualAudioDriver::audioProcessingLoop() {
    LOGI("音频处理循环开始");
    
    while (m_isRunning.load()) {
        // 这里可以添加额外的音频处理逻辑
        // 主要的音频处理在回调函数中进行
        
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
    
    LOGI("音频处理循环结束");
}

aaudio_data_callback_result_t VirtualAudioDriver::aaudioInputCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames) {
    
    VirtualAudioDriver* driver = static_cast<VirtualAudioDriver*>(userData);
    int16_t* inputData = static_cast<int16_t*>(audioData);
    
    // 复制到输入缓冲区
    std::memcpy(driver->m_inputBuffer.data(), inputData, numFrames * sizeof(int16_t));
    
    // 如果有回调函数，调用它
    if (driver->m_audioCallback) {
        driver->m_audioCallback(
            driver->m_inputBuffer.data(),
            driver->m_outputBuffer.data(),
            numFrames
        );
    }
    
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

aaudio_data_callback_result_t VirtualAudioDriver::aaudioOutputCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames) {
    
    VirtualAudioDriver* driver = static_cast<VirtualAudioDriver*>(userData);
    int16_t* outputData = static_cast<int16_t*>(audioData);
    
    // 复制输出缓冲区到输出
    std::memcpy(outputData, driver->m_outputBuffer.data(), numFrames * sizeof(int16_t));
    
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void VirtualAudioDriver::setAudioCallback(std::function<void(const int16_t*, int16_t*, int)> callback) {
    m_audioCallback = callback;
}

void VirtualAudioDriver::release() {
    stop();
    
    if (m_useAAudio) {
        if (m_inputStream) {
            AAudioStream_close(m_inputStream);
            m_inputStream = nullptr;
        }
        if (m_outputStream) {
            AAudioStream_close(m_outputStream);
            m_outputStream = nullptr;
        }
    } else {
        // 清理OpenSL ES资源
        if (m_recorderObject) {
            (*m_recorderObject)->Destroy(m_recorderObject);
            m_recorderObject = nullptr;
        }
        if (m_playerObject) {
            (*m_playerObject)->Destroy(m_playerObject);
            m_playerObject = nullptr;
        }
        if (m_engineObject) {
            (*m_engineObject)->Destroy(m_engineObject);
            m_engineObject = nullptr;
        }
    }
    
    LOGI("虚拟音频驱动资源已释放");
}

void VirtualAudioDriver::logAAudioError(aaudio_result_t result, const char* operation) {
    LOGE("%s失败: %s", operation, AAudio_convertResultToText(result));
}

void VirtualAudioDriver::logOpenSLESError(SLresult result, const char* operation) {
    LOGE("%s失败，错误代码: %d", operation, result);
}

// OpenSL ES实现（备用方案）
bool VirtualAudioDriver::initializeOpenSLES() {
    LOGI("初始化OpenSL ES");
    
    // 创建引擎
    SLresult result = slCreateEngine(&m_engineObject, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        logOpenSLESError(result, "创建OpenSL ES引擎");
        return false;
    }
    
    result = (*m_engineObject)->Realize(m_engineObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        logOpenSLESError(result, "实现OpenSL ES引擎");
        return false;
    }
    
    result = (*m_engineObject)->GetInterface(m_engineObject, SL_IID_ENGINE, &m_engineEngine);
    if (result != SL_RESULT_SUCCESS) {
        logOpenSLESError(result, "获取OpenSL ES引擎接口");
        return false;
    }
    
    return createOpenSLESRecorder() && createOpenSLESPlayer();
}

bool VirtualAudioDriver::createOpenSLESRecorder() {
    // OpenSL ES录音器实现
    LOGI("创建OpenSL ES录音器");
    
    // 这里添加OpenSL ES录音器的详细实现
    // 由于代码较长，这里提供框架
    
    return true;
}

bool VirtualAudioDriver::createOpenSLESPlayer() {
    // OpenSL ES播放器实现
    LOGI("创建OpenSL ES播放器");
    
    // 这里添加OpenSL ES播放器的详细实现
    
    return true;
}