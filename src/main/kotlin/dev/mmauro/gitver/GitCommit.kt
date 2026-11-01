package dev.mmauro.gitver

import java.io.Serializable
import kotlin.time.Instant

data class GitCommit(
    val hash: String,
    val shortHash: String,
    val subject: String,
    val body: String,
    val author: String,
    val timestamp: Instant,
) : Serializable {

    companion object {
        const val GIT_FORMAT = "%H%x00%h%x00%s%x00%b%x00%an%x00%aI%x00"

        fun parseList(output: String): List<GitCommit> {
            return output
                .split('\u0000')
                .dropLast(1)
                .chunked(6)
                .map { fields ->
                    GitCommit(
                        hash = fields[0].trimStart('\n'),
                        shortHash = fields[1],
                        subject = fields[2],
                        body = fields[3],
                        author = fields[4],
                        timestamp = Instant.parse(fields[5]),
                    )
                }
        }
    }
}
