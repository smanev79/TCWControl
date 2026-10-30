# TCW Control

Android app that monitors and controls Teracom TCW devices over HTTP. It polls each device's
`status.xml` (with optional basic authentication) and shows sensors, analog/virtual inputs,
digital inputs and relays, with alarm values in red.

## Modules

- `core` — plain Kotlin/JVM. The common `DeviceStatus` model, the `status.xml` parsers and the
  HTTP client. No Android dependencies, so the parsers are unit tested on the JVM.
- `app` — Jetpack Compose UI: device list, add/edit device, device details with relay control.

## Supported devices

| Layout | Detected by | Models tested | Relay command |
|---|---|---|---|
| Modern (fw 2.x) | `<DeviceInfo>` | TCW241, TCW220, TCW210TH | `status.xml?ron=n` / `rof=n` / `rpl=n`, n = 1,2,4,8 |
| Classic CM | `<Device>` | TCW181B-CM, TCW122B-CM | `status.xml?rN=1` / `rN=0` / `plN=1` |

Sample files for every model are in `core/src/test/resources`. To support a new model, add its
`status.xml` there and a test in `StatusParserTest`; if its layout differs from both families,
add a parser next to `ModernParser` and `ClassicParser`.

## Build

```
./gradlew :core:test :app:assembleDebug
```

The GitHub Actions workflow runs the same command and uploads the debug APK as an artifact.

The latest debug APK from `main` is published as the `latest` pre-release:
https://github.com/smanev79/TCWControl/releases/download/latest/TCWControl-debug.apk
