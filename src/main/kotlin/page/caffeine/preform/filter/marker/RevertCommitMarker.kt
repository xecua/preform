package page.caffeine.preform.filter.marker

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.util.io.DisabledOutputStream
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(name = "RevertCommitMarker", description = ["Mark revert commits."])
class RevertCommitMarker : RepositoryRewriter() {
    // We need to traverse twice to mark reverted commits, and the first one must not rewrite any object
    private var rewriting = false

    private val changeVectors = mutableMapOf<ChangeVector, ObjectId>()

    private val revertingCommits = mutableSetOf<ObjectId>()
    private val revertedCommits = mutableSetOf<ObjectId>()

    @Option(
        names = ["--all-files"],
        description = ["Consider changes for all kinds of files (Default: only .java files)"]
    )
    var considerAllFiles = false

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
            val df = DiffFormatter(DisabledOutputStream.INSTANCE)
            df.setRepository(sourceRepo)
            val diffs = df.scan(parentCommit.tree, commit.tree)

            // 多分遅いのでなんか工夫した方がいい
            // あとこれテストどうしよ
            val changeVector = ChangeVector()
            diffs.forEach { it ->
                // 両方が対象のソースコードでない場合は無視するんだっけ
                if ((!it.oldPath.endsWith(".java") || !it.newPath.endsWith(".java")) && !considerAllFiles) {
                    return@forEach
                }

                when (it.changeType!!) {
                    DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.COPY -> {
                        changeVector.addedFiles.add(it.newPath)
                    }

                    DiffEntry.ChangeType.DELETE -> {
                        changeVector.deletedFiles.add(it.oldPath)
                    }

                    DiffEntry.ChangeType.RENAME -> {
                        changeVector.deletedFiles.add(it.oldPath)
                        changeVector.addedFiles.add(it.newPath)
                        // 内容の修正が入ることもある?
                    }

                    DiffEntry.ChangeType.MODIFY -> {
                        val fileName = it.newPath

                        val edits = df.toFileHeader(it).toEditList()
                        edits.forEach { edit ->
                            when (edit.type!!) {
                                Edit.Type.INSERT -> {
                                    val newRawText = RawText(source.readBlob(it.newId.toObjectId(), c))

                                    changeVector.addedCodes.addAll((edit.beginB until edit.endB).map {
                                        "$fileName:${newRawText.getString(it)}"
                                    })
                                }

                                Edit.Type.DELETE -> {
                                    val oldRawText = RawText(source.readBlob(it.oldId.toObjectId(), c))

                                    changeVector.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                        "$fileName:${oldRawText.getString(it)}"
                                    })
                                }

                                Edit.Type.REPLACE -> {
                                    val newRawText = RawText(source.readBlob(it.newId.toObjectId(), c))
                                    val oldRawText = RawText(source.readBlob(it.oldId.toObjectId(), c))

                                    changeVector.addedCodes.addAll((edit.beginB until edit.endB).map {
                                        "$fileName:${newRawText.getString(it)}"
                                    })
                                    changeVector.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                        "$fileName:${oldRawText.getString(it)}"
                                    })
                                }

                                Edit.Type.EMPTY -> {}
                            }
                        }

                    }
                }
            }

            // check if this commit reverts previous commit
            val reversedChangeVector = changeVector.reversed()
            if (changeVectors.contains(reversedChangeVector)) {
                revertedCommits.add(changeVectors[reversedChangeVector]!!)
                revertingCommits.add(commit)
            }

            if (changeVector.isNotEmpty()) {
                // Prevent empty commit being inserted into candidates
                changeVectors[changeVector] = commit
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

data class ChangeVector(
    val addedFiles: MutableSet<String> = mutableSetOf(),
    val deletedFiles: MutableSet<String> = mutableSetOf(),
    val addedCodes: MutableSet<String> = mutableSetOf(),
    val deletedCodes: MutableSet<String> = mutableSetOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ChangeVector) {
            return false
        }
        return this.addedFiles == other.addedFiles &&
                this.deletedFiles == other.deletedFiles &&
                this.addedCodes == other.addedCodes &&
                this.deletedCodes == other.deletedCodes
    }

    fun isEmpty(): Boolean =
        this.addedFiles.isEmpty() && this.deletedFiles.isEmpty() && this.addedCodes.isEmpty() && this.deletedCodes.isEmpty()

    fun isNotEmpty(): Boolean = !this.isEmpty()

    fun reversed(): ChangeVector = ChangeVector(
        addedFiles = this.deletedFiles,
        deletedFiles = this.addedFiles,
        addedCodes = this.deletedCodes,
        deletedCodes = this.addedCodes
    )

    // 全部空だと真になっちゃうのだけ回避
    fun reverts(other: ChangeVector): Boolean =
        !(this.isEmpty() && other.isEmpty()) &&
                this.addedFiles == other.deletedFiles &&
                this.deletedFiles == other.addedFiles &&
                this.addedCodes == other.deletedCodes &&
                this.deletedCodes == other.addedCodes

    // auto-generated
    override fun hashCode(): Int {
        var result = addedFiles.hashCode()
        result = 31 * result + deletedFiles.hashCode()
        result = 31 * result + addedCodes.hashCode()
        result = 31 * result + deletedCodes.hashCode()
        return result
    }

}
