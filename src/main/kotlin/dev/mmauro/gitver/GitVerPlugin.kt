package dev.mmauro.gitver

import dev.mmauro.gitver.tasks.PrintVersionTask
import io.github.z4kn4fein.semver.Version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

private const val TASKS_GROUP = "versioning"

public class GitVerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "gitver",
            DefaultGitVerExtension::class.java,
            project.providers,
        )

        if (project == project.rootProject) {
            project.registerPrintVersionTask(
                name = "printCurrentVersion",
                description = "Prints the current GIT-derived version.",
                version = extension.currentVersion,
            )
            project.registerPrintVersionTask(
                name = "printLatestReleaseVersion",
                description = """
                    Prints the latest repository-wide GIT-derived release version, if such version exists.
                    A release version excludes any pre-release such as -alpha, -beta, -rc.1, and so on, but includes major versions equal to 0.
                """.trimIndent(),
                version = extension.taggedVersions.map { it.latestReleaseNull() },
                missingVersionMessage = "No release version found in the repository",
            )
            project.registerPrintVersionTask(
                name = "printLastTaggedVersion",
                description = "Prints the last tagged GIT-derived version on this branch, if such version exists",
                version = extension.lastTaggedVersion,
                missingVersionMessage = "No tagged version found in the current branch",
            )
        }
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
