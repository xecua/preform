package page.caffeine.preform.filter.marker

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.ChangeVector
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(name = "RevertCommitMarker", description = ["Mark revert commits."])
class RevertCommitMarker : RepositoryRewriter() {
    // We need to traverse twice to mark reverted commits, and the first one must not rewrite any object
    private var rewriting = false

    private val changeVectors = mutableMapOf<ChangeVector, MutableSet<ObjectId>>()

    private val revertingCommits = mutableSetOf<ObjectId>()
    private val revertedCommits = mutableSetOf<ObjectId>()

    override fun rewriteCommits(c: Context?) {
        super.rewriteCommits(c)
        rewriting = true
        super.rewriteCommits(c) // traverse twice to mark reverted commits
    }

    override fun rewriteCommitMessage(message: String?, c: Context?): String {
        val commit = c?.commit ?: return super.rewriteCommitMessage(message, c)
        if (rewriting) {
            return if (revertingCommits.contains(commit)) {
                annotateComment(message ?: "", true)
            } else if (revertedCommits.contains(commit)) {
                annotateComment(message ?: "", false)
            } else super.rewriteCommitMessage(message, c)
        } else {
            if (commit.parentCount != 1) {
                return super.rewriteCommitMessage(message, c)
            }

            // as same condition as Wen et al. (2022).
            if (message != null) {
                // Not considering this being reverted in second condition(ChangeVector-based).
                val match = REVERTING_COMMIT_MESSAGE_PATTERN.matchEntire(message)
                if (match != null) {
                    revertingCommits.add(commit)
                    revertedCommits.add(ObjectId.fromString(match.groupValues[1]))
                    return super.rewriteCommitMessage(message, c)
                }
            }

            val parentCommit = commit.getParent(0)
            val changeVector = ChangeVector.fromTrees(sourceRepo!!, parentCommit.tree, commit.tree)

            // check if this commit reverts previous commit
            val reversedChangeVector = changeVector.reversed()
            if (changeVectors.contains(reversedChangeVector)) {
                revertedCommits.addAll(changeVectors[reversedChangeVector]!!)
                revertingCommits.add(commit)
            }

            if (changeVector.isNotEmpty()) {
                // Prevent empty commit being inserted into candidates
                changeVectors.getOrDefault(changeVector, mutableSetOf()).add(commit)
            }

            return super.rewriteCommitMessage(message, c)
        }
    }

    private fun annotateComment(message: String, reverting: Boolean): String = """
            |$message
            |
            |[Preform] ${if (reverting) "Reverting" else "Reverted"} Commit
            |""".trimMargin()

    companion object {
        private val logger = KotlinLogging.logger {}

        val REVERTING_COMMIT_MESSAGE_PATTERN =
            Regex("""^Revert ".*This reverts commit ([0-9a-f]{40}).*""", RegexOption.DOT_MATCHES_ALL)
    }
}
