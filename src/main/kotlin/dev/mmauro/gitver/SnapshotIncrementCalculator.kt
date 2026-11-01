package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Inc
import io.github.z4kn4fein.semver.Version
import java.io.Serializable

fun interface SnapshotIncrementCalculator : Serializable {
    /**
     * Given the [lastTaggedVersion] reachable from HEAD and a list of [commits] from the last tagged version and HEAD, calculates how to
     * increment the version for a synthetic snapshot version.
     *
     * @param lastTaggedVersion the last tagged version
     * @param commits list of commits between the one tagged with [lastTaggedVersion] (excluded) and HEAD (included), from oldest to newest
     *
     * @see GitVerExtension.lastTaggedVersion
     * @see GitVerExtension.snapshotIncrementCalculator
     */
    fun calculate(
        lastTaggedVersion: Version,
        commits: List<GitCommit>,
    ): Inc
}
