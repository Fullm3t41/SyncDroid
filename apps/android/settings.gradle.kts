pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SyncDroid"
include(":app")
include(":mesh-protocol", ":sync-core")
project(":mesh-protocol").projectDir = file("../../shared/mesh-protocol")
project(":sync-core").projectDir = file("../../shared/sync-core")
