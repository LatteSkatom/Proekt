// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Google Services plugin (Firebase)
        classpath("com.google.gms:google-services:4.4.2")
    }
}

plugins {
    // Android & Kotlin plugins (через Version Catalog)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
