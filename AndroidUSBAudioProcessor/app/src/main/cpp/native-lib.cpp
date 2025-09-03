#include <jni.h>
#include <string>
#include <android/log.h>
#include "AudioEngine.h"

#define LOG_TAG "NativeAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static AudioEngine* audioEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_initializeNative(
        JNIEnv* env,
        jobject /* this */,
        jint sampleRate,
        jint channelCount,
        jint framesPerBurst) {
    
    LOGI("Initializing native audio engine: sampleRate=%d, channels=%d, framesPerBurst=%d",
         sampleRate, channelCount, framesPerBurst);
    
    if (audioEngine != nullptr) {
        delete audioEngine;
    }
    
    audioEngine = new AudioEngine();
    return audioEngine->initialize(sampleRate, channelCount, framesPerBurst);
}

JNIEXPORT jboolean JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_startNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (audioEngine == nullptr) {
        LOGE("Audio engine not initialized");
        return JNI_FALSE;
    }
    
    return audioEngine->start();
}

JNIEXPORT jboolean JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_stopNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (audioEngine == nullptr) {
        LOGE("Audio engine not initialized");
        return JNI_FALSE;
    }
    
    return audioEngine->stop();
}

JNIEXPORT void JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_releaseNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (audioEngine != nullptr) {
        delete audioEngine;
        audioEngine = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_setEffectNative(
        JNIEnv* env,
        jobject /* this */,
        jint effectType) {
    
    if (audioEngine != nullptr) {
        audioEngine->setEffect(static_cast<EffectType>(effectType));
    }
}

JNIEXPORT void JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_setVolumeNative(
        JNIEnv* env,
        jobject /* this */,
        jfloat volume) {
    
    if (audioEngine != nullptr) {
        audioEngine->setVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_setEqualizerBandNative(
        JNIEnv* env,
        jobject /* this */,
        jint band,
        jfloat gain) {
    
    if (audioEngine != nullptr) {
        audioEngine->setEqualizerBand(band, gain);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_getAudioLevelNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (audioEngine != nullptr) {
        return audioEngine->getAudioLevel();
    }
    return 0.0f;
}

JNIEXPORT jint JNICALL
Java_com_example_usbaudioprocessor_audio_NativeAudioProcessor_getLatencyNative(
        JNIEnv* env,
        jobject /* this */) {
    
    if (audioEngine != nullptr) {
        return audioEngine->getLatency();
    }
    return -1;
}

} // extern "C"