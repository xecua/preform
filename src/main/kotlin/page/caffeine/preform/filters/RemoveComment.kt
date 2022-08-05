package page.caffeine.preform.filters

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import org.eclipse.jgit.lib.ObjectId
import picocli.CommandLine
import java.nio.charset.StandardCharsets

@CommandLine.Command(
    name = "RemoveComment",
    description = ["Remove all comments from source code.", "depending on https://github.com/YoshikiHigo/CommentRemover"]
)
class RemoveComment : RepositoryRewriter() {
    override fun rewriteBlob(blobId: ObjectId?, c: Context?): ObjectId {
        val content = String(source.readBlob(blobId, c), StandardCharsets.UTF_8)
        val afterContent = rewriteContent(content)
        return target.writeBlob(afterContent.toByteArray(), c)
    }

    // internal fun rewriteContent(content: String): String =
    //     CommentRemoverJC(CRConfig.initialize(arrayOf("--linecomment", "remove", "--blockcomment", "remove"))).perform(content)
    internal fun rewriteContent(content: String): String {
        // CommentRemoverだとコメントを削除することによって発生した(もとから空行ではなかった)空行が生えてしまう ← どうにかする必要がある
        
        return content
    }
}
