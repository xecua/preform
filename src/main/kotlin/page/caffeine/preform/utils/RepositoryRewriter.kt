package page.caffeine.preform.utils

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId

// workaround for link https://github.com/sh5i/git-stein/commit/d43f0971402f8aec2af46d569e0bae14272e6321 
open class RepositoryRewriter : jp.ac.titech.c.se.stein.core.RepositoryRewriter() {
    // 行儀が悪い
    private fun isLink(entry: EntrySet.Entry): Boolean = FileMode.GITLINK.equals(entry.mode)

    override fun rewriteEntry(entry: EntrySet.Entry, c: Context): EntrySet {
        val uc = c.with(Context.Key.entry, entry);

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
