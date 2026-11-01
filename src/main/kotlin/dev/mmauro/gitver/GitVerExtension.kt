package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Inc
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.inc
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

public interface GitVerExtension {
    /**
     * Returns the latest version tag reachable from the current commit (HEAD).
     *
     * **Important**: This is not necessarily the latest version tag in the entire repository.
     * Only tags reachable from the current branch are considered.
     *
     * For example, consider the following GIT history:
     * ```mermaid
     * gitGraph
     *     commit id: "A" tag:"v1.0.0"
     *     commit id: "B"
     *     branch feature
     *     checkout main
     *     commit id: "C"  tag:"v1.1.0"
     *     commit id: "D"
     *     commit id: "HEAD"
     *     checkout feature
     *     commit id: "E" tag: "v2.0.0"
     * ```
     *
     * In this case this provider returns `1.1.0`.
     */
    public val lastTaggedVersion: Provider<Version>

    /**
     * Returns a list of all commits between the last tagged version (excluded) and HEAD (included), from oldest to newest.
     *
     * If there is no last tagged version, this returns all commits since the root.
     */
    public val commitsSinceLastTaggedVersion: Provider<List<GitCommit>>

    /**
     * Returns the version assigned to the currently checked-out commit (HEAD).
     *
     * The returned version depends on whether the current commit has a version tag:
     * - **Tagged** commit: Returns the exact version associated with the tag (in this case it would be identical to [lastTaggedVersion])
     * - **Untagged** commit: Returns the next patch version after lastTaggedVersion, with a SNAPSHOT pre-release identifier.
     */
    public val currentVersion: Provider<Version>

    /**
     * Returns all version tags in the repository, regardless of whether they are rachable from the current branch, sorted in ascending
     * semantic version order.
     *
     * This includes pre-release versions (e.g. betas). SNAPSHOT versions are included only if they are explicitly tagged.
     *
     * Only tags matching the `v*` pattern are considered.
     *
     * For example, consider the following GIT history:
     * ```mermaid
     * gitGraph
     *     commit id: "A" tag: "v1.0.0"
     *     commit id: "B"
     *     commit id: "C" tag: "v1.1.0"
     *     branch feature
     *     checkout feature
     *     commit id: "D" tag: "v2.0.0"
     *     checkout main
     *     commit id: "E"
     *     commit id: "HEAD"
     * ```
     *
     * In this case this provider returns `1.0.0`, `1.1.0`, and `2.0.0`.
     */
    public val taggedVersions: Provider<List<Version>>

    /**
     * Calculates how [lastTaggedVersion] is incremented for [currentVersion] when the HEAD does not have an explicit version tag.
     * The resulting version always receives the SNAPSHOT pre-release identifier.
     *
     * The function receives both the current [lastTaggedVersion] and the list of [commitsSinceLastTaggedVersion].
     *
     * By default, this returns [Inc.PRE_RELEASE] for any version except if there's no last version, in which case [Inc.MINOR] is used, so
     * that the first snapshot version is `0.1.0-SNAPSHOT`.
     *
     * #### [Inc.PRE_RELEASE] (default)
     * Increases the version by its **pre-release**.
     *
     * If the last version is _not_ a pre-release, this behaves identically to [Inc.PATCH], otherwise the same exact major/minor/patch
     * versions are kept as is and the pre-release bit is set to `SNAPSHOT`.
     *
     * Examples:
     * - `1.2.3` becomes `1.2.4-SNAPSHOT`
     * - `1.2.3-alpha` becomes `1.2.3-SNAPSHOT`
     * - `1.2.3-rc.1` becomes `1.2.3-SNAPSHOT`
     * - `1.2.3-SNAPSHOT.1` becomes `1.2.3-SNAPSHOT.2`
     *
     * #### [Inc.PATCH]
     * Increases the version by its **patch** version, e.g. `1.2.3` becomes `1.2.4-SNAPSHOT`.
     *
     * #### [Inc.MINOR]
     * Increases the version by its **minor** version, e.g. `1.2.3` becomes `1.3.0-SNAPSHOT`.
     *
     * #### [Inc.MAJOR]
     * Increases the version by its **major** version, e.g. `1.2.3` becomes `2.0.0-SNAPSHOT`.
     *
     * @see Version.inc
     * @see SnapshotIncrementCalculator.DEFAULT
     */
    public val snapshotIncrement: Property<SnapshotIncrementCalculator>
}

public fun Property<SnapshotIncrementCalculator>.set(increment: Inc) {
    set { _, _, _ -> increment }
}
