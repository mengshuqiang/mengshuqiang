# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep audio processing classes
-keep class com.virtualaudio.card.audio.** { *; }
-keep class com.virtualaudio.card.service.** { *; }

# Keep accessibility service
-keep class com.virtualaudio.card.service.WeChatAudioAccessibilityService { *; }

# Keep Xposed module (if using)
-keep class com.virtualaudio.card.wechat.XposedModule { *; }

# Keep reflection-used classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Audio related classes
-keep class android.media.** { *; }

# Prevent obfuscation of audio callback methods
-keepclassmembers class * {
    public void *Callback*(...);
    public void on*(...);
}