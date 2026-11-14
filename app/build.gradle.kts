import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

// Pleos Playground 'My Project' 의 Client ID/Secret (서버 음성 합성·인식용). 저장소에 쓰지 않고 local.properties 에만 둔다.
// 없으면 ON_DEVICE 모드로만 동작한다 (공모전 제출 APK 는 이 상태로 충분하다).
// APK 안의 값은 누구나 꺼내 볼 수 있으므로 **debug 빌드에만** 넣고, 제출용 release APK 에는 절대 넣지 않는다.
// 공공데이터포털 키(dataGoKr.serviceKey)는 수집 도구 전용이라 앱에는 아예 읽지 않는다.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun localProp(key: String) = (localProps.getProperty(key) ?: "").replace("\"", "")

android {
    namespace = "app.yetgil.story"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.yetgil.story"
        // Pleos Connect 요구 사항: minSdk 28 이상
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "PLEOS_CLIENT_ID", "\"${localProp("pleos.clientId")}\"")
            buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${localProp("pleos.clientSecret")}\"")
        }
        release {
            isMinifyEnabled = false
            // 공모전 시연용 release APK. 배포 서명 키를 만들기 전까지는 디버그 키로 서명한다.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // 이야기 꾸러미·경로·특일 파일은 저장소의 data/ 를 그대로 앱 assets 로 쓴다 (복사본을 두지 않는다)
    sourceSets.getByName("main").assets.srcDir(rootProject.file("data"))
}

dependencies {
    implementation(project(":engine"))

    // Pleos Connect SDK
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
