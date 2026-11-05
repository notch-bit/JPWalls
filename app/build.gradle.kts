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
dependencies {
    // Auto subject cut-out. "Unbundled" ML Kit: the model lives in Google Play services, so the APK stays small.
    implementation("com.google.android.gms:play-services-mlkit-subject-segmentation:16.0.0-beta1")
}
