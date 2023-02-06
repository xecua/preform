package page.caffeine.preform.filter.restructurer

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import page.caffeine.preform.util.ChangeVector
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(
    name = "RevertCommitSquasher",
    description = ["Squash reverting and reverted commits", "Currently supporting consecutive pair."]
)
class RevertCommitSquasher : RepositoryRewriter() {
    private var previousCommitChangeVector = ChangeVector()
    private var parentCommitIdIfItRevertsParent: RevCommit? = null

    override fun rewriteParents(parents: Array<out ObjectId>?, c: Context?): Array<ObjectId> {
        val commit = c?.commit
        if (commit == null) {
            parentCommitIdIfItRevertsParent = null
            previousCommitChangeVector = ChangeVector()
            return super.rewriteParents(parents, c)
        }

        if (parentCommitIdIfItRevertsParent != null) {
            val newParents = parents!!.flatMap {
                if (it == parentCommitIdIfItRevertsParent) {
                    // it is guaranteed that it has only one parent
                    parentCommitIdIfItRevertsParent!!.getParent(0).parents.toList()
                } else {
                    listOf(it)
                }
            }

            parentCommitIdIfItRevertsParent = null
            previousCommitChangeVector = ChangeVector()
            return newParents.map { commitMapping[it] ?: it }.toTypedArray()
        }

        if (commit.parentCount != 1) {
            parentCommitIdIfItRevertsParent = null
            previousCommitChangeVector = ChangeVector()
            return super.rewriteParents(parents, c)
        }

        val parentCommit = commit.getParent(0)

        // as same condition as Wen et al. (2022).
        if (commit.fullMessage != null) {
            val match = REVERTING_COMMIT_MESSAGE_PATTERN.matchEntire(commit.fullMessage)
            if (match != null && match.groupValues[1] == parentCommit.name) {
                parentCommitIdIfItRevertsParent = commit
                previousCommitChangeVector = ChangeVector()
                return super.rewriteParents(parents, c)
            }
        }

        val currentCommitChangeVector = ChangeVector.fromTrees(sourceRepo!!, parentCommit.tree, commit.tree)

        // check if this commit reverts previous commit
        parentCommitIdIfItRevertsParent = if (currentCommitChangeVector.reverts(previousCommitChangeVector)) {
            commit
        } else {
            null
        }
        previousCommitChangeVector = currentCommitChangeVector

        return super.rewriteParents(parents, c)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        val REVERTING_COMMIT_MESSAGE_PATTERN =
            Regex("""^Revert ".*This reverts commit ([0-9a-f]{40}).*""", RegexOption.DOT_MATCHES_ALL)
    }
}
