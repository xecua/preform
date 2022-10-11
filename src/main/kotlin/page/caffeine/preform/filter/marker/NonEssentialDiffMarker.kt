package page.caffeine.preform.filter.marker

import ch.qos.logback.classic.Level
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator
import com.github.gumtreediff.gen.SyntaxException
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator
import com.github.gumtreediff.matchers.Matchers
import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.treewalk.TreeWalk
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command
import java.nio.charset.StandardCharsets


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

        logger.debug("${parentCommit.name} -> ${commit.name}")
        diffs.forEach {
            if ((!FileMode.REGULAR_FILE.equals(it.oldMode) && !FileMode.REGULAR_FILE.equals(it.newMode))
                || (!it.oldPath.endsWith(".java") || !it.newPath.endsWith(".java"))
            ) {
                return@forEach
            }

            // main
            // logger.debug("$it")
            val srcRoot = try {
                JdtTreeGenerator().generateFrom()
                    .string(String(source.readBlob(it.oldId.toObjectId(), c), StandardCharsets.UTF_8)).root
            } catch (e: SyntaxException) {
                logger.info("Syntax Error occurred in file ${it.oldId}. skipping.")
                return@forEach
            }
            val dstRoot = try {
                JdtTreeGenerator().generateFrom()
                    .string(String(source.readBlob(it.newId.toObjectId(), c), StandardCharsets.UTF_8)).root
            } catch (e: SyntaxException) {
                logger.info("Syntax Error occurred in file ${it.newId}. skipping.")
                return@forEach
            }
            val matcher = Matchers.getInstance().matcher
            val mappings = matcher.match(srcRoot, dstRoot)
            val editScriptGenerator = SimplifiedChawatheScriptGenerator()
            val editScripts = editScriptGenerator.computeActions(mappings)
            editScripts.forEach { logger.debug("$it") }
            
            // ここから検知を入れていく: クラス分離する?


        }

        return super.rewriteCommitMessage(message, c)
    }

    companion object {
        val logger = (KotlinLogging.logger {}).also {
            (it.underlyingLogger as ch.qos.logback.classic.Logger).level = Level.DEBUG
        }
    }
}
