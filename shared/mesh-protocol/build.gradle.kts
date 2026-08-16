plugins {
    kotlin("jvm")
}

group = "com.syncdroid.shared"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test-junit"))
}
