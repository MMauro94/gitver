pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gitver"
