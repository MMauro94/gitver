package dev.mmauro.gitver

import io.github.z4kn4fein.semver.Inc
import io.github.z4kn4fein.semver.Version
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.io.path.div
import kotlin.io.path.writeText

private const val INVALID_VERSION_TAG = "v1.invalid"

class DefaultGitVerExtensionIntegrationTest : FunSpec(
    {
        xcontext("every provider fails if the directory is not GIT-initialized") {
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
        }

        xcontext("GIT repo with no commits (fresh init, no HEAD)") {
            val extension = gitRepo().gitVerExtension()

            test("lastTaggedVersion is not present") {
                extension.lastTaggedVersion.isPresent.shouldBeFalse()
            }
            test("commitsSinceLastTaggedVersion is not present") {
                extension.commitsSinceLastTaggedVersion.get().shouldBeEmpty()
            }
            test("currentVersion is 0.0.1-SNAPSHOT") {
                extension.currentVersion.get() shouldBe Version.parse("0.1.0-SNAPSHOT")
            }
            test("taggedVersions is empty") {
                extension.taggedVersions.get().shouldBeEmpty()
            }
        }

        xcontext("GIT repo with commits but no tags") {
            val repo = gitRepo {
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
                extension.currentVersion.get() shouldBe Version.parse("0.1.0-SNAPSHOT")
            }
            test("taggedVersions is empty") {
                extension.taggedVersions.get().shouldBeEmpty()
            }
        }

        xcontext("when HEAD is pointing at a commit with a tagged version") {
            val repo = gitRepo {
                commit("A")
                val b = commit("B")
                tag("v1.0.0")

                commit("C")

                commit("D")
                tag("v1.1.0")

                // More recent commit in a different branch should not be considered at all
                branch("new-version", commit = b) {
                    commit("E")
                    tag("v2.0.0")
                }
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

            context("currentVersion gets incremented if the git status is dirty") {
                (repo.dir / "file").writeText("hello world")

                test("unstaged file") {
                    repo.gitVerExtension().currentVersion.get() shouldBe Version.parse("1.1.1-SNAPSHOT")
                }

                test("staged file") {
                    repo.cmd("add", "file")
                    repo.gitVerExtension().currentVersion.get() shouldBe Version.parse("1.1.1-SNAPSHOT")
                }
            }
        }

        xcontext("when HEAD is pointing at a commit with NO tagged version") {
            val repo = gitRepo()
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

                context("uses the provided snapshotIncrement calculator") {
                    withTests(
                        Inc.PRE_RELEASE to "1.1.0-SNAPSHOT",
                        Inc.PATCH to "1.1.1-SNAPSHOT",
                        Inc.MINOR to "1.2.0-SNAPSHOT",
                        Inc.MAJOR to "2.0.0-SNAPSHOT",
                    ) { (snapshotIncrement, expectedVersion) ->
                        val extension = repo.gitVerExtension()

                        var calls = 0
                        extension.snapshotIncrement.set { version, commits, isDirty ->
                            withClue("SnapshotIncrementCalculator is called with correct version") {
                                version shouldBe Version.parse("1.1.0-rc.1")
                            }
                            withClue("SnapshotIncrementCalculator is called with correct commits") {
                                validateCommitsSinceLastTaggedVersion(commits)
                            }
                            calls++
                            snapshotIncrement
                        }

                        extension.currentVersion.get() shouldBe Version.parse(expectedVersion)
                        withClue("SnapshotIncrementCalculator is called once") {
                            calls shouldBe 1
                        }
                    }
                }
            }
        }

        xcontext("every provider fails when the repo contains invalid tags, even if unreachable from HEAD") {
            val repo = gitRepo {
                commit("A")
                val b = commit("B")
                tag("v1.0.0")

                branch("feature", b) {
                    commit("C")
                    tag(INVALID_VERSION_TAG)
                }

                commit("D")
                tag("v1.1.0")
            }

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
        }

        xcontext("when the repo contains tags that start with v but are not versions") {
            val repo = gitRepo {
                commit("A")
                val b = commit("B")
                tag("v1.0.0")

                branch("hotfix", b) {
                    commit("C")
                    tag("v1.0.1")
                }

                commit("D")
                tag("v2.0.0")

                commit("E")
                tag("velociraptor")
            }
            val extension = repo.gitVerExtension()

            test("lastTaggedVersion finds skips over non-version tags") {
                extension.lastTaggedVersion.get() shouldBe Version(major = 2, minor = 0, patch = 0)
            }
            test("commitsSinceLastTaggedVersion is empty") {
                extension.commitsSinceLastTaggedVersion.get().shouldBeSingle().subject shouldBe "E"
            }
            test("currentVersion works") {
                extension.currentVersion.get() shouldBe Version(major = 2, minor = 0, patch = 1, preRelease = "SNAPSHOT")
            }
            test("taggedVersions works") {
                extension.taggedVersions.get() shouldBe listOf(
                    Version(major = 1, minor = 0, patch = 0),
                    Version(major = 1, minor = 0, patch = 1),
                    Version(major = 2, minor = 0, patch = 0),
                )
            }
        }

        xcontext("if a commit has multiple version tags associated to it") {
            val repo = gitRepo {
                commit("A")
                tag("v1.0.2")
                tag("v1.0.0")
                tag("v1.0.1")
            }
            val extension = repo.gitVerExtension()

            test("lastTaggedVersion returns the max version - not the latest") {
                extension.lastTaggedVersion.get() shouldBe Version(major = 1, minor = 0, patch = 2)
            }
            test("commitsSinceLastTaggedVersion is empty") {
                extension.commitsSinceLastTaggedVersion.get().shouldBeEmpty()
            }
            test("currentVersion returns the max version - not the latest") {
                extension.currentVersion.get() shouldBe Version(major = 1, minor = 0, patch = 2)
            }
            test("taggedVersions returns all versions") {
                extension.taggedVersions.get() shouldBe listOf(
                    Version(major = 1, minor = 0, patch = 0),
                    Version(major = 1, minor = 0, patch = 1),
                    Version(major = 1, minor = 0, patch = 2),
                )
            }
        }

        context("works with both lightweight and annotated tags") {
            val repo = gitRepo {
                commit("A")
                tag("v1.0.0")
                commit("B")
                annotatedTag("v1.0.1", message = "some fixes")
                commit("C")
                commit("D")
                commit("E")
                annotatedTag("v2.0.0", message = "some big release")
                commit("F")
            }
            val extension = repo.gitVerExtension()

            test("lastTaggedVersion") {
                extension.lastTaggedVersion.get() shouldBe Version(major = 2, minor = 0, patch = 0)
            }
            test("commitsSinceLastTaggedVersion") {
                extension.commitsSinceLastTaggedVersion.get().map { it.subject } shouldBe listOf("F")
            }
            test("currentVersion") {
                extension.currentVersion.get() shouldBe Version(major = 2, minor = 0, patch = 1, preRelease = "SNAPSHOT")
            }
            test("taggedVersions") {
                extension.taggedVersions.get() shouldBe listOf(
                    Version(major = 1, minor = 0, patch = 0),
                    Version(major = 1, minor = 0, patch = 1),
                    Version(major = 2, minor = 0, patch = 0),
                )
            }
        }
    },
)
