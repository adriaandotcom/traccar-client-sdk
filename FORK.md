# Places offline iOS fork

Based on traccar/traccar-client-sdk commit 9037a8866c690ea20934b1da12868e1cf221a2bc (Apache-2.0).
Original license and copyright are retained. Modified by the Places project, October 2026.

The iOS source, location filter, motion detector, stationary region detector and signal engine
come from upstream. This fork replaces server configuration, HTTP uploading, network monitoring,
SQLDelight retry queue, log database, heartbeat and automatic initialization with OfflineOutput.
Only iOS targets are built; the upstream Android/Flutter/React Native samples are reference only.
No server SDK package is published by this fork.

OfflineOutput.save must acknowledge only a durable local commit. Failed writes stop collection.
The host restores the last point and stationary state and starts only after protected storage is
available. It controls opt-in and permissions. No SDK permission prompt is issued.

Changes also retain significant-change recovery while stationary, enable platform auto-pause,
and remove only the SDK's own stationary region when stopping (never host regions).
No raw coordinate logging remains. No HTTP dependency is compiled into this framework.

Build with JDK 21 and Xcode: ./gradlew :core:assembleTraccarClientSDKReleaseXCFramework
