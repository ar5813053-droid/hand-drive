# HandDrive

**Virtual steering wheel for Android racing games** — controlled by your hand via the phone camera.

> **Current status: Phases 1–7 complete**  
> CameraX · MediaPipe · Virtual steering · Open-palm brake · **Accessibility input injection**  
> Phase 7 Game Profiles + control calibration is implemented.

## Privacy

- Camera frames processed **on-device only** (MediaPipe model bundled)
- No recording, no upload, no analytics, no account
- **No INTERNET permission**
- Accessibility is used **only** to inject the touch gestures you intend

## Requirements

- Android 8.0 (API 26)+
- Camera permission
- **Accessibility Service enabled** for HandDrive (Settings → Accessibility)

## How to use

1. Enable **HandDrive** in system Accessibility settings
2. Open **Controller** → grant camera → **Start**
3. Use **Accessibility Test** buttons first (Test Tap / Left / Right / Brake / Release All)
4. Live hand tracking drives steering + open-palm brake when both tracking and Accessibility are active
5. **Emergency Stop** immediately releases all injected input

Default steering mapping remains inverted (hand RIGHT → steer LEFT) unless you enable Invert Steering.

## Manual Accessibility test checklist

1. Install debug APK  
2. Enable HandDrive Accessibility service  
3. Controller shows **Accessibility: CONNECTED**  
4. Test Tap — visible tap at default steer coordinates  
5. Test Left / Test Right — drag gestures  
6. Test Brake — brake region tap/hold  
7. Release All — stops active input  
8. Disable Accessibility — UI shows NOT ENABLED / DISCONNECTED  
9. Emergency Stop — always releases  

Real-game compatibility is Phase 8. Not every game accepts injected touches the same way.

## Architecture

```
CameraX → HandTracker → TrackingResult
                            ├→ SteeringEngine → SteeringCommand ─┐
                            └→ GestureDetector → GestureState  ─┤
                                                               ▼
                                                    InputCommand
                                                               ▼
                                                    GestureController
                                                               ▼
                                          HandDriveAccessibilityService
                                                               ▼
                                                    dispatchGesture()
```

## Tech

| Piece | Version |
|-------|---------|
| CameraX | 1.4.1 |
| MediaPipe Tasks Vision | 0.10.21 |
| min/target/compile SDK | 26 / 35 / 35 |

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

GitHub Actions builds on every push to `main`.

## License

MIT
