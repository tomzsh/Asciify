# Asciify v1.1.0 build verification

Verified on 4 November 2026. Minimum supported Android version: Android 10 / API 29.

| APK | Version | Application ID | Bytes | Signing |
|---|---|---|---:|---|
| `Asciify-release.apk` | 1.1.0 (2) | `com.asciify.studio` | 4,431,822 | Local release certificate, R8 and resource shrinking enabled |
| `Asciify-debug.apk` | 1.1.0-debug (2) | `com.asciify.studio.debug` | 39,252,837 | Android debug certificate, debuggable |

Both APKs pass `apksigner verify` (APK Signature Scheme v2) and `zipalign -c -P 16 4`. The release is not debuggable. The two variants can be installed together. File SHA-256 values are in `SHA256SUMS.txt` and the individual `.apk.sha256` files.

Keep a secure backup of the private `.signing` folder to sign future updates. It is excluded from the repository.

## Automated checks

- Release APK, debug APK and debug test APK: build successful.
- JVM unit tests: **32 passed** (19 processing tests, 13 dither math tests), zero failures/errors.
- Android lint, debug and release: **zero errors**, 22 warnings per variant. Warnings concern pinned SDK/dependency/tool versions, backup-rule migration and optional KTX syntax.
- Android 15/API 35: **12 debug integration tests passed**, covering both renderers, all Dithered styles, orientation, audio, cancellation and gallery publication.
- The signed, optimized release imported synthetic footage, exported through GPU and CPU, and saved playable MP4s to the gallery.

The release smoke test used a 12 FPS target and 1 Mbps video bitrate. CPU output has a fixed 12 FPS timeline; GPU output averaged 10 FPS while preserving source timestamps. Choose CPU when a fixed output frame rate matters. Encoders use variable bitrate.

The device suite covers GPU and CPU H.264 output, selectable FPS, trimmed timestamps, retained/muted audio, CPU cancellation, actual portrait rotation metadata, absence of unintended portrait letterboxing, custom colors, and foreground-service gallery publication/history. It additionally exports all 10 Dithered presets through both renderers, compares decoded static gray/RGB calibration frames, checks fixed temporal patterns, portrait pattern coordinates, gamma/inversion, exact uncompressed two-ink output and session compatibility. H.264 comparisons allow lossy color compression; uncompressed raster tests require exact palette colors.

The GPU rendering backend is **OpenGL ES / GLSL through Media3**. Vulkan is not implemented. Device tests use the emulator's SwiftShader OpenGL ES backend; physical-device GPU performance and vendor codec behavior have not been benchmarked.

GPU tests used the emulator's SwiftShader OpenGL ES backend. Performance on physical Adreno and Mali devices has not been measured. All test footage was synthetic; source footage was not modified.
