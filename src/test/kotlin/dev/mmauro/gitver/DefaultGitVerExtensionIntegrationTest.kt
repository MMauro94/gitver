package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Inc
import io.github.z4kn4fein.semver.Version
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withContexts
import io.kotest.datatest.withTests
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

private const val INVALID_VERSION_TAG = "v1.invalid"

class DefaultGitVerExtensionIntegrationTest : FunSpec(
    {
        context("every provider fails if the directory is not GIT-initialized") {
            val extension = tempdir().gitVerExtension()

            test("lastTaggedVersion") {
                shouldThrowAny {
                    extension.lastTaggedVersion.orNull
                }
            }
            test("commitsSinceLastTaggedVersion") {
                shouldThrowAny {
                    extension.commitsSinceLastTaggedVersion.orNull
                }
            }
            test("currentVersion") {
                shouldThrowAny {
                    extension.currentVersion.orNull
                }
            }
            test("taggedVersions") {
                shouldThrowAny {
                    extension.taggedVersions.orNull
                }
            }
            test("latestStableVersion") {
                shouldThrowAny {
                    extension.latestStableVersion.orNull
                }
            }
        }

        context("GIT repo with no commits (fresh init, no HEAD)") {
            val extension = autoClose(GitTestRepo.create()).gitVerExtension()

            test("lastTaggedVersion is not present") {
                extension.lastTaggedVersion.isPresent.shouldBeFalse()
            }
            test("commitsSinceLastTaggedVersion is not present") {
                extension.commitsSinceLastTaggedVersion.get().shouldBeEmpty()
            }
            test("currentVersion is 0.0.1-SNAPSHOT") {
                extension.currentVersion.get() shouldBe Version.parse("0.0.1-SNAPSHOT")
            }
            test("taggedVersions is empty") {
                extension.taggedVersions.get().shouldBeEmpty()
            }
            test("latestStableVersion is not present") {
                extension.latestStableVersion.isPresent.shouldBeFalse()
            }
        }

        context("GIT repo with commits but no tags") {
            val repo = autoClose(GitTestRepo.create()).apply {
                commit("A")
                commit("B")
                commit("C")
            }
            val extension = repo.gitVerExtension()

            test("lastTaggedVersion is not present") {
                extension.lastTaggedVersion.isPresent.shouldBeFalse()
            }
            test("commitsSinceLastTaggedVersion contains the three commits") {
                extension.commitsSinceLastTaggedVersion.get()
                    .map { it.subject }
                    .shouldContainExactly(listOf("A", "B", "C"))
            }
            test("currentVersion is 0.0.1-SNAPSHOT") {
                extension.currentVersion.get() shouldBe Version.parse("0.0.1-SNAPSHOT")
            }
            test("taggedVersions is empty") {
                extension.taggedVersions.get().shouldBeEmpty()
            }
            test("latestStableVersion is not present") {
                extension.latestStableVersion.isPresent.shouldBeFalse()
            }
        }

        context("when the current HEAD is pointing at a commit with a tagged version") {
            val repo = autoClose(GitTestRepo.create())
            repo.commit("A")
            val b = repo.commit("B")
            repo.tag("v1.0.0")

            repo.commit("C")

            repo.commit("D")
            repo.tag("v1.1.0")

            // More recent commit in a different branch should not be considered at all
            repo.branch("new-version", commit = b) {
                repo.commit("E")
                repo.tag("v2.0.0")
            }

            test("lastTaggedVersion matches the current tag") {
                repo.gitVerExtension().lastTaggedVersion.get() shouldBe Version.parse("1.1.0")
            }
            test("commitsSinceLastTaggedVersion is empty") {
                repo.gitVerExtension().commitsSinceLastTaggedVersion.get().shouldBeEmpty()
            }
            test("currentVersion matches the current tag") {
                repo.gitVerExtension().currentVersion.get() shouldBe Version.parse("1.1.0")
            }
        }

        context("when the current HEAD is pointing at a commit with NO tagged version") {
            val repo = autoClose(GitTestRepo.create())
            repo.commit("A")
            val b = repo.commit("B")
            repo.tag("v1.0.0")

            repo.commit("D")
            repo.tag("v1.1.0-rc.1")

            val e = repo.commit("E")
            val f = repo.commit("F")

            // More recent commit in a different branch should not be considered
            repo.branch("new-version", commit = b) {
                repo.commit("G")
                repo.tag("v2.0.0")
            }

            test("lastTaggedVersion walks back the current branch until it finds a tagged commit") {
                repo.gitVerExtension().lastTaggedVersion.get() shouldBe Version.parse("1.1.0-rc.1")
            }

            fun validateCommitsSinceLastTaggedVersion(commits: List<GitCommit>) {
                commits shouldHaveSize 2

                commits[0] should {
                    it.subject shouldBe "E"
                    it.hash shouldBe e
                }
                commits[1] should {
                    it.subject shouldBe "F"
                    it.hash shouldBe f
                }
            }

            test("commitsSinceLastTaggedVersion contains commits E and F") {
                val commits = repo.gitVerExtension().commitsSinceLastTaggedVersion.get()
                validateCommitsSinceLastTaggedVersion(commits)
            }

            context("currentVersion") {
                test("by default, uses the PRE_RELEASE increase") {
                    repo.gitVerExtension().currentVersion.get() shouldBe Version.parse("1.1.0-SNAPSHOT")
                }

                val expectedVersionByInc = listOf(
                    Inc.PRE_RELEASE to "1.1.0-SNAPSHOT",
                    Inc.PATCH to "1.1.1-SNAPSHOT",
                    Inc.MINOR to "1.2.0-SNAPSHOT",
                    Inc.MAJOR to "2.0.0-SNAPSHOT",
                )

                context("uses the provided snapshotIncrement to calculate the SNAPSHOT version") {
                    withTests(expectedVersionByInc) { (snapshotIncrement, expectedVersion) ->
                        val extension = repo.gitVerExtension()
                        extension.snapshotIncrement.set(snapshotIncrement)

                        extension.currentVersion.get() shouldBe Version.parse(expectedVersion)
                    }
                }

                context("uses the provided snapshotIncrementCalculator") {
                    withTests(expectedVersionByInc) { (snapshotIncrement, expectedVersion) ->
                        val extension = repo.gitVerExtension()
                        // Even if set, this configuration is ignored
                        // Explicitly setting to something != snapshotIncrement
                        extension.snapshotIncrement.set(
                            Inc.entries.single { it.ordinal == (snapshotIncrement.ordinal + 1) % Inc.entries.size },
                        )

                        var calls = 0
                        extension.snapshotIncrementCalculator.set(
                            SnapshotIncrementCalculator { version, commits ->
                                withClue("SnapshotIncrementCalculator is called with correct version") {
                                    version shouldBe Version.parse("1.1.0-rc.1")
                                }
                                withClue("SnapshotIncrementCalculator is called with correct commits") {
                                    validateCommitsSinceLastTaggedVersion(commits)
                                }
                                calls++
                                snapshotIncrement
                            },
                        )

                        extension.currentVersion.get() shouldBe Version.parse(expectedVersion)
                        withClue("SnapshotIncrementCalculator is called once") {
                            calls shouldBe 1
                        }
                    }
                }
            }
        }

        context("every provider fails when the repo contains invalid tags, even if unreachable from HEAD") {
            val repo = autoClose(GitTestRepo.create())
            repo.commit("A")
            val b = repo.commit("B")
            repo.tag("v1.0.0")

            repo.branch("feature", b) {
                repo.commit("C")
                repo.tag(INVALID_VERSION_TAG)
            }

            repo.commit("D")
            repo.tag("v1.1.0")

            val extension = repo.gitVerExtension()

            test("lastTaggedVersion") {
                shouldThrowAny {
                    extension.lastTaggedVersion.orNull
                }
            }
            test("commitsSinceLastTaggedVersion") {
                shouldThrowAny {
                    extension.commitsSinceLastTaggedVersion.orNull
                }
            }
            test("currentVersion") {
                shouldThrowAny {
                    extension.currentVersion.orNull
                }
            }
            test("taggedVersions") {
                shouldThrowAny {
                    extension.taggedVersions.orNull
                }
            }
            test("latestStableVersion") {
                shouldThrowAny {
                    extension.latestStableVersion.orNull
                }
            }
        }

        context("when the repo contains tags that start with v but are not versions") {
            val repo = autoClose(GitTestRepo.create())
            repo.commit("A")
            val b = repo.commit("B")
            repo.tag("v1.0.0")

            repo.branch("hotfix", b) {
                repo.commit("C")
                repo.tag("v1.0.1")
            }

            repo.commit("D")
            repo.tag("v2.0.0")

            repo.commit("E")
            repo.tag("velociraptor")

            test("lastTaggedVersion finds skips over non-version tags") {
                repo.gitVerExtension().lastTaggedVersion.get() shouldBe Version(major = 2, minor = 0, patch = 0)
            }
            test("commitsSinceLastTaggedVersion is empty") {
                repo.gitVerExtension().commitsSinceLastTaggedVersion.get().shouldBeSingle().subject shouldBe "E"
            }
            test("currentVersion works") {
                repo.gitVerExtension().currentVersion.get() shouldBe Version(major = 2, minor = 0, patch = 1, preRelease = "SNAPSHOT")
            }
            test("taggedVersions works") {
                repo.gitVerExtension().taggedVersions.get() shouldBe listOf(
                    Version(major = 1, minor = 0, patch = 0),
                    Version(major = 1, minor = 0, patch = 1),
                    Version(major = 2, minor = 0, patch = 0),
                )
            }
            test("latestStableVersion works") {
                repo.gitVerExtension().latestStableVersion.get() shouldBe Version(major = 2, minor = 0, patch = 0)
            }
        }
    },
)
