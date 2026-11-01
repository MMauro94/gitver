package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Inc
import io.github.z4kn4fein.semver.Version
import java.io.Serializable

public fun interface SnapshotIncrementCalculator : Serializable {
    /**
     * Given the [version] reachable from HEAD and a list of [commits] from the last tagged version and HEAD, calculates how to
     * increment the version for a synthetic snapshot version.
     *
     * @param version the version that will be incremented. It's [GitVerExtension.lastTaggedVersion], or [Version.min] if no tagged version
     * reachable from HEAD exists.
     * @param commits list of commits between the one tagged with [version] (excluded) and HEAD (included), in topological order
     * from oldest to newest
     * @param isDirty whether there's any staged or unstaged changes
     *
     * @see GitVerExtension.lastTaggedVersion
     * @see GitVerExtension.snapshotIncrement
     */
    public fun calculate(
        version: Version,
        commits: List<GitCommit>,
        isDirty: Boolean,
    ): Inc

    public companion object {
        /**
         * The default [SnapshotIncrementCalculator] used in [GitVerExtension.snapshotIncrement].
         *
         * For any case in which there _is_ a last tagged version, this returns [Inc.PRE_RELEASE].
         *
         * If there is _not_ a last tagged version, such as on a new project, the given version is [Version.minor] and this implementation
         * returns [Inc.MINOR] so that the initial SNAPSHOT version is `0.1.0-SNAPSHOT`.
         *
         * @see GitVerExtension.snapshotIncrement
         */
        public val DEFAULT: SnapshotIncrementCalculator = { version, _, _ ->
            if (version == Version.min) {
                Inc.MINOR
            } else {
                Inc.PRE_RELEASE
            }
        }
    }
}
