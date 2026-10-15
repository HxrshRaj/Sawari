plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Optional: keep build output out of synced folders (OneDrive/Dropbox lock files mid-build).
// Set sawari.buildRoot=C:/some/dir in ~/.gradle/gradle.properties. Unset on CI, so nothing changes there.
(findProperty("sawari.buildRoot") as String?)?.let { root ->
    subprojects { layout.buildDirectory.set(file("$root/${project.name}")) }
}
