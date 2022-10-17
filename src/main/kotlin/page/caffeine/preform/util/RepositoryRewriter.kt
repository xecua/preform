package page.caffeine.preform.util

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository

// workaround for link https://github.com/sh5i/git-stein/commit/d43f0971402f8aec2af46d569e0bae14272e6321 
open class RepositoryRewriter : jp.ac.titech.c.se.stein.core.RepositoryRewriter() {
    // 許せ
    protected var sourceRepo: Repository? = null
    protected var targetRepo: Repository? = null

    override fun initialize(sourceRepo: Repository?, targetRepo: Repository?) {
        this.sourceRepo = sourceRepo
        this.targetRepo = targetRepo
        super.initialize(sourceRepo, targetRepo)
    }
    
    override fun confirmStartRef(ref: Ref?, c: Context?): Boolean = true

    // 行儀が悪い
    private fun isLink(entry: EntrySet.Entry): Boolean = FileMode.GITLINK.equals(entry.mode)

    override fun rewriteEntry(entry: EntrySet.Entry, c: Context): EntrySet {
        val uc = c.with(Context.Key.entry, entry)

        val newId = if (isLink(entry)) {
            rewriteLink(entry.id, uc)
        } else if (entry.isTree) {
            rewriteTree(entry.id, uc)
        } else {
            rewriteBlob(entry.id, uc)
        }

        val newName = rewriteName(entry.name, uc)
        return if (newId == ZERO) EntrySet.EMPTY else EntrySet.Entry(entry.mode, newName, newId, entry.directory)
    }

    open fun rewriteLink(commitId: ObjectId, c: Context): ObjectId = commitId
}
