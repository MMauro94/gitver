package dev.mmauro.gitver

import io.kotest.assertions.withClue
import io.kotest.core.TestConfiguration
import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val GRADLE_HOME_DIR = ".gradle"
private const val MAIN_BRANCH = "main"

data class GitConfig(
    val userName: String = "John Doe",
    val userEmail: String = "john.doe@example.com",
    val authorName: String = "Jane Doe",
    val authorEmail: String = "jane.doe@example.org",
)

class GitTestRepo(
    val dir: Path,
    val config: GitConfig = GitConfig(),
) : AutoCloseable {

    fun cmd(vararg args: String): String {
        return ProcessBuilder()
            .command(
                "git",
                // author/committer config
                "-c",
                "user.name=${config.userName}",
                "-c",
                "user.email=${config.userEmail}",
                "-c",
                "author.name=${config.authorName}",
                "-c",
                "author.email=${config.authorEmail}",
                *args,
            )
            .directory(dir.toFile())
            .start()
            .also {
                it.waitFor(10, TimeUnit.SECONDS)
                withClue("git command exited OK, stderr: ${it.errorReader().readText()}") {
                    it.exitValue() shouldBe 0
                }
            }
            .inputReader()
            .readText()
    }

    /**
     * Adds a random file and commits it
     */
    @OptIn(ExperimentalUuidApi::class)
    fun commit(message: String): String {
        val uuid = Uuid.generateV4()
        val file = dir / uuid.toHexDashString()
        file.createFile()
        file.writeText(uuid.toHexString())

        cmd("add", file.name)
        cmd("commit", "-m", message)

        return cmd("rev-parse", "HEAD").trim()
    }

    /**
     * Creates a new branch named [name]
     */
    fun branch(name: String, commit: String? = null, block: () -> Unit) {
        val originalBranch = cmd("branch", "--show-current").trim()
        if (commit != null) {
            checkout(commit)
        }
        cmd("branch", name)
        try {
            block()
        } finally {
            checkout(originalBranch)
        }
    }

    /**
     * Checks out the given [branchOrCommit]
     */
    fun checkout(branchOrCommit: String) {
        cmd("checkout", branchOrCommit)
    }

    /**
     * Tags HEAD with a tag with the given [name]
     */
    fun tag(name: String) {
        cmd("tag", name)
    }

    fun annotatedTag(name: String, message: String) {
        cmd("tag", "-a", name, "-m", message)
    }

    @OptIn(ExperimentalPathApi::class)
    override fun close() {
        dir.deleteRecursively()
    }

    companion object {
        fun create(gitConfig: GitConfig = GitConfig(), init: GitTestRepo.() -> Unit = {}): GitTestRepo {
            val dir = createTempDirectory()
            return GitTestRepo(dir, gitConfig).also {
                it.cmd("init", "-b", MAIN_BRANCH)
                (dir / ".git" / "info" / "exclude").writeText(GRADLE_HOME_DIR)

                it.init()
            }
        }
    }
}

fun TestConfiguration.gitRepo(gitConfig: GitConfig = GitConfig(), init: GitTestRepo.() -> Unit = {}): GitTestRepo {
    return autoClose(GitTestRepo.create(gitConfig, init))
}

fun File.gitVerExtension(): DefaultGitVerExtension {
    val project = ProjectBuilder.builder()
        .withProjectDir(this)
        .withGradleUserHomeDir(File(this, GRADLE_HOME_DIR))
        .build()

    project.pluginManager.apply(GitVerPlugin::class.java)

    return project.extensions.getByType(DefaultGitVerExtension::class.java)
}

fun GitTestRepo.gitVerExtension() = dir.toFile().gitVerExtension()
