# Traccar Client SDK — Places offline fork

This Apache-2.0 iOS fork retains the Traccar location filter, Core Location adapter,
Core Motion stop detector and stationary-region wakeups. It delivers accepted points
to a host-provided `OfflineOutput`, which must commit them to permanent local storage.
There is no HTTP client, server URL, device ID, retry queue or telemetry in the framework.

See [FORK.md](FORK.md) for provenance, changes and lifecycle requirements.
The Android, Flutter and React Native source directories are retained upstream reference
material, not built or distributed by this iOS fork.

## Build and test

Requires JDK 21 and Xcode. Versions are pinned in `gradle/libs.versions.toml`.

```sh
./gradlew :core:iosSimulatorArm64Test
./gradlew :core:assembleTraccarClientSDKReleaseXCFramework
python3 scripts/check_offline.py
```

Release packaging uses the generated XCFramework, `swift package compute-checksum`,
and an immutable GitHub release URL in Package.swift. There is no automatic initializer;
the host constructs `OfflineTracker` after local storage is unlocked and permissions are
available. Call `close` and drain host writes before deleting or restoring history.

Motion and region opportunities remain controlled by iOS. Measure physical-device battery
and background behavior; a simulator build cannot establish energy savings.
