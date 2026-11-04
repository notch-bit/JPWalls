plugins { id("com.android.application") }
android {
    namespace = "com.jpwalls"
    compileSdk = 34
    defaultConfig { applicationId = "com.jpwalls"; minSdk = 29; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // replace with your own key to publish
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
// No dependencies on purpose: keeps the APK tiny.
