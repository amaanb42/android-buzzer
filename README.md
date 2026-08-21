# Android Buzzer

Android Buzzer is a small Jetpack Compose remote for the bedroom unit in the
[`wireless-buzzer`](../../wireless-buzzer/README.md) project. It uses the buzzer's
local Wi-Fi HTTP API and mirrors state changes made by either physical button.

## Behavior

- The app polls `http://192.168.50.50/api/status` every 500 ms while its
  screen is visible.
- **Ring** sends `POST /api/ring`; **Stop** sends `POST /api/stop`.
- The background is pastel red while stopped and pastel blue while ringing,
  with deeper matching colors in dark mode.
- Successful Ring and Stop actions produce semantic haptic feedback. Failed
  actions produce rejection feedback and a visible error.
- Ring and Stop update immediately, and rapid taps converge on the latest choice.
  Commands preempt polling, retry once, and verify status before reporting an error.
- A single failed status request keeps the last confirmed state and connection.
  **Offline** appears after three consecutive failures, and polling automatically
  recovers when the buzzer becomes reachable again.

The endpoint is intentionally fixed because the firmware API is unauthenticated
HTTP. Router 1 must reserve `192.168.50.50` for the ESP32's Wi-Fi MAC address;
do not create a duplicate reservation on Router 2. Phones connected through
Router 2 must be able to route to `192.168.50.50` on Router 1's network. After
restarting the ESP32, verify the reservation and route from each relevant
network with:

```bash
curl --noproxy '*' http://192.168.50.50/api/status
```

Android's cleartext network policy permits local HTTP application-wide, while
the app keeps the endpoint hardcoded to prevent redirection to arbitrary hosts.

## Platform and permissions

The app supports Android 12 and newer (`minSdk 31`) and targets Android 17
(`targetSdk 37`). Android 17 requires the user to grant local-network access;
older supported versions need only the normal Internet permission.

## Build

Use JDK 17 and an Android SDK containing platform 37:

```bash
./gradlew test lint assembleDebug
```

With a device or emulator available, run the Compose tests with:

```bash
./gradlew connectedDebugAndroidTest
```

The debug APK is written under `app/build/outputs/apk/debug/`.

## Structure

- `data/HttpBuzzerApi.kt` implements the three HTTP calls.
- `data/BuzzerRepository.kt` serializes calls and provides foreground polling.
- `ui/BuzzerViewModel.kt` holds the last confirmed buzzer and connection state.
- `ui/BuzzerScreen.kt` contains the single Material 3 Expressive screen.

Material 3 is pinned to the `1.5.0-alpha26` Expressive track so the app can use
the expressive motion scheme, morphing button shapes, and loading indicator.
