# HandDrive

**Virtual steering wheel for Android racing games** — controlled by your hand via the phone camera.

> **Current status: Phases 1–8**  
> CameraX · MediaPipe · Steering · Open-palm brake · Auto throttle · Profiles · Landscape calibration · Accessibility injection  
> **Real-game results require manual device testing** — see [`docs/GAME_COMPATIBILITY.md`](docs/GAME_COMPATIBILITY.md).

## Privacy

- Camera frames processed **on-device only** (MediaPipe model bundled)
- No recording, no upload, no analytics, no account
- **No INTERNET permission**
- Accessibility injects only the touch gestures you configure

## Requirements

- Android 8.0 (API 26)+
- Camera permission
- **Accessibility Service enabled** for HandDrive

## Quick start

1. Enable **HandDrive** in system Accessibility settings  
2. **Game Profiles** → create a profile  
3. **Calibrate Controls** (locks **landscape**, place steering/brake/throttle markers)  
4. Open your racing game in landscape  
5. HandDrive **Controller** → **Start**  
6. Use **Emergency Stop** if anything sticks  

Default steering: hand RIGHT → game LEFT (toggle Invert in settings/profile).

## Manual tests

See `docs/GAME_COMPATIBILITY.md`. CI cannot prove third-party game compatibility.

## Architecture

```
CameraX → HandTracker → TrackingResult
                            ├→ SteeringEngine → SteeringCommand ─┐
                            └→ GestureDetector → GestureState  ─┤
                                                               ▼
                                                    GestureController
                                                               ▼
                                          HandDriveAccessibilityService
                                                               ▼
                                                    dispatchGesture()
```

Profiles supply normalized control coordinates (landscape calibration).

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

## License

MIT
