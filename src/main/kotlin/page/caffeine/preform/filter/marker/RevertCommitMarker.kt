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

@Command(name = "RevertCommitMarker", description = ["Mark revert commits.", "Currently supporting consecutive pair."])
class RevertCommitMarker : RepositoryRewriter() {
    // commit vector
    // 並列処理を前提としないのならこいつは直接の親になるはず(逆に言うと並列だと一致しない可能性がある)
    // 富豪プログラミングするなら別にそれでもいい
    private var previousCommitChangeVector = ChangeVector()

    private val revertedCommits = mutableSetOf<ObjectId>()

    override fun rewriteCommits(c: Context?) {
        super.rewriteCommits(c)
        super.rewriteCommits(c) // traverse twice to mark reverted commits
    }

    override fun rewriteCommitMessage(message: String?, c: Context?): String {
        val commit = c?.commit ?: return super.rewriteCommitMessage(message, c)

        if (revertedCommits.contains(commit)) {
            return annotateComment(message ?: "", false)
        }

        if (commit.parentCount != 1) {
            return super.rewriteCommitMessage(message, c)
        }

        val parentCommit = commit.getParent(0)

        // as same condition as Wen et al. (2022).
        if (message != null) {
            val match = REVERTING_COMMIT_MESSAGE_PATTERN.matchEntire(message)
            if (match != null && match.groupValues[1] == parentCommit.name) {
                revertedCommits.add(parentCommit)
                return annotateComment(message, true)
            }
        }

         val df = DiffFormatter(DisabledOutputStream.INSTANCE)
        df.setRepository(sourceRepo)
        val diffs = df.scan(parentCommit.tree, commit.tree)

        // 多分遅いのでなんか工夫した方がいい
        // あとこれテストどうしよ
        val currentCommitChangeVector = ChangeVector()
        diffs.forEach { it ->
            // 両方が対象のソースコードでない場合は無視するんだっけ
            if (!it.oldPath.endsWith(".java") || !it.newPath.endsWith(".java")) {
                return@forEach
            }

            when (it.changeType!!) {
                DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.COPY -> {
                    currentCommitChangeVector.addedFiles.add(it.newPath)
                }

                DiffEntry.ChangeType.DELETE -> {
                    currentCommitChangeVector.deletedFiles.add(it.oldPath)
                }

                DiffEntry.ChangeType.RENAME -> {
                    currentCommitChangeVector.deletedFiles.add(it.oldPath)
                    currentCommitChangeVector.addedFiles.add(it.newPath)
                    // 内容の修正が入ることもある?
                }

                DiffEntry.ChangeType.MODIFY -> {
                    val fileName = it.newPath

                    val edits = df.toFileHeader(it).toEditList()
                    edits.forEach { edit ->
                        when (edit.type!!) {
                            Edit.Type.INSERT -> {
                                val newRawText = RawText(source.readBlob(it.newId.toObjectId(), c))

                                currentCommitChangeVector.addedCodes.addAll((edit.beginB until edit.endB).map {
                                    "$fileName:${newRawText.getString(it)}"
                                })
                            }

                            Edit.Type.DELETE -> {
                                val oldRawText = RawText(source.readBlob(it.oldId.toObjectId(), c))

                                currentCommitChangeVector.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                    "$fileName:${oldRawText.getString(it)}"
                                })
                            }

                            Edit.Type.REPLACE -> {
                                val newRawText = RawText(source.readBlob(it.newId.toObjectId(), c))
                                val oldRawText = RawText(source.readBlob(it.oldId.toObjectId(), c))

                                currentCommitChangeVector.addedCodes.addAll((edit.beginB until edit.endB).map {
                                    "$fileName:${newRawText.getString(it)}"
                                })
                                currentCommitChangeVector.deletedCodes.addAll((edit.beginA until edit.endA).map {
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
        if (currentCommitChangeVector.reverts(previousCommitChangeVector)) {
            previousCommitChangeVector = currentCommitChangeVector
            revertedCommits.add(parentCommit)
            return annotateComment(message ?: "", true)
        }

        previousCommitChangeVector = currentCommitChangeVector
        return super.rewriteCommitMessage(message, c)
    }

    private fun annotateComment(message: String, reverting: Boolean): String = """
            |$message
            |
            |[Preform] ${if (reverting) "Reverting" else "Reverted"} Commit
            |""".trimMargin()

    companion object {
        private val logger = KotlinLogging.logger {}

        val REVERTING_COMMIT_MESSAGE_PATTERN = Regex("""^Revert ".*This reverts commit ([0-9a-f]{40}).*""", RegexOption.DOT_MATCHES_ALL)
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

    // 全部空だと真になっちゃうのだけ回避
    fun reverts(other: ChangeVector): Boolean =
        !(this.addedFiles.isEmpty() && other.addedFiles.isEmpty() &&
            this.deletedFiles.isEmpty() && other.deletedFiles.isEmpty() &&
            this.addedCodes.isEmpty() && other.addedCodes.isEmpty() &&
            this.deletedCodes.isEmpty() && other.deletedCodes.isEmpty()
            ) &&
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
