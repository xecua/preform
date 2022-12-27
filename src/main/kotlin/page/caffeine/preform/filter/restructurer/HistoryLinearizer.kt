package page.caffeine.preform.filter.restructurer

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(name = "HistoryLinearizer", description = ["Ensure all commits have one parent"])
class HistoryLinearizer : RepositoryRewriter() {
    private var numOfMergeCommits = 0

    override fun cleanUp(c: Context?) {
        println("#Instance commits: $numOfMergeCommits")
        
        super.cleanUp(c)
    }
    
    override fun rewriteParents(parents: Array<out ObjectId>?, c: Context?): Array<ObjectId> {
        if (parents == null || parents.isEmpty()) {
            return arrayOf()
        }
        return arrayOf(parents[0])
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
