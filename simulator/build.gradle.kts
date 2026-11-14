plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine"))
}

application {
    mainClass.set("app.yetgil.simulator.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

// ./gradlew :simulator:run 의 상대 경로(data/...)를 저장소 루트 기준으로
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
