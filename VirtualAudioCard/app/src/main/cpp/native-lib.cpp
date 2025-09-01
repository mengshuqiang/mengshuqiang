#include <jni.h>
#include <string>
#include <android/log.h>
#include <aaudio/AAudio.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include "audio_processing.h"
#include "virtual_audio_driver.h"

#define LOG_TAG "VirtualAudioNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局变量
static VirtualAudioDriver* g_audioDriver = nullptr;
static AudioProcessor* g_audioProcessor = nullptr;

extern "C" JNIEXPORT jstring JNICALL
Java_com_virtualaudio_card_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualaudio_card_audio_VirtualAudioDevice_initializeNative(
        JNIEnv* env,
        jobject /* this */,
        jint sampleRate,
        jint bufferSize) {
    
    LOGI("初始化本地音频驱动，采样率: %d, 缓冲区大小: %d", sampleRate, bufferSize);
    
    try {
        // 初始化音频处理器
        g_audioProcessor = new AudioProcessor(sampleRate);
        
        // 初始化虚拟音频驱动
        g_audioDriver = new VirtualAudioDriver(sampleRate, bufferSize);
        
        if (g_audioDriver->initialize()) {
            LOGI("本地音频驱动初始化成功");
            return JNI_TRUE;
        } else {
            LOGE("本地音频驱动初始化失败");
            return JNI_FALSE;
        }
    } catch (const std::exception& e) {
        LOGE("初始化异常: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_virtualaudio_card_audio_VirtualAudioDevice_startNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (g_audioDriver) {
        return g_audioDriver->start() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_virtualaudio_card_audio_VirtualAudioDevice_stopNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (g_audioDriver) {
        g_audioDriver->stop();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_virtualaudio_card_audio_VirtualAudioDevice_releaseNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (g_audioDriver) {
        delete g_audioDriver;
        g_audioDriver = nullptr;
    }
    
    if (g_audioProcessor) {
        delete g_audioProcessor;
        g_audioProcessor = nullptr;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_virtualaudio_card_service_AudioProcessingService_processAudioNative(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray inputData,
        jint length) {
    
    if (!g_audioProcessor) {
        return inputData; // 如果处理器未初始化，返回原始数据
    }
    
    // 获取输入数据
    jbyte* inputBytes = env->GetByteArrayElements(inputData, nullptr);
    
    // 创建输出数组
    jbyteArray outputData = env->NewByteArray(length);
    jbyte* outputBytes = env->GetByteArrayElements(outputData, nullptr);
    
    // 处理音频数据
    g_audioProcessor->processAudio(
        reinterpret_cast<int16_t*>(inputBytes),
        reinterpret_cast<int16_t*>(outputBytes),
        length / 2  // 16位样本数
    );
    
    // 释放数组
    env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(outputData, outputBytes, 0);
    
    return outputData;
}

extern "C" JNIEXPORT void JNICALL
Java_com_virtualaudio_card_service_AudioProcessingService_setProcessingParametersNative(
        JNIEnv* env,
        jobject /* this */,
        jfloat noiseGate,
        jfloat compressorRatio,
        jfloat compressorThreshold) {
    
    if (g_audioProcessor) {
        g_audioProcessor->setNoiseGate(noiseGate);
        g_audioProcessor->setCompressorRatio(compressorRatio);
        g_audioProcessor->setCompressorThreshold(compressorThreshold);
        
        LOGI("音频处理参数已更新: 噪音门限=%.2f, 压缩比=%.2f, 压缩阈值=%.2f", 
             noiseGate, compressorRatio, compressorThreshold);
    }
}