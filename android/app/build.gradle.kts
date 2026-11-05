plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.yg.tokenvault"
    compileSdk = 35
    // AGP 8.7 的默认 build-tools 是 34.0.0，这里跟 compileSdk 对齐用 35.0.0
    buildToolsVersion = "35.0.0"

    signingConfigs {
        // Fixed keystore: every build is signed by the same keycoffer.jks.
        // Changing it breaks in-place upgrades: users would have to uninstall (and lose data).
        create("release") {
            val fixed = file("../keystore/keycoffer.jks")
            // 固定签名文件不进公开仓库：本机有就用它，没有就退回本机 debug 签名（能构建，但签名会变）
            if (fixed.exists()) {
                storeFile = fixed
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.yg.tokenvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.5.2"
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 用 debug 签名方便本地直接生成可安装包；正式发布请替换为自有 keystore
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    // BiometricPrompt 需要 FragmentActivity 宿主
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.google.zxing:core:3.5.3")

    // 相机扫码：CameraX + 预览
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 本地 JVM 单元测试：验证 TOTP/Base32 算法（不依赖 Android 框架）
    testImplementation("junit:junit:4.13.2")
    // local JVM test dep: real org.json instead of Android stub
    testImplementation(files("libs/json-20240303.jar"))
}