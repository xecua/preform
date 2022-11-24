package page.caffeine.preform.util

import jp.ac.titech.c.se.stein.core.Context
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

    override fun rewrite(c: Context?) {
        setUp(c)
        if (cacheProvider != null) {
            cacheProvider.inTransaction {
                rewriteCommits(c)
                updateRefs(c)
                return@inTransaction null
            }
        } else {
            rewriteCommits(c)
            updateRefs(c)
        }

        source.writeNotes(c)
        // transit previous notes
        val sourceNote = source.readNote(c)
        target.eachNote({ targetId, sourceIdString ->
            val originalIdString = sourceNote.getNote(
                ObjectId.fromString(String(sourceIdString))
            )?.let { sourceNote ->
                String(source.readBlob(sourceNote.data, c))
            }
            if (originalIdString != null) {
                target.addNote(targetId, originalIdString, c)
            }
        }, c)
        target.writeNotes(c)

        cleanUp(c)
    }

    override fun confirmStartRef(ref: Ref?, c: Context?): Boolean = true
}
