package page.caffeine.preform.filter.marker

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.refactoringminer.api.Refactoring
import org.refactoringminer.api.RefactoringHandler
import org.refactoringminer.api.RefactoringType
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl
import org.slf4j.LoggerFactory
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command


@Command(
    description = ["Mark commits that contain non-essential changes.", "Currently supported only Rename related ones (Detected by RefactoringMiner https://github.com/tsantalis/RefactoringMiner)."]
)
class NonEssentialDiffMarker : RepositoryRewriter() {
    init {
        (LoggerFactory.getLogger("org.refactoringminer") as Logger).level = Level.WARN
    }

    override fun rewriteCommitMessage(message: String?, c: Context?): String {
        // contextからこのコミットと親コミットを引っ張り出してくる?
        val commit = c?.commit ?: return super.rewriteCommitMessage(message, c)
        // merge commitは無視する?(といっても勝手にFirst parentを見るっぽいが)
        if (commit.parentCount != 1) {
            return super.rewriteCommitMessage(message, c)
        }

        val rMiner = GitHistoryRefactoringMinerImpl()
        val handler = object : RefactoringHandler() {
            var hasNonEssentialChange = false

            override fun handle(_commitId: String?, refactorings: MutableList<Refactoring>?) {
                if (refactorings == null) {
                    return
                }

                if (
                    refactorings.any {
                        it.refactoringType in arrayOf(
                            RefactoringType.RENAME_VARIABLE, // Local Variable Renames
                            RefactoringType.RENAME_PARAMETER,
                            RefactoringType.RENAME_ATTRIBUTE, // Rename Induced Modifications?: できれば区別したいが……
                            RefactoringType.RENAME_METHOD,
                            RefactoringType.RENAME_CLASS,
                            RefactoringType.MOVE_RENAME_CLASS,
                            RefactoringType.MOVE_AND_RENAME_OPERATION,
                            RefactoringType.MOVE_RENAME_ATTRIBUTE,
                        )
                    }) {
                    hasNonEssentialChange = true
                }

            }

            override fun handleException(commitId: String?, e: Exception?) {
                // logger.error(e, { "Ignoring." })
            }
        }
        rMiner.detectAtCommit(sourceRepo, commit.name, handler)

        return if (handler.hasNonEssentialChange) {
            annotateComment(message ?: "")
        } else {
            super.rewriteCommitMessage(message, c)
        }
    }

    private fun annotateComment(message: String): String = """
        |$message
        |
        |[Preform] This commit contains non-essential changes
    """.trimMargin()

    companion object {
        val logger = KotlinLogging.logger {}
        // .also {
        //     (it.underlyingLogger as Logger).level = Level.DEBUG
        // }
    }
}
