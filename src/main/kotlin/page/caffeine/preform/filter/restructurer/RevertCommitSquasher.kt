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

    private var numOfPairs = 0

    override fun cleanUp(c: Context?) {
        println("#Revert pairs: $numOfPairs")

        super.cleanUp(c)
    }

    override fun rewriteParents(parents: Array<out ObjectId>?, c: Context?): Array<ObjectId> {
        val commit = c?.commit
        if (commit == null) {
            parentCommitIdIfItRevertsParent = null
            previousCommitChangeVector = ChangeVector()
            return super.rewriteParents(parents, c)
        }

        if (parentCommitIdIfItRevertsParent != null) {
            val newParents = parents!!.flatMap {
                if (it == parentCommitIdIfItRevertsParent) {
                    // it is guaranteed that it has only one parent
                    parentCommitIdIfItRevertsParent!!.getParent(0).parents.toList()
                } else {
                    listOf(it)
                }
            }

            numOfPairs++
            parentCommitIdIfItRevertsParent = null
            previousCommitChangeVector = ChangeVector()
            return newParents.map { commitMapping[it] ?: it }.toTypedArray()
        }

        if (commit.parentCount != 1) {
            parentCommitIdIfItRevertsParent = null
            previousCommitChangeVector = ChangeVector()
            return super.rewriteParents(parents, c)
        }

        val parentCommit = commit.getParent(0)

        // as same condition as Wen et al. (2022).
        if (commit.fullMessage != null) {
            val match = REVERTING_COMMIT_MESSAGE_PATTERN.matchEntire(commit.fullMessage)
            if (match != null && match.groupValues[1] == parentCommit.name) {
                parentCommitIdIfItRevertsParent = commit
                previousCommitChangeVector = ChangeVector()
                return super.rewriteParents(parents, c)
            }
        }

        val df = DiffFormatter(DisabledOutputStream.INSTANCE)
        df.setRepository(sourceRepo)
        val diffs = df.scan(parentCommit.tree, commit.tree)

        // 多分遅いのでなんか工夫した方がいい
        // あとこれテストどうしよ
        val currentCommitChangeVector = ChangeVector()
        diffs.forEach { diff ->
            when (diff.changeType!!) {
                DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.COPY -> {
                    currentCommitChangeVector.addedFiles.add(diff.newPath)
                }

                DiffEntry.ChangeType.DELETE -> {
                    currentCommitChangeVector.deletedFiles.add(diff.oldPath)
                }

                DiffEntry.ChangeType.RENAME -> {
                    currentCommitChangeVector.deletedFiles.add(diff.oldPath)
                    currentCommitChangeVector.addedFiles.add(diff.newPath)
                    // 内容の修正が入ることもある?
                }

                DiffEntry.ChangeType.MODIFY -> {

                    val edits = df.toFileHeader(diff).toEditList()
                    edits.forEach { edit ->
                        when (edit.type!!) {
                            Edit.Type.INSERT -> {
                                val newRawText = RawText(source.readBlob(diff.newId.toObjectId(), c))

                                currentCommitChangeVector.addedCodes.addAll((edit.beginB until edit.endB).map {
                                    "${diff.newPath}:${newRawText.getString(it)}"
                                })
                            }

                            Edit.Type.DELETE -> {
                                val oldRawText = RawText(source.readBlob(diff.oldId.toObjectId(), c))

                                currentCommitChangeVector.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                    "${diff.oldPath}:${oldRawText.getString(it)}"
                                })
                            }

                            Edit.Type.REPLACE -> {
                                val newRawText = RawText(source.readBlob(diff.newId.toObjectId(), c))
                                val oldRawText = RawText(source.readBlob(diff.oldId.toObjectId(), c))

                                currentCommitChangeVector.addedCodes.addAll((edit.beginB until edit.endB).map {
                                    "${diff.newPath}:${newRawText.getString(it)}"
                                })
                                currentCommitChangeVector.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                    "${diff.oldPath}:${oldRawText.getString(it)}"
                                })
                            }

                            Edit.Type.EMPTY -> {}
                        }
                    }

                }
            }
        }

        // check if this commit reverts previous commit
        parentCommitIdIfItRevertsParent = if (currentCommitChangeVector.reverts(previousCommitChangeVector)) {
            commit
        } else {
            null
        }
        previousCommitChangeVector = currentCommitChangeVector

        return super.rewriteParents(parents, c)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        val REVERTING_COMMIT_MESSAGE_PATTERN =
            Regex("""^Revert ".*This reverts commit ([0-9a-f]{40}).*""", RegexOption.DOT_MATCHES_ALL)
    }
}
