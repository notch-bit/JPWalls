JPWalls (Android Studio project)
1. Open this folder in Android Studio and let Gradle sync (needs internet once: it now pulls Google ML Kit).
2. For the small, optimised APK: Build > Generate Signed App Bundle / APK, or run: ./gradlew assembleRelease
   (release is minified, resource-shrunk and signed with the debug key so it installs directly).
3. UI lives in app/src/main/assets/index.html. Icon: white background, black dot (res/drawable/ic_fg.xml).

NEW IN THIS VERSION
- 7 new clock styles: Wide light, 3D extrude, Duo tone, Pill, Big back, Sticker, In words.
- Font choice for every style (Modern sans / Serif / Mono / Condensed).
- Studio: move clock left/right, rotate it, put it in front of or behind the subject, save/apply clock presets.
- Auto cut-out: one tap finds the subject in the foreground photo (Google ML Kit subject segmentation,
  runs through Google Play services; the first use may need internet to download the model).
- Depth motion: background and subject drift in opposite directions on phone tilt and home-screen scroll.

WHERE THINGS ARE
- Every clock style exists twice and must be kept in sync: JS preview in index.html (STYLES) and
  the Java live-wallpaper renderer in ClockRenderer.java (drawStyle).
- LiveWallpaperService.java: pre-scales layers, caches the clock as a bitmap, runs the depth motion.
- MainActivity.java: Bridge.autoCutout() calls ML Kit and sends the mask back to the page (window.onCutout).
