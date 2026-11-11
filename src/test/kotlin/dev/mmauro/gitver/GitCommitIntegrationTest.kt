package dev.mmauro.gitver

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldStartWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val DATE_LEEWAY = 5.seconds

private val GIT_CONFIG = GitConfig(
    userName = "Mario",
    userEmail = "mario@example.com",
    authorName = "Luigi",
    authorEmail = "luigi@example.com",
)

class GitCommitIntegrationTest : FunSpec(
    {
        test("parse works as expected") {
            val now = Clock.System.now()
            val firstCommitBody = """
                First commit
                
                Hello, this is the first commit.
                
                I am also multi line.
                
                Bye 👋
                
            """.trimIndent()
            val repo = gitRepo(GIT_CONFIG) {
                commit(
                    message = firstCommitBody.trimIndent(),
                )

                commit("second commit")
            }

            val commits = GitCommit.parseList(repo.cmd("log", "--format=${GitCommit.GIT_FORMAT}"))

            commits.forAll {
                it.hash shouldStartWith it.shortHash
                it.hash shouldMatch Regex("^[0-9a-f]+$")

                it.author.date.shouldBeBetween(now - DATE_LEEWAY, now + DATE_LEEWAY)
            }

            commits.first() should {
                it.subject shouldBe "second commit"
                it.body.shouldBeEmpty()
            }

            commits.last() should { commit ->
                commit.committer should {
                    it.name shouldBe "Mario"
                    it.email shouldBe "mario@example.com"
                }
                commit.author should {
                    it.name shouldBe "Luigi"
                    it.email shouldBe "luigi@example.com"
                }

                commit.subject shouldBe "First commit"
                commit.body shouldBe firstCommitBody.lines().drop(2).joinToString("\n")
            }
        }
    },
)
