
// build.gradle.kts (Project: SafeChat)
plugins {
    alias(libs.plugins.android.application) apply false // hoặc id("com.android.application") version "..." apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}