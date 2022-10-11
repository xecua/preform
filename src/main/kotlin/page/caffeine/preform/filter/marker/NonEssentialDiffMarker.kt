package page.caffeine.preform.filter.marker

import jp.ac.titech.c.se.stein.core.Context
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.treewalk.TreeWalk
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command


@Command(name = "NonEssentialDiffMarker", description = ["Mark commits that contain non-essential changes."])
class NonEssentialDiffMarker : RepositoryRewriter() {

    override fun rewriteCommitMessage(message: String?, c: Context?): String {
        // contextからこのコミットと親コミットを引っ張り出してくる?
        val commit = c?.commit ?: return super.rewriteCommitMessage(message, c)
        // merge commitは無視する?
        if (commit.parentCount != 1) {
            return super.rewriteCommitMessage(message, c)
        }

        val parentCommit = commit.getParent(0)

        val walk = TreeWalk(sourceRepo)
        walk.addTree(commit.tree)
        walk.addTree(parentCommit.tree)
        val diffs = DiffEntry.scan(walk, true)

        println("${parentCommit.name} -> ${commit.name}")
        diffs.forEach {
            if (FileMode.REGULAR_FILE.equals(it.oldMode) || FileMode.REGULAR_FILE.equals(it.newMode)) {
                println(it)
            }
        }

        return super.rewriteCommitMessage(message, c)
    }
}
