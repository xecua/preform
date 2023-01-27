package page.caffeine.preform.filter.restructurer

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(name = "EmptyCommitRemover", description = ["Remove commits with no diff, except for merge commits."])
class EmptyCommitRemover : RepositoryRewriter() {
    override fun rewriteParents(parents: Array<out ObjectId>?, c: Context?): Array<ObjectId> {
        return parents?.map {
            val parent = it as RevCommit

            var newParent = parent
            // track parents up to a commit with zero/multiple parents (root/merge commit) or a different tree than its parent
            while (newParent.parentCount == 1 && newParent.tree == newParent.getParent(0).tree) {
                newParent = newParent.getParent(0)
            }
            commitMapping[newParent] ?: run {
                logger.warn { "Commit $newParent seems not to be rewritten." }
                newParent
            }
        }?.toTypedArray() ?: return super.rewriteParents(parents, c)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
