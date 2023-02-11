package page.caffeine.preform.filter.restructurer

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(
    name = "RevertCommitSquasher",
    description = ["Squash reverting and reverted commits", "Currently supporting consecutive pair."]
)
class RevertCommitSquasher : RepositoryRewriter() {
    override fun rewriteParents(parents: Array<out ObjectId>, c: Context?): Array<ObjectId> {
        return parents.map {
            val parent = commitMapping[it]
            if (parent == null) {
                logger.warn { "Rewritten commit not found: ${it.name} {c}" }
                return@map it
            }

            val parentCommit = targetRepo?.parseCommit(parent)!!

            // #parents of the parent must be 1
            if (parentCommit.parentCount != 1) {
                return@map parentCommit
            }

            // #parents of the grand parent must be 1
            val grandParentCommit = targetRepo?.parseCommit(parentCommit.getParent(0))!!
            if (grandParentCommit.parentCount != 1) {
                return@map parentCommit
            }

            val grandGrandParentCommit = targetRepo?.parseCommit(grandParentCommit.getParent(0))!!

            // substitute Wen et al.(2022)'s original condition check with tree id matching
            return@map if (parentCommit.tree.id == grandGrandParentCommit.tree.id) {
                grandGrandParentCommit
            } else {
                parentCommit
            }

            // condition 1: commit message
            // if (REVERTING_COMMIT_MESSAGE_PATTERN.matchEntire(parentCommit.fullMessage)?.groupValues?.get(1) == grandParentCommit.name) {
            //     return@map grandGrandParentCommit
            // }
            //
            // condition 2: change vector
            // val grandParentCommitChangeVector =
            //     ChangeVector.fromTrees(targetRepo!!, grandGrandParentCommit.tree, grandParentCommit.tree)
            // val parentCommitChangeVector =
            //     ChangeVector.fromTrees(targetRepo!!, grandParentCommit.tree, parentCommit.tree)
            //
            // if (parentCommitChangeVector.reverts(grandParentCommitChangeVector)) {
            //     return@map grandGrandParentCommit
            // }
            // 
            // return@map parentCommit
        }.toTypedArray()
    }

    override fun rewriteRefObject(id: ObjectId, type: Int, c: Context?): ObjectId {
        if (type != Constants.OBJ_COMMIT) {
            return super.rewriteRefObject(id, type, c)
        }

        val target = commitMapping[id]
        if (target == null) {
            logger.warn { "Rewritten commit not found: ${id.name} {c}" }
            return id
        }

        val targetCommit = targetRepo?.parseCommit(target)!!
        if (targetCommit.parentCount != 1) {
            return target
        }

        val targetParentCommit = targetRepo?.parseCommit(targetCommit.getParent(0))!!
        if (targetParentCommit.parentCount != 1) {
            return target
        }

        val targetGrandParentCommit = targetRepo?.parseCommit(targetParentCommit.getParent(0))!!

        return if (targetCommit.tree.id == targetGrandParentCommit.tree.id) {
            targetGrandParentCommit
        } else {
            targetCommit
        }
    }


    companion object {
        private val logger = KotlinLogging.logger {}

        // val REVERTING_COMMIT_MESSAGE_PATTERN =
        //     Regex("""^Revert ".*This reverts commit ([0-9a-f]{40}).*""", RegexOption.DOT_MATCHES_ALL)
    }
}
