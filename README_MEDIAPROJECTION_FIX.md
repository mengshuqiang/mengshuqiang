# MediaProjection Foreground Service Fix

## Problem
The error `java.lang.SecurityException: Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` occurs when trying to use MediaProjection API without a properly configured foreground service.

## Solution Overview
Starting from Android 10 (API 29), MediaProjection requires a foreground service with the specific type `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`. This implementation provides a complete solution with:

1. **MediaProjectionService** - A foreground service that handles MediaProjection lifecycle
2. **UsbAudioManager** - Main class that manages audio capture using MediaProjection
3. **MainActivity** - Sample activity demonstrating proper usage
4. **AndroidManifest.xml** - Proper permissions and service declarations

## Key Components

### 1. MediaProjectionService (`MediaProjectionService.uts`)
- Implements a foreground service with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
- Creates and manages MediaProjection instances safely
- Handles notification requirements for foreground services
- Provides proper lifecycle management

### 2. UsbAudioManager (`usb-audio-manager.uts`)
- Manages the entire audio capture workflow
- Starts and binds to MediaProjectionService before creating MediaProjection
- Implements AudioRecord with AudioPlaybackCaptureConfiguration (API 29+)
- Handles audio data processing in a separate thread

### 3. Permissions Required
```xml
<!-- Core permissions -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Required for Android 14+ -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />

<!-- Optional but recommended -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 4. Service Declaration in AndroidManifest.xml
```xml
<service
    android:name=".MediaProjectionService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="mediaProjection"
    tools:targetApi="q">
</service>
```

## Implementation Flow

1. **Permission Check**: Request RECORD_AUDIO and other necessary permissions
2. **Start Foreground Service**: Start MediaProjectionService before requesting MediaProjection
3. **Request MediaProjection**: Show system dialog for screen recording permission
4. **Create MediaProjection**: Use the service to create MediaProjection instance
5. **Setup AudioCapture**: Configure AudioRecord with AudioPlaybackCaptureConfiguration
6. **Start Recording**: Begin capturing audio in a background thread
7. **Process Audio**: Handle captured audio data (send to USB, save to file, etc.)
8. **Cleanup**: Properly stop and release all resources

## API Level Requirements

- **Minimum**: API 21 (Android 5.0) for basic MediaProjection
- **Recommended**: API 29 (Android 10) for AudioPlaybackCapture
- **Full Support**: API 29+ for all features including foreground service type

## Error Handling

The implementation includes comprehensive error handling for:
- Missing permissions
- Service binding failures
- MediaProjection creation failures
- AudioRecord initialization errors
- Thread interruptions

## Usage Example

```typescript
// Initialize the manager
const audioManager = new UsbAudioManager(context)

// Start MediaProjection (will show permission dialog)
audioManager.initializeMediaProjection()

// Handle the result in onActivityResult
override onActivityResult(requestCode: Int, resultCode: Int, data: Intent | null): void {
    audioManager.handleActivityResult(requestCode, resultCode, data)
}

// Start recording when ready
if (audioManager.isMediaProjectionAvailable()) {
    audioManager.startRecording()
}

// Stop recording
audioManager.stopRecording()

// Clean up
audioManager.release()
```

## Testing Checklist

- [ ] Verify all permissions are granted
- [ ] Check notification appears when service starts
- [ ] Confirm MediaProjection permission dialog shows
- [ ] Test audio capture starts without crashes
- [ ] Verify audio data is being received
- [ ] Check proper cleanup on app exit
- [ ] Test on different Android versions (especially 10+)

## Troubleshooting

1. **Still getting SecurityException**: 
   - Ensure service is started before creating MediaProjection
   - Verify `foregroundServiceType="mediaProjection"` in manifest
   - Check Android version compatibility

2. **Service not starting**:
   - Check FOREGROUND_SERVICE permission
   - Verify notification channel creation for Android O+
   - Ensure proper PendingIntent flags

3. **No audio captured**:
   - Verify RECORD_AUDIO permission
   - Check AudioRecord state after initialization
   - Ensure API level is 29+ for AudioPlaybackCapture

4. **App crashes on Android 14+**:
   - Add FOREGROUND_SERVICE_MEDIA_PROJECTION permission
   - Update targetSdk and compileSdk appropriately

## Notes

- The foreground service will show a persistent notification while active
- Users must grant screen recording permission for audio capture to work
- Audio capture may not work with DRM-protected content
- Some apps may prevent their audio from being captured
- Battery optimization may affect long-running capture sessions