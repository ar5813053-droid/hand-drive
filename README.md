# HandDrive

**Virtual steering wheel for Android racing games** — controlled by your hand via the phone camera.

HandDrive tracks your hand in real time, models a virtual steering wheel, detects an open-palm brake gesture, and injects touch gestures into racing games through Android Accessibility Service.

> **Current status: Phase 1 foundation**  
> Project structure, Jetpack Compose UI, navigation, theme, and GitHub Actions debug APK build.

## Privacy

The camera is used for real-time hand tracking. Camera frames are processed **locally on the device** and are **not recorded or uploaded** by HandDrive.

- No account required  
- No cloud backend  
- No unnecessary internet permission  

## Requirements

- Android 8.0 (API 26) or higher  
- Camera (added in Phase 2)  
- Accessibility Service permission (added in Phase 6)

## Development phases

| Phase | Focus |
|-------|-------|
| **1** | Android foundation, Compose UI, navigation, GitHub Actions |
| 2 | CameraX + preview + frame analyzer |
| 3 | MediaPipe hand tracking + landmarks |
| 4 | Virtual steering engine |
| 5 | Open-palm brake + safety |
| 6 | Accessibility Service + input injection |
| 7 | Game calibration + profiles |
| 8 | Real game testing |
| 9 | Polish |
| 10 | Release |

## Building

### GitHub Actions (recommended when working from phone only)

Every push to `main` builds a debug APK. Download the artifact from the Actions tab.

### Local (if you have a development machine)

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/`

## Tech stack (Phase 1)

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- minSdk 26 / targetSdk 35 / compileSdk 35

## License

MIT — see [LICENSE](LICENSE)
