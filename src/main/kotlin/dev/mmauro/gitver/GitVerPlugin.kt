package dev.mmauro.gitver

import dev.mmauro.gitver.tasks.PrintVersionTask
import io.github.z4kn4fein.semver.Version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

private const val TASKS_GROUP = "versioning"

class GitVerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "gitver",
            DefaultGitVerExtension::class.java,
            project.providers,
        )

        project.registerPrintVersionTask(
            name = "printCurrentVersion",
            description = "Prints the current GIT-derived version.",
            version = extension.currentVersion,
        )
        project.registerPrintVersionTask(
            name = "printLatestStableVersion",
            description = "Prints the latest stable repository-wide GIT-derived version, if such version exists.",
            version = extension.latestStableVersion,
            missingVersionMessage = "No stable version found in the repository"
        )
        project.registerPrintVersionTask(
            name = "printLastTaggedVersion",
            description = "Prints the last tagged GIT-derived version on this branch, if such version exists",
            version = extension.lastTaggedVersion,
            missingVersionMessage = "No tagged version found in the current branch"
        )
    }

    private fun Project.registerPrintVersionTask(
        name: String,
        description: String,
        version: Provider<Version>,
        missingVersionMessage: String? = null,
    ) {
        tasks.register(name, PrintVersionTask::class.java) {
            it.group = TASKS_GROUP
            it.description = description

            it.version.set(version)

            if (missingVersionMessage != null) {
                it.missingVersionMessage.set(missingVersionMessage)
            }
        }
    }
}
