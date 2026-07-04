# Android lint status

Current Android lint cleanup status:

- Semgrep scan: 0 findings
- Android i18n strict check: OK
- `./gradlew :app:lintDebug`: OK
- `./gradlew :app:assembleDebug`: OK
- Lint warnings reduced from 839 to 11

The remaining lint warnings are dependency/toolchain update notices, not app-code lint
findings.

## Deferred warning groups

### Toolchain / compileSdk 37 group

These updates are intentionally deferred because the latest AndroidX runtime
versions require compileSdk 37 or later, while the app currently builds with
android-36.1.

Deferred examples:

- Gradle wrapper 9.3.1 -> 9.6.1
- Android Gradle Plugin 9.1.1 -> 9.2.1
- androidx.core:core-ktx 1.10.1 -> 1.19.0
- androidx.lifecycle:lifecycle-runtime-ktx 2.6.1 -> 2.11.0
- androidx.activity:activity-compose 1.8.0 -> 1.13.0
- androidx.compose:compose-bom 2024.09.00 -> 2026.06.01
- org.jetbrains.kotlin.plugin.compose 2.2.10 -> 2.4.0

These should be handled together as a separate compileSdk/toolchain upgrade.

### Networking major-version group

These updates are intentionally deferred because they are major-version changes
in security-sensitive networking code:

- Retrofit 2.11.0 -> 3.0.0
- OkHttp 4.12.0 -> 5.4.0

DNA-Nexus uses pinned TLS / QR-SPKI / authenticated API connections, so OkHttp
and Retrofit major upgrades should be tested separately with login, pairing,
file transfer, and pinned TLS flows.
