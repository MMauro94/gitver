package dev.mmauro.gitver

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

public open class DefaultGitVerExtension @Inject constructor(
    private val providers: ProviderFactory,
    objects: ObjectFactory,
) : GitVerExtension {

    /**
     * Map of commit ID => list of tagged versions for this commit
     */
    private val commitToVersions: Provider<Map<String, List<Version>>> = providers
        // %(objectname) is the commit hash for lightweight tags, but the tag hash for annotated ones
        // %(*objectname) is the commit hash for annotated/signed tags, but it's empty for lightweight tags
        .exec { it.commandLine("git", "tag", "--format", "%(objectname) %(*objectname) %(refname:short)", "--list", VERSION_FILTER) }
        .standardOutput
        .asText
        .map { stdout ->
            stdout
                .split("\n")
                .reversed()
                .asSequence()
                .filter { it.isNotEmpty() }
                .map { it.split(' ', limit = 3) }
                .filter { (_, _, tag) -> tag.looksLikeVersionTag() }
                .groupBy(
                    keySelector = { (hash, peeledHash) -> peeledHash.takeIf { it.isNotBlank() } ?: hash },
                    valueTransform = { (_, _, tag) -> Version.fromTag(tag) },
                )
        }

    override val lastTaggedVersion: Provider<Version> = commitToVersions.flatMap { commitToVersions ->
        providers
            .exec {
                it.commandLine("git", "log", "HEAD", "--format=%H")
                it.isIgnoreExitValue = true
            }
            .standardOutput
            .asText
            .map { stdout ->
                stdout
                    .trim()
                    .split("\n")
                    .firstNotNullOfOrNull { hash -> commitToVersions[hash]?.max() }
            }
    }

    override val commitsSinceLastTaggedVersion: Provider<List<GitCommit>> = lastTaggedVersion
        .map { "${it.toTag()}..HEAD" }
        .orElse("HEAD")
        .flatMap { range ->
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

    private val isDirty = providers
        .exec { it.commandLine("git", "status", "--porcelain") }
        .standardOutput
        .asText
        .map { it.isNotBlank() }

    override val currentVersion: Provider<Version> = lastTaggedVersion.orElse(Version.min).flatMap { last ->
        isDirty.flatMap { isDirty ->
            when (val current = if (isDirty) null else pointedAtVersion.orNull?.max()) {
                null -> last.incrementVersionForSnapshot(isDirty)
                last -> providers.provider { last }
                else -> error("Impossible state: current version commit ($current) is different than latest ($last)")
            }
        }
    }

    override val taggedVersions: Provider<List<Version>> = commitToVersions.map { it.values.flatten().sorted() }

    override val snapshotIncrement: Property<SnapshotIncrementCalculator> = objects
        .property(SnapshotIncrementCalculator::class.java)
        .convention(SnapshotIncrementCalculator.DEFAULT)

    private fun Version.incrementVersionForSnapshot(isDirty: Boolean): Provider<Version> {
        return snapshotIncrement.flatMap { calculator ->
            commitsSinceLastTaggedVersion.map { commits ->
                val increment = calculator.calculate(
                    version = this,
                    commits = commits,
                    isDirty = isDirty,
                )
                inc(increment, preRelease = "SNAPSHOT")
            }
        }
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
