import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm") version "2.4.20"
    id("java-gradle-plugin")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.mavenPublish)
    id("dev.mmauro.gitver") version "0.1.0-SNAPSHOT"
}

group = "dev.mmauro"
version = gitver.currentVersion.get()

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("giver") {
            id = "dev.mmauro.gitver"
            implementationClass = "dev.mmauro.gitver.GitVerPlugin"
            displayName = "GitVer Plugin"
            description = "Provides semantic version information from Git tags"
        }
    }
}

dependencies {
    implementation(libs.semver)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
    }
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
    explicitApi()
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.mmauro", project.name, project.version.toString())

    pom {
        name = "gitver"
        description = "A tiny Gradle plugin for Git tags based semantic versioning."
        inceptionYear = "2026"
        url = "https://github.com/MMauro94/gitver/"
        licenses {
            license {
                name = "GNU General Public License v3.0"
                url = "https://www.gnu.org/licenses/gpl-3.0.html"
                distribution = "https://www.gnu.org/licenses/gpl-3.0.html"
            }
        }
        developers {
            developer {
                id = "MMauro94"
                name = "Mauro Molin"
                email = "molin.mauro@gmail.com"
                url = "https://github.com/MMauro94"
                organization = "Mauro94"
                organizationUrl = "https://github.com/Mauro94"
            }
        }
        scm {
            url = "https://github.com/MMauro94/gitver"
            connection = "scm:git:git://github.com/MMauro94/gitver.git"
            developerConnection = "scm:git:ssh://github.com:MMauro94/gitver.git"
        }
    }
}
