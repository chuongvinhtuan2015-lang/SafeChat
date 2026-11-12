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
        mavenCentral() // Bắt buộc để tải canhub cropper
        maven("https://jitpack.io") // Bắt buộc cho các sub-dependencies của cropper
    }
}

rootProject.name = "SafeChat"
include(":app")