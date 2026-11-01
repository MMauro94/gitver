package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Inc
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.inc
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.process.ExecOutput
import javax.inject.Inject
import kotlin.text.contains

private const val VERSION_FILTER = "v*"

open class DefaultGitVerExtension @Inject constructor(
    private val providers: ProviderFactory,
    objects: ObjectFactory,
) : GitVerExtension {

    /**
     * Map of commit ID => list of tagged versions for this commit
     */
    private val commitToVersions: Provider<Map<String, List<Version>>> = providers
        .exec { it.commandLine("git", "tag", "--format", "%(objectname) %(refname:short)", "--list", VERSION_FILTER) }
        .standardOutput
        .asText
        .map { stdout ->
            stdout
                .split("\n")
                .reversed()
                .asSequence()
                .filter { it.isNotEmpty() }
                .map { it.split(" ", limit = 2) }
                .filter { (_, tag) -> tag.looksLikeVersionTag() }
                .groupBy(
                    keySelector = { (hash) -> hash },
                    valueTransform = { (_, tag) -> Version.fromTag(tag) },
                )
        }

    override val lastTaggedVersion = commitToVersions.flatMap { commitToVersions ->
        providers
            .exec { it.commandLine("git", "log", "HEAD", "--format=%H"); it.isIgnoreExitValue = true }
            .standardOutput
            .asText
            .map { stdout ->
                stdout
                    .trim()
                    .split("\n")
                    .firstNotNullOfOrNull { hash -> commitToVersions[hash]?.max() }
            }
    }

    override val commitsSinceLastTaggedVersion = lastTaggedVersion.map { "${it.toTag()}..HEAD" }.orElse("HEAD").flatMap { range ->
        providers
            .exec {
                it.commandLine("git", "log", "--reverse", "--format=${GitCommit.GIT_FORMAT}", range)
                it.isIgnoreExitValue = true
            }
            .mapStdOutputIgnoringMissingHead { GitCommit.parseList(it) }
    }

    private val pointedAtVersion = commitToVersions.flatMap { commitToVersions ->
        providers
            .exec {
                it.commandLine("git", "rev-parse", "HEAD")
                it.isIgnoreExitValue = true
            }
            .mapStdOutputIgnoringMissingHead { commitToVersions[it.trim()] }
    }

    override val currentVersion = lastTaggedVersion.orElse(Version.min).map { latest ->
        when (val current = pointedAtVersion.orNull?.max()) {
            null -> latest.incrementVersionForSnapshot()
            latest -> latest
            else -> error("Impossible state: current version commit ($current) is different than latest ($latest)")
        }
    }

    override val taggedVersions = commitToVersions.map { it.values.flatten().sorted() }

    override val latestStableVersion = taggedVersions.map { versions ->
        versions.filter { it.preRelease == null }.maxOrNull()
    }

    override val snapshotIncrement: Property<Inc> = objects
        .property(Inc::class.java)
        .convention(Inc.PRE_RELEASE)

    override val snapshotIncrementCalculator: Property<SnapshotIncrementCalculator> = objects
        .property(SnapshotIncrementCalculator::class.java)

    private fun Version.incrementVersionForSnapshot(): Version {
        val calculator = snapshotIncrementCalculator.orNull
        val increment = calculator?.calculate(this, commitsSinceLastTaggedVersion.get()) ?: snapshotIncrement.get()
        return inc(increment, preRelease = "SNAPSHOT")
    }
}

private fun <T : Any> ExecOutput.mapOutput(
    mapper: (exitValue: Int, stdOut: String, stdErr: String) -> T?,
): Provider<T> {
    return result.map {
        mapper(
            it.exitValue,
            standardOutput.asText.get(),
            standardError.asText.get(),
        )
    }
}

private fun <T : Any> ExecOutput.mapStdOutputIgnoringMissingHead(mapper: (stdOut: String) -> T?): Provider<T> {
    return mapOutput { exitValue, stdOut, stdErr ->
        when (exitValue) {
            0 -> mapper(stdOut)
            128 if stdErr.contains("unknown revision or path not in the working tree", ignoreCase = true) -> mapper(stdOut)
            else -> error("Unexpected GIT result (exit code: $exitValue, output: $stdOut, error: $stdErr)")
        }
    }
}

