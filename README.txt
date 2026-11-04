JPWalls (Android Studio project)
1. Open this folder in Android Studio and let Gradle sync.
2. For the small, optimised APK: Build > Generate Signed App Bundle / APK, or run: ./gradlew assembleRelease
   (release is minified, resource-shrunk and signed with the debug key so it installs directly).
3. UI lives in app/src/main/assets/index.html. Icon: white background, black dot (res/drawable/ic_fg.xml).
