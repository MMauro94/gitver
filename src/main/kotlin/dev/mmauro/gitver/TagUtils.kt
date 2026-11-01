package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Version

internal fun Version.Companion.fromTag(tag: String): Version {
    require(tag.startsWith("v")) { "tag '$tag' doesn't start with v" }
    return Version.parse(tag.removePrefix("v"), strict = true)
}

internal fun Version.toTag(): String = "v$this"

/**
 * Checks if a given tag should be considered a version tag or not.
 *
 * This is deliberately more lenient than [Version.Companion.fromTag] so that a tag that "looks like" a version tag but is not quite right
 * will return true and fail on parsing, rather than being ignored.
 */
internal fun String.looksLikeVersionTag(): Boolean {
    // Any tag that can be parsed, even in non-strict mode, "looks like" a version tag
    if (runCatching { Version.parse(this, strict = false) }.isSuccess) return true

    // Anything starting with "v" followed by a digit "looks like" a version tag
    if (startsWith("v") && this[1].isDigit()) return true

    return false
}

/**
 * Returns the latest **non-pre-release** version in the collection (i.e. excludes alpha, betas, RCs, etc.).
 *
 * @throws NoSuchElementException if no stable release exists
 */
public fun Collection<Version>.latestRelease(): Version {
    return filter { !it.isPreRelease }.max()
}

/**
 * Returns the latest **non-pre-release** version in the collection (i.e. excludes alpha, betas, RCs, etc.), or `null` if no stable release
 * exists.
 */
public fun Collection<Version>.latestReleaseNull(): Version? {
    return filter { !it.isPreRelease }.maxOrNull()
}
