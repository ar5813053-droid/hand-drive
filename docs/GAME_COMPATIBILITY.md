# Game Compatibility Test Checklist

HandDrive injects touches via Android AccessibilityService `dispatchGesture()`.
**Not every racing game accepts injected input equally.** Always test manually.

## Before testing

- [ ] HandDrive Accessibility service **enabled** and **Connected** in Controller
- [ ] Game profile **created and selected**
- [ ] **Calibrate Controls** completed in **landscape**
- [ ] Device held in **same orientation** as calibration
- [ ] Target racing game supports on-screen touch controls

## Test procedure

1. Open the racing game and enter a race / free drive with visible on-screen controls.
2. Switch to HandDrive → Controller → **Start Controller**.
3. Place phone so the camera sees your hand (dashboard / stand).

### Checklist

- [ ] Accessibility shows **Connected**
- [ ] Profile shows **Calibrated**
- [ ] Landscape orientation matches calibration
- [ ] Steering left/right moves game vehicle
- [ ] Default inversion feels correct (or Invert Steering toggled as needed)
- [ ] Steering returns toward center when hand centers
- [ ] Throttle engages with valid tracking (Hold mode)
- [ ] Open palm engages **Brake** and releases throttle
- [ ] Closing hand releases brake and restores throttle
- [ ] Tracking loss releases throttle, brake, and steering
- [ ] **Stop Controller** releases all input
- [ ] **Emergency Stop** releases all input immediately
- [ ] Game continues to accept Accessibility gestures after ~1–2 minutes

## Record result on profile

| Status | Meaning |
|--------|---------|
| Untested | Not tried yet |
| Works | Core steering + throttle + brake usable |
| Partially Works | Some controls fail or drop out |
| Not Compatible | Game ignores injected touches |

**Do not mark a game as Works without a real-device session.**

## Known platform limits

- `dispatchGesture` cannot hold a permanent OS-level finger forever; continuous controls use short continued strokes.
- Some engines partially ignore injected events.
- Real finger touches can cancel injected strokes.
- OEM Android builds vary.

## Games (manual results only)

| Game | Status | Notes | Device / Android |
|------|--------|-------|------------------|
| *(add after physical testing)* | | | |
