package page.caffeine.preform.util

import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.util.io.DisabledOutputStream

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
   
   companion object {
       fun fromTrees(repository: Repository, oldTree: RevTree, newTree: RevTree): ChangeVector {
           val df = DiffFormatter(DisabledOutputStream.INSTANCE)
           df.setRepository(repository)
           
           val cv = ChangeVector()
           df.scan(oldTree, newTree).forEach { diff ->
                when(diff.changeType) {
                    DiffEntry.ChangeType.ADD, DiffEntry.ChangeType.COPY -> {
                        cv.addedFiles.add(diff.newPath)
                    }
                    DiffEntry.ChangeType.DELETE -> {
                        cv.deletedFiles.add(diff.oldPath)
                    }
                    DiffEntry.ChangeType.RENAME -> {
                        cv.deletedFiles.add(diff.oldPath)
                        cv.addedFiles.add(diff.newPath)
                    }
                    DiffEntry.ChangeType.MODIFY -> {
                        df.toFileHeader(diff).toEditList().forEach { edit ->
                            when(edit.type) {
                                Edit.Type.INSERT -> {
                                    val newSourceCode = RawText(repository.open(diff.newId.toObjectId(), 3).bytes)
                                    cv.addedCodes.addAll((edit.beginB until edit.endB).map {
                                        "${diff.newPath}:${newSourceCode.getString(it)}"
                                    })
                                }
                                Edit.Type.DELETE -> {
                                    val oldSourceCode = RawText(repository.open(diff.oldId.toObjectId(), 3).bytes)

                                    cv.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                        "${diff.oldPath}:${oldSourceCode.getString(it)}"
                                    })
                                }
                                
                                Edit.Type.REPLACE -> {
                                    val oldSourceCode = RawText(repository.open(diff.oldId.toObjectId(), 3).bytes)
                                    val newSourceCode = RawText(repository.open(diff.newId.toObjectId(), 3).bytes)
                                    cv.deletedCodes.addAll((edit.beginA until edit.endA).map {
                                        "${diff.oldPath}:${oldSourceCode.getString(it)}"
                                    })
                                    cv.addedCodes.addAll((edit.beginB until edit.endB).map {
                                        "${diff.newPath}:${newSourceCode.getString(it)}"
                                    })
                                }
                                Edit.Type.EMPTY, null -> {}
                            }
                        }
                    }
                    null -> {}
                }
           }
           return cv
       }
   } 
}
