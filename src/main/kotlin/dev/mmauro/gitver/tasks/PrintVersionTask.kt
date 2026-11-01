package dev.mmauro.gitver.tasks

import io.github.z4kn4fein.semver.Version
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
public abstract class PrintVersionTask : DefaultTask() {

    @get:Input
    @get:Optional
    public abstract val version: Property<Version>

    @get:Input
    @get:Optional
    public abstract val missingVersionMessage: Property<String>

    @TaskAction
    public fun printVersion() {
        val version = this.version.orNull
            ?: throw GradleException(missingVersionMessage.get())

        logger.quiet(version.toString())
    }
}
