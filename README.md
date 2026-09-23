Droid64
=======

Emulating the C64 on Android devices.

Some facts:

- Optimized for TV devices (such as the Amazon Fire TV Stick).
- Supports gamepads (preferrable!)
- Uses hardware acceleration for rendering (OpenGL)
- Supports loading disk files (D64), and tape files (T64)
- Supports loading from game archives (zip file containing D64 and T64 files)
- Convenient auto-scan functionality
- Smart-touch interface for using touch screen borders and corners for special functionality

Download
---------

Download this fork's Android builds at

https://github.com/isaiahpettingill/Droid64/releases

Android 16 and arm64 builds include a native `arm64-v8a` library. To add a
disk image, open Select Disk, choose **Import disk or archive**, pick a D64,
T64, PRG, SNAP, or ZIP file, and tap **Re-Scan Disks**. Imported files and
snapshots are stored in the app's private storage. The app targets Android 15
(API 35); pull request builds compile and boot on Android 16 (API 36), and
scheduled release builds use the latest stable Android platform.
The app supports both portrait and landscape. On devices without the optional
Android reverb effect, sound continues without reverb instead of crashing at
startup.

Build locally with JDK 17, Android SDK Platform 35, Build Tools 35.0.0,
NDK 27.2.12479018, and CMake 3.22.1:

```sh
./gradlew :app:assembleDebug
```

To build against another installed platform, pass `-PandroidCompileSdk=36`.

## Automatic releases

The GitHub Actions workflow builds on pushes and pull requests. On the first
day of each month it detects the newest stable Android API, builds the APK,
launches it in an emulator for that API, and publishes a release named
`android-<API>-v1.1.0` if that release does not already exist. Pushing a
`v*` tag or starting the workflow manually also publishes a signed release.
If a build or emulator smoke test fails, no release is published.

To enable signed releases, set these GitHub repository Actions secrets using
one persistent Android signing key:

- `DROID64_KEYSTORE_B64`: base64 encoding of the keystore (`base64 -w0 your-key.jks`)
- `DROID64_STORE_PASSWORD`: keystore password
- `DROID64_KEY_ALIAS`: key alias
- `DROID64_KEY_PASSWORD`: key password

Keep the keystore backed up. Replacing it prevents APK updates over earlier
releases. CI always uploads a debug APK as a workflow artifact; its temporary
debug signature is not stable between runner instances. Android API releases
increase the APK version code by one per API level. Functional use on a Pixel 8
should still be checked on the device before treating an emulator smoke test as
full device validation.

Wiki
---------

Read the Droid64 wiki at

https://github.com/rosc77/Droid64/wiki

Copyright
---------

```
Copyright 2016 Roland Schabenberger

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Open Source
-----------

Droid64 is based on the fantastic Frodo C64 emulator written by Christian Bauer.

Frodo has been used nearly unchanged, just little integration work has been done to get
it work right. Thanks Christian for the fantastic work!

See http://frodo.cebix.net for details.
