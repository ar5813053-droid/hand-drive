# HandDrive

**Virtual steering wheel for Android racing games** — controlled by your hand via the phone camera.

HandDrive tracks your hand in real time, models a virtual steering wheel, detects an open-palm brake gesture, and (in a later phase) injects touch gestures into racing games through Android Accessibility Service.

> **Current status: Phases 1–5 complete**  
> CameraX · MediaPipe hand tracking · Virtual steering · Open-palm brake · Settings · Calibration  
> **Phase 6 (Accessibility input injection) is NOT implemented yet.**

## Privacy

The camera is used for real-time hand tracking with **MediaPipe on-device**. Camera frames are processed **locally** and are **not recorded or uploaded**.

- MediaPipe model is **bundled** in the APK (no runtime download)
- No account, no cloud, no analytics
- **No INTERNET permission**

## Requirements

- Android 8.0 (API 26) or higher
- Camera permission

## How to use (Phases 2–5)

1. Open **Controller**
2. Grant camera permission
3. Tap **Start** — live preview + hand landmarks appear
4. Open palm → brake ON (status card)
5. Turn hand left/right → steering value updates (default: inverted)
6. Tune sensitivity / dead zone / invert in **Settings**
7. Capture center/left/right poses in **Calibration**
8. **Emergency Stop** halts tracking and resets steering/brake

Input injection into games arrives in **Phase 6**.

## Building

### GitHub Actions

Every push to `main` runs unit tests and builds a debug APK. Download the artifact from the Actions tab.

### Local

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Tech stack

| Component | Version / choice |
|-----------|------------------|
| Kotlin + Compose + Material 3 | — |
| CameraX | 1.4.1 |
| MediaPipe Tasks Vision | 0.10.21 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| DataStore Preferences | settings + calibration |

## Architecture

```
CameraX → HandTracker → TrackingResult
                            ├→ SteeringEngine → SteeringCommand
                            └→ GestureDetector → GestureState
```

Phase 6 will connect SteeringCommand + GestureState → AccessibilityService.

## License

MIT — see [LICENSE](LICENSE)
