# Viewport preview validation

The standalone geometry checks require a Kotlin/JVM compiler (tested with 2.0.20) and Java 17. From the repository root:

```sh
kotlinc src/android/app/src/main/java/org/citra/citra_emu/display/ViewportPlacement.kt \
  src/android/viewport-tests/ViewportPlacementTest.kt \
  -include-runtime -d /tmp/viewport-geometry-tests.jar
java -jar /tmp/viewport-geometry-tests.jar
```

The checks cover both aspect presets, width preservation, fitting oversized heights, centering, invalid values, movement bounds, and 40,000 corner resizes preserving proportions and opposite anchors.

The editor, transform and secondary-display classes were also compiled in isolation with Kotlin 2.0.20 against Android API classes and stubs for app/AndroidX dependencies. This is a syntax/type check, not a complete app build or a device test.

## Build and device checks

Use the existing Android SDK/NDK environment. From `src/android`, run:

```sh
./gradlew assembleVanillaRelWithDebInfo --max-workers=1
```

Do not run `clean` for this incremental preview build. The APK is generated at `app/build/outputs/apk/vanilla/relWithDebInfo/app-vanilla-relWithDebInfo.apk`.

On the Odin 3, check:

1. Launch with an existing saved internal placement and confirm it is retained.
2. Open **Edit screen viewports**. Select External; drag and resize its map on the Odin while watching the external display.
3. Apply 15:9 externally and 4:3 internally. Resize again and confirm each selected proportion is preserved. Check touchscreen alignment after applying the internal preset.
4. Change both displays, then Cancel or Android Back: both must return to their starting placements. Repeat with Save, restart the app and verify both placements.
5. Center and Reset each display; verify the other display is unchanged.
6. Disconnect/reconnect the external monitor during editing; confirm unsaved changes are canceled, no crash occurs, and saved placement returns. Repeat a rotation with unsaved edits.
7. With no external display active, confirm External is disabled and internal editing still works.
8. If an external touchscreen is available, verify local input alignment after resizing and confirm game touches are suppressed during editing.

The viewport includes native letterboxing; the presets do not crop it. Verify visibility and interaction on both portrait and landscape configurations before promoting the preview to the stable branch.
