# Asciify

A native, offline Android video-to-ASCII studio. Built with Kotlin and Jetpack Compose, with an OLED charcoal interface and mint accents. Interface language: English.

## Features

- Import videos with Android's document picker, without full-library storage access.
- Live ASCII video preview, Original comparison, playback and scrubbing. CPU still-frame preview if the GPU player fails.
- **18 ASCII styles:** Classic, Matrix, True color, Newsprint, Amber CRT, Blueprint, Binary, Neon wave, Block party, Dot matrix, Rose quartz, Arctic, Typewriter, Source code, Stardust, Noir, Solar flare and Minimal.
- **10 Dithered styles:** Bayer 2×2, Bayer 4×4, Bayer 8×8, Bayer 16×16, Halftone Dots, Diagonal Ink, Crosshatch, Stipple, Retro 4-tone, and Color Dither.
- Searchable style categories and favorites; adjustable character/pixel detail, contrast, brightness, gamma, original colors and inversion.
- Dither controls: fixed spatial patterns, amount, style-default/2/4/8 tone levels, and linear-light processing. Original-color dithering applies levels per RGB channel.
- Independent character/background color palettes, custom six-digit HEX colors, source-video colors, and a reset to the selected style's original palette. Colors are shared by preview, GPU export and CPU export.
- Trim with a range selector or precise start/end times.
- **MP4 / H.264 export:** 480p, 720p, 1080p or source size; 12, 15, 24, 25, 30 or 60 FPS; 1–30 Mbps; optional original audio.
- Auto, GPU and CPU processing. Auto retries the independent CPU renderer after GPU decode/render/encode failure.
- Background export with progress notification, cancel, gallery publication, persistent history, playback and Android sharing.
- Settings, trim, last source and favorites survive reopening. A procedural sample scene works before importing footage.
- No accounts, network permission, uploads, analytics, watermark or subscription.

## Install / build

Requirements: Android Studio with JDK 21, Android SDK 36, and an Android 10/API 29 or newer device.

Open this directory in Android Studio, let Gradle sync, and run the `app` configuration. Android Studio creates `local.properties` with your SDK location.

```sh
./gradlew :app:assembleDebug :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Download the signed release APK and its checksum from [GitHub Releases](https://github.com/tomzsh/Asciify/releases/latest). Local build outputs are available in `app/build/outputs/apk/`.

| Build | App name | Application ID | Purpose |
|---|---|---|---|
| Debug | Asciify Debug | `com.asciify.studio.debug` | Debuggable development build, default debug certificate |
| Release | Asciify | `com.asciify.studio` | Signed, optimized with R8 and resource shrinking |

The two new variants can be installed together. Earlier development APKs used the release application ID with a debug certificate; Android cannot update that older installation directly with the new release certificate. Keep any settings you need before removing an older development installation.

Release signing is loaded from the local `.signing/release.properties` file. The release keystore is `.signing/asciify-release.p12`; its credentials and keystore are excluded from Git and have private filesystem permissions. **Keep a secure backup of the whole `.signing` folder: the same signing key is needed for future APK updates.** On another checkout, configure this folder using `config/signing.properties.example`; debug builds do not require the release signing files. Do not commit the real signing files.

Gradle output paths: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

For device integration tests:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The integration suite targets debug so it can call the processing APIs directly. The optimized release is checked through its public UI; R8 can remove internal methods/classes used by the instrumentation runner.

The bundled test fixture is a two-second synthetic H.264/AAC color test pattern generated with FFmpeg. Tests verify GPU and CPU MP4 output, trim timestamps, actual sample counts, retained/muted audio, CPU cancellation, and foreground-service gallery publication. Dither tests additionally cover all 10 presets on CPU/GPU, spatial/temporal agreement on static gray/RGB calibration footage, portrait orientation, custom palettes, gamma/inversion, and old-session defaults.

## Processing design

`engine/AsciiEffect.kt` loads the GLSL character shader, samples a downscaled cell grid and maps luminance to a generated glyph atlas. The atlas and color definitions are shared with `CpuAsciiRenderer`. Dithered styles use the same shader pipeline with a tiny threshold-rank texture, sharing deterministic maps with the CPU renderer. Dither quantization and ink blending run in linear light; the threshold pattern never depends on the frame timestamp. Media3 Transformer supplies GPU decoding/effects/encoding and AAC audio output. The preview uses the same `AsciiEffect` with ExoPlayer.

`engine/CpuExporter.kt` is a separate fallback: MediaMetadataRetriever samples frames, Canvas draws the ASCII glyphs, CPU code converts RGB to YUV, and MediaCodec accepts image buffers for H.264 encoding. It does not use OpenGL for character rendering. Sampling bitmaps and pixel arrays are reused across frames, and monochrome character paint avoids repeated color-filter allocation. Raster dithering writes a logical pixel grid and scales it with nearest-neighbor sampling. Compatible software codecs are preferred in CPU mode; Android may still accelerate source decoding or video encoding internally.

`export/ExportService.kt` owns rendering and cancellation. It snapshots the settings, renders to app-private cache, copies the completed MP4 into a pending MediaStore entry, then publishes it to **Movies/Asciify**. Failures and cancellation remove temporary files and pending output. Source files are never overwritten.

## Behavior and limits

- Output preserves source aspect ratio, uses even dimensions and never upscales. The resolution labels refer to output height.
- FPS is capped to the source rate. GPU frame dropping approximates the selected rate while preserving source timing; CPU mode samples a regular timeline. GPU export history shows the selected target, and the actual encoded rate can be lower. In testing, 30 FPS footage produced about 10 FPS with a 12 FPS GPU target. Choose CPU when a fixed output frame rate matters. Neither mode interpolates new motion frames.
- Estimated size is approximate because Android encoders use variable bitrate and containers add overhead; audio estimate uses 128 kbps. Container overhead is more noticeable on very short clips.
- GPU mode converts retained audio to AAC. CPU mode copies compatible AAC/MP3 audio with trim-adjusted timestamps. Other audio formats produce an explicit error: disable audio or choose GPU processing to convert them.
- Supported input formats, resolutions, frame rates and HDR decoding depend on device codecs. GPU export requests SDR tone mapping; CPU HDR behavior follows the platform frame retriever.
- CPU mode is slower. Prefer 480p/720p and moderate character density on lower-end devices.
- Android controls background service lifetime and may stop exceptionally long exports. An interrupted export is identified on the next launch; progress is not resumed automatically.
- History stores up to 100 entries. Removing an entry removes it from the list; the video remains in the gallery. Android's gallery can delete the physical file.

## Project layout

```text
app/src/main/java/com/asciify/studio/
  model/     immutable settings, styles, export planning and luminance math
  engine/    shared glyph atlas, CPU renderer, GPU effect, video exporters
  data/      preferences, session and export history
  export/    foreground service and MediaStore publication
  ui/        Compose studio, style gallery, export settings and history
```

Pinned dependencies: AGP 8.13.2, Kotlin 2.3.20, Compose BOM 2025.12.01, Media3 1.11.1. The Compose BOM is pinned to the SDK-36-compatible line rather than the newer SDK-37 line.

The GPU backend uses OpenGL ES via Media3; Vulkan has not been added.
