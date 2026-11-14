pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK (Vehicle, NaviHelper, TextToSpeech, SpeechToText, FusedLocation, LLM)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/") {
            content { includeGroupAndSubgroups("ai.pleos.playground") }
        }
    }
}

rootProject.name = "yetgil-story"

// 순수 Kotlin 엔진. Pleos SDK에 의존하지 않는다.
include(":engine")
// 공공 데이터 → 이야기 꾸러미·특일 파일 (서버에서 돌리는 도구)
include(":collector")
// 실차 없이 가상 주행으로 엔진을 돌려 보는 시연 도구
include(":simulator")
// 차량 앱 (Android Automotive · Pleos Connect SDK). Android SDK 경로는 local.properties 의 sdk.dir
include(":app")
