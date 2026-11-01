package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Version
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withContexts
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class TagUtilsTest : FunSpec(
    {
        val validTags = listOf(
            "v0.0.0" to Version.min,
            "v0.0.1" to Version(major = 0, minor = 0, patch = 1),
            "v1.0.0" to Version(major = 1, minor = 0, patch = 0),
            "v1.2.3-alpha" to Version(major = 1, minor = 2, patch = 3, preRelease = "alpha"),
            "v15.6.71-rc.1" to Version(major = 15, minor = 6, patch = 71, preRelease = "rc.1"),
            "v1.78.11+12345" to Version(major = 1, minor = 78, patch = 11, buildMetadata = "12345"),
            "v9.9.9-SNAPSHOT+a.b.c" to Version(major = 9, minor = 9, patch = 9, preRelease = "SNAPSHOT", buildMetadata = "a.b.c"),
        )

        val invalidTags = mapOf(
            "empty string" to "",
            "blank string" to "   ",
            "missing v" to "1.0.0",
            "starting with space" to " v1.0.0",
            "just a string" to "hello",
            "empty version" to "v",
            "blank version" to "v   ",
            "missing patch" to "v1.0",
            "missing minor" to "v1",
            "ending with space" to "v1.0.0 ",
            "string starting with v" to "velociraptor",
        )

        context("fromTag") {
            context("correctly parses valid version tags") {
                withTests(nameFn = { it.first }, validTags) { (tag, version) ->
                    Version.fromTag(tag) shouldBe version
                }
            }

            context("fails with invalid version tags") {
                withTests(invalidTags) { tag ->
                    shouldThrowAny {
                        Version.fromTag(tag)
                    }
                }
            }
        }

        context("toTag") {
            withTests(validTags) { (tag, version) ->
                version.toTag() shouldBe tag
            }
        }

        test("toTag/fromTag round trips") {
            checkAll(
                Arb.int(min = 0),
                Arb.int(min = 0),
                Arb.int(min = 0),
                Arb.string(codepoints = Codepoint.alphanumeric().merge(Arb.of(Codepoint('-'.code)))),
                Arb.string(codepoints = Codepoint.alphanumeric().merge(Arb.of(Codepoint('-'.code)))).filterNot { it.isBlank() },
            ) { major, minor, patch, preRelease, buildMetadata ->
                val version = Version(major, minor, patch, preRelease, buildMetadata)

                Version.fromTag(version.toTag()) shouldBe version
            }
        }

        context("looksLikeVersionTag") {
            withTests(
                validTags.map { it.first to true } + invalidTags.keys.map { it to false } + listOf(
                    "v1.invalid" to true,
                    "v3l0c1r4pt0r" to true,
                    "very-nice-feature-1" to false,
                    "ver1" to false,
                    "1245678" to false,
                ),
            ) { (tag, expected) ->
                tag.looksLikeVersionTag() shouldBe expected
            }
        }

        context("latest") {
            withContexts(
                nameFn = { it.first.toString() },
                emptyList<String>() to null,
                listOf("0.0.1", "0.0.2") to "0.0.2",
                listOf("1.0.0", "1.0.0") to "1.0.0",
                listOf("1.0.0-beta1", "1.0.0-beta2", "1.0.0-rc1") to null,
                listOf("2.9.9", "10.0.0") to "10.0.0",
                listOf("1.0.1", "1.1.0") to "1.1.0",
                listOf("1.0.0", "1.0.1+9876", "1.1.0+1234") to "1.1.0",
            ) { (versions, expected) ->
                val versions = versions.map(Version::parse)
                val expected = expected?.let(Version::parse)

                test("latestStable") {
                    if (expected != null) {
                        versions.latestRelease() shouldBe expected
                    } else {
                        shouldThrow<NoSuchElementException> { versions.latestRelease() }
                    }
                }
                test("latestStableOrNull") {
                    versions.latestReleaseNull() shouldBe expected
                }
            }
        }
    },
)
