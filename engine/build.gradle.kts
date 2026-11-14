plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // 저장소의 data/packs 를 테스트에서 검증한다.
    systemProperty("yetgil.packDir", rootProject.file("data/packs").absolutePath)
}
