package page.caffeine.preform.filter.marker

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import gr.uom.java.xmi.diff.*
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
    name = "NonEssentialDiffMarker",
    description = ["Mark commits that contain non-essential changes.", "Currently supported only Rename related ones (Detected by RefactoringMiner https://github.com/tsantalis/RefactoringMiner)."]
)
class NonEssentialDiffMarker : RepositoryRewriter() {
    init {
        (LoggerFactory.getLogger("org.refactoringminer") as Logger).level = Level.WARN
    }

    private var numOfLocalVariableRenames = 0
    private var numOfNonLocalRenames = 0
    private var numOfCommitsContainingLocalVariableRenames = 0
    private var numOfCommitsContainingNonLocalRenames = 0 // Approximation for Rename Induced Modifications

    override fun cleanUp(c: Context?) {
        println("""
            #Local Variable Renames: $numOfLocalVariableRenames
            #Commits containing Local Variable Renames: $numOfCommitsContainingLocalVariableRenames
            #Non-Local Variable Renames: $numOfNonLocalRenames
            #Commits containing Non-Local Variable Renames: $numOfCommitsContainingNonLocalRenames
        """.trimIndent())

        super.cleanUp(c)
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
            var numOfLocalVariableRenames = 0
            val filesWithLocalVariableRenames = mutableSetOf<String>()
            var numOfNonLocalRenames = 0
            val filesWithNonLocalRenames = mutableSetOf<String>()

            override fun handle(_commitId: String?, refactorings: MutableList<Refactoring>?) {
                if (refactorings == null) {
                    return
                }

                refactorings.forEach {
                    when (it.refactoringType) {

                        RefactoringType.RENAME_VARIABLE, RefactoringType.RENAME_PARAMETER -> {
                            filesWithLocalVariableRenames.add(
                                (it as RenameVariableRefactoring).originalVariable.locationInfo.filePath
                            )
                            numOfLocalVariableRenames++
                        }
                        // Rename Induced Modifications
                        RefactoringType.RENAME_ATTRIBUTE -> {
                            filesWithNonLocalRenames.add((it as RenameAttributeRefactoring).renamedAttribute.locationInfo.filePath)
                            numOfNonLocalRenames++
                        }

                        RefactoringType.RENAME_METHOD -> {
                            // Here we can get references of this method, but other rename operations does not allow it.
                            // val references = (it as RenameOperationRefactoring).callReferences
                            // filesWithNonLocalRenames.addAll(references.map { it.invokedOperationBefore.locationInfo.filePath })
                            // numOfNonLocalRenames += references.size
                            filesWithNonLocalRenames.add((it as RenameOperationRefactoring).originalOperation.locationInfo.filePath)
                            numOfNonLocalRenames++
                        }

                        RefactoringType.RENAME_CLASS -> {
                            filesWithNonLocalRenames.add((it as RenameClassRefactoring).originalClass.locationInfo.filePath)
                            numOfNonLocalRenames++
                        }

                        RefactoringType.MOVE_RENAME_CLASS -> {
                            filesWithNonLocalRenames.add((it as MoveAndRenameClassRefactoring).originalClass.locationInfo.filePath)
                            numOfNonLocalRenames++
                        }

                        RefactoringType.MOVE_AND_RENAME_OPERATION -> {
                            filesWithNonLocalRenames.add((it as MoveOperationRefactoring).originalOperation.locationInfo.filePath)
                            numOfNonLocalRenames++
                        }

                        RefactoringType.MOVE_RENAME_ATTRIBUTE -> {
                            filesWithNonLocalRenames.add((it as MoveAndRenameAttributeRefactoring).originalAttribute.locationInfo.filePath)
                            numOfNonLocalRenames++
                        }

                        else -> {}
                    }
                }

            }

            override fun handleException(commitId: String?, e: Exception?) {
                logger.error(e) { "Ignoring." }
            }
        }
        rMiner.detectAtCommit(sourceRepo, commit.name, handler)

        handler.let {
            numOfLocalVariableRenames += it.numOfLocalVariableRenames
            numOfNonLocalRenames += it.numOfNonLocalRenames
            numOfCommitsContainingLocalVariableRenames += it.filesWithLocalVariableRenames.size
            numOfCommitsContainingNonLocalRenames += it.filesWithNonLocalRenames.size
        }

        return if (handler.numOfNonLocalRenames > 0 || handler.numOfLocalVariableRenames > 0) {
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
        private val logger = KotlinLogging.logger {}
    }
}
