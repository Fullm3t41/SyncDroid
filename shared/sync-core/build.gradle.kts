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
    testImplementation(kotlin("test-junit"))
}
