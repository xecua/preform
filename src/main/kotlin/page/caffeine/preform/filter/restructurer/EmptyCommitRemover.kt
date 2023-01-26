package page.caffeine.preform.filter.restructurer

import jp.ac.titech.c.se.stein.core.Context
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(name = "EmptyCommitRemover", description = ["Remove commits with no diff, except for merge commits."])
class EmptyCommitRemover : RepositoryRewriter() {
    override fun rewriteParents(parents: Array<out ObjectId>?, c: Context?): Array<ObjectId> {
        return c?.commit?.parents?.map { parent ->
            if (parent.parents?.size != 1) {
                return@map parent
            }

            val grandParent = parent.parents[0]

            return@map if (parent.tree == grandParent.tree) {
                    grandParent
            } else {
                 parent
            }
        }?.toTypedArray() ?: return super.rewriteParents(parents, c)       
    }
}
