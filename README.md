# LumenWave Visualizer

**A desktop-inspired floating audio spectrum for Android.**

LumenWave is a native Kotlin Android project. Its floating panel takes visual cues from macOS window controls and Linux desktop widgets: three traffic-light controls, a translucent dark surface, a draggable title strip, and a resize grip.

## Project snapshot

- App: **LumenWave Visualizer**
- Package: `dev.lumenwave.app`
- Version: `1.0.0` (version code `1`)
- Minimum Android: **Android 10 / API 29**
- Target / compile SDK: **Android 16 / API 36**
- Language: Kotlin
- Shizuku API: `13.1.5`
- Audio path: Android `AudioPlaybackCapture` + `MediaProjection`

## What is implemented

- Animated launch screen and a first-run permission checklist.
- Shizuku Binder detection and permission request.
- Overlay permission and battery-optimization setup shortcuts.
- Notification permission flow on Android 13+.
- System playback capture for the `MEDIA`, `GAME`, and `UNKNOWN` audio usages.
- Foreground capture service with an ongoing notification and Stop action.
- Floating window that can be dragged from its title area and resized from the lower-right grip.
- Red = stop, yellow = collapse/restore, green = expand/restore.
- Rainbow, custom three-tone gradient, and single-color palettes.
- Three editable palette slots, glow, peak markers, opacity, bar count, sensitivity, smoothing, bar height, spacing, and window dimensions.
- FFT-based spectrum analysis in memory. Audio is not written to storage.
- GitHub Actions workflow that builds and publishes a debug APK artifact.

## Important platform facts

Shizuku is included for the requested authorization and integration status. It does **not** grant a normal app unrestricted audio privileges and cannot override Android's protected-playback policy.

Internal playback capture is a separate Android security flow. Although Android presents a MediaProjection consent dialog, LumenWave uses the projection token only to configure audio playback capture; it does not create a screen-recording VirtualDisplay or save screen images.


1. The user grants the `RECORD_AUDIO` runtime permission.
2. Android displays the MediaProjection playback-capture consent prompt.
3. The audio-producing app must allow playback capture. Android normally captures media/game/unknown usages only when the source app's manifest and player capture policy permit it.

Some music services, protected media, calls, and some games deliberately block playback capture. In those cases LumenWave may show silence even though its service is running. This is an Android platform restriction, not a Shizuku authorization failure.

The MediaProjection session is user-approved and session-scoped. Start a new session and approve the system prompt again after stopping, rebooting, or losing the capture session. Swiping LumenWave's activity away does not intentionally stop the foreground service, but Force stop, process termination, battery management, or vendor-specific restrictions can still end it.

## Shizuku setup

Install Shizuku from the official project page: <https://shizuku.rikka.app/download/>.

- Android 11 and newer: Shizuku can be started with Wireless debugging from the device itself.
- Android 10: non-root setup generally requires ADB from a computer; rooted devices can use the root start method.
- Return to LumenWave, tap **Configure** next to Shizuku, and approve the app's Shizuku permission prompt.

The Shizuku service must be running and authorized. ADB-backed Shizuku generally needs to be started again after a device reboot.

## Build in Android Studio

This ZIP contains the complete Android Studio source project and CI workflow; it is not a precompiled installable APK. An Android SDK build is required to produce the APK.

1. Install Android Studio Meerkat or newer, JDK 17, and Android SDK Platform 36.
2. Open this `LumenWave` folder in Android Studio.
3. Allow Gradle sync to finish. Android Studio may download the Android Gradle Plugin and Shizuku dependencies.
4. Select the `app` run configuration and run it on an Android 10+ device.
5. To build a debug APK, run:

   ```bash
   ./gradlew assembleDebug
   ```

   On Windows:

   ```bat
   gradlew.bat assembleDebug
   ```

   Output: `app/build/outputs/apk/debug/app-debug.apk`.

The wrapper scripts bootstrap Gradle 8.13 if Gradle is not installed. An internet connection is needed for the first build to fetch Gradle, the Android plugin, SDK components, and dependencies.

## Build on GitHub

1. Create a new GitHub repository named `LumenWave`.
2. Upload the contents of this folder to the repository root (not the outer ZIP directory).
3. Push to `main` or `master`, or run the **Android CI** workflow manually from the Actions tab.
4. Open the completed workflow run and download the `LumenWave-debug-apk` artifact.

The workflow builds a debug APK, not a Play Store release. For release distribution, create and protect your own signing key, configure signing secrets, and add a signed release workflow. Never commit a keystore or passwords to Git.

## First launch checklist

1. Start Shizuku and authorize LumenWave.
2. Allow display over other apps.
3. Allow background operation / battery-optimization exemption.
4. Allow notifications.
5. Tap **Next**.
6. Open **Home → Start Visualizer**.
7. Grant Android's audio permission, then approve the MediaProjection prompt.
8. Play audio in a compatible app. The floating spectrum should react to the captured signal.

## Troubleshooting

**Shizuku stays amber** — Open Shizuku, start its service, return to LumenWave, and approve the authorization request.

**The overlay does not appear** — Recheck the special "Display over other apps" permission for LumenWave. Some OEMs have additional background pop-up controls.

**The bars stay flat** — Check that Android's MediaProjection prompt was approved, that the source app permits capture, and that audio is actually playing. Try an ordinary local music player or a game known to allow playback capture. Protected apps can intentionally return silence.

**The service stops after a while** — Review the manufacturer's autostart/background activity controls, keep the battery exemption enabled, and avoid Force stop. Android and OEM task managers can still terminate processes.

**The window is hard to move** — Drag the title strip rather than the bars. Use the bottom-right `◢` grip to resize.

## Design / behavior notes

- The floating surface does not take keyboard focus, so normal app interaction can continue around it.
- The panel is not draggable by touching its bars; use its header.
- The green window control expands the panel to nearly the full screen width; yellow collapses it; red stops capture and removes the panel.
- Capture is opt-in and visible through Android's foreground-service / MediaProjection UI.

## License

The LumenWave source in this repository is provided under the MIT License. The Shizuku API is consumed as an external dependency and remains under its own license.

## Technical references

- Android playback capture: <https://developer.android.com/media/platform/av-capture>
- `AudioPlaybackCaptureConfiguration`: <https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration>
- MediaProjection lifecycle and foreground-service requirements: <https://developer.android.com/media/grow/media-projection>
- Shizuku API developer guide: <https://github.com/RikkaApps/Shizuku-API>
- Shizuku download: <https://shizuku.rikka.app/download/>
