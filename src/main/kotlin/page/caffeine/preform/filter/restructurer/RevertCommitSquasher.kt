package page.caffeine.preform.filter.restructurer

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.util.io.DisabledOutputStream
import page.caffeine.preform.filter.marker.ChangeVector
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(
    name = "RevertCommitSquasher",
    description = ["Squash reverting and reverted commits", "Currently supporting consecutive pair."]
)
class RevertCommitSquasher : RepositoryRewriter() {
    private var previousCommitChangeVector = ChangeVector()
    private var parentCommitIdIfItRevertsParent: RevCommit? = null

    override fun rewriteParents(parents: Array<out ObjectId>?, c: Context?): Array<ObjectId> {
        val commit = c?.commit ?: return super.rewriteParents(parents, c)

        if (parentCommitIdIfItRevertsParent != null) {
            val newParents = parents!!.flatMap {
                if (it == parentCommitIdIfItRevertsParent) {
                    // it is guaranteed that it has only one parent
                    parentCommitIdIfItRevertsParent!!.getParent(0).parents.toList()
                } else {
                    listOf(it)
                }
            }.toTypedArray()

            parentCommitIdIfItRevertsParent = null
            return newParents
        }

        if (commit.parentCount != 1) {
            return super.rewriteParents(parents, c)
        }

        val parentCommit = commit.getParent(0)

        // as same condition as Wen et al. (2022).
        if (commit.fullMessage != null) {
            val match = REVERTING_COMMIT_MESSAGE_PATTERN.matchEntire(commit.fullMessage)
            if (match != null && match.groupValues[1] == parentCommit.name) {
                parentCommitIdIfItRevertsParent = commit
                return super.rewriteParents(parents, c)
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
            parentCommitIdIfItRevertsParent = commit
        }

        return super.rewriteParents(parents, c)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        val REVERTING_COMMIT_MESSAGE_PATTERN = Regex("""^Revert ".*This reverts commit ([0-9a-f]{40}).*""", RegexOption.DOT_MATCHES_ALL)
    }
}
