# Jarvis TODO

## Deprecation Warnings (non-blocking, address in future cleanup pass)

### AudioRouteManager.kt
- [ ] Replace `isBluetoothA2dpOn`, `startBluetoothSco`, `stopBluetoothSco`, `isSpeakerphoneOn`, `isWiredHeadsetOn` with `AudioManager.setCommunicationDevice()` / `AudioDeviceInfo` (deprecated on API 31+)

### CameraCapabilityImpl.kt
- [ ] Migrate from legacy Camera1 API (`android.hardware.Camera`, `Camera.CameraInfo`) to CameraX or Camera2

### PairingStore.kt / SecureTokenStore.kt / PassphraseStore.kt
- [ ] Replace `EncryptedSharedPreferences` and `MasterKey` (security-crypto) with Jetpack DataStore + encryption, or update to the non-deprecated `MasterKeys` API

### ScreenContentExtractor.kt
- [ ] Remove the `isHeading()` extension function — shadowed by the Android SDK member `AccessibilityNodeInfo.isHeading()` and is now redundant
