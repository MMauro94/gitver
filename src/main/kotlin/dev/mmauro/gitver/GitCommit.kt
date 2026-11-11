package dev.mmauro.gitver

import java.io.Serializable
import kotlin.time.Instant

public data class GitCommit(
    val hash: String,
    val shortHash: String,
    val subject: String,
    val body: String,
    val author: PersonInfo,
    val committer: PersonInfo,
) : Serializable {

    public data class PersonInfo(
        val name: String,
        val email: String,
        val date: Instant,
    )

    public companion object {
        private val GIT_FIELDS = listOf(
            "%H", // Hash
            "%h", // Short hash
            "%s", // Subject
            "%b", // Body
            "%an", // Author name
            "%ae", // Author email
            "%aI", // Author ISO date
            "%cn", // Committer name
            "%ce", // Committer email
            "%cI", // Committer ISO date
        )
        internal val GIT_FORMAT = GIT_FIELDS.joinToString(separator = "%x00", postfix = "%x00")

        internal fun parseList(output: String): List<GitCommit> {
            return output
                .split('\u0000')
                .dropLast(1)
                .chunked(GIT_FIELDS.size)
                .map { fields ->
                    GitCommit(
                        hash = fields[0].trimStart('\n'),
                        shortHash = fields[1],
                        subject = fields[2],
                        body = fields[3],
                        author = PersonInfo(
                            name = fields[4],
                            email = fields[5],
                            date = Instant.parse(fields[6]),
                        ),
                        committer = PersonInfo(
                            name = fields[7],
                            email = fields[8],
                            date = Instant.parse(fields[9]),
                        ),
                    )
                }
        }
    }
}
