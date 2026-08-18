plugins {
    kotlin("jvm")
}

group = "com.syncdroid.shared"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":mesh-protocol"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.json:json:20250517")
    testImplementation(kotlin("test-junit"))
}
