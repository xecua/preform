package page.caffeine.preform.filter.normalizer

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine
import picocli.CommandLine.Command

@Command(name = "ExtensionFilter", description = ["Remove all files without specified file name extension"])
class ExtensionFilter : RepositoryRewriter() {
    @CommandLine.Parameters(description = ["Extension(s) to leave, without `.`"], paramLabel = "ext", arity = "1..*")
    lateinit var extensions: Array<String>

    override fun rewriteBlob(blobId: ObjectId?, c: Context?): ObjectId {
        val fileName = (c?.get(Context.Key.entry) as? EntrySet.Entry)?.name?.lowercase()
            ?: return ZERO

        return if (testFileName(fileName)) {
            super.rewriteBlob(blobId, c)
        } else {
            ZERO
        }

    }

    fun testFileName(fileName: String) = extensions.any { fileName.endsWith(".$it") }
}
