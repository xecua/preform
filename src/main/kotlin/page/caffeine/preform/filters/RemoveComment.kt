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

    internal fun rewriteContent(content: String): String {
        val remover = JavaCommentRemover()
        return remover.removeComment(content, false)
    }
}

abstract class CommentRemover {
    protected abstract fun getLineCommentStart(): List<String>
    protected abstract fun getBlockCommentStartAndEnd(): List<Pair<String, String>>

    /**
     * @param keepLine: Whether keep lines if the line become contain whitespaces only after removing the comment.
     *                  Note: This does not remove lines only with whitespaces from the beginning.
     */
    fun removeComment(content: String, keepLine: Boolean): String {
        var inLineComment = false
        var inBlockComment = false
        var existCommentInCurrentLine = false
        val blockCommentBegins = getBlockCommentStartAndEnd().map { it.first }
        val blockCommentEnds = getBlockCommentStartAndEnd().map { it.second }

        val currentLine = StringBuilder()
        val res = StringBuilder()

        // 1行に複数回ブロックコメントが現れることがあることに注意

        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (!inLineComment && blockCommentBegins.any { content.substring(i).startsWith(it) }) {
                // 行コメントの途中なら判定外
                inBlockComment = true
            }
            if (!inBlockComment && getLineCommentStart().any { content.substring(i).startsWith(it) }) {
                // ブロックコメントの途中なら判定外
                inLineComment = true
            }

            if (c == '\n') {
                // 改行: \rは必要ならcurrentLineに入っているので気にしなくていい
                if (!(!keepLine && existCommentInCurrentLine && currentLine.matches(Regex("""^\s*$""")))) {
                    // (無視した文字があったことによって空白だけになった、かつkeepLineがfalse (マッチは重そうなので最後)) の否定(こっちしか処理がないので)
                    currentLine.append(c)
                    res.append(currentLine)
                }

                currentLine.clear()
                inLineComment = false
                existCommentInCurrentLine = false
            } else {
                if (!inLineComment && !inBlockComment) {
                    currentLine.append(c)
                } else {
                    existCommentInCurrentLine = true
                }
            }

            for (bce in blockCommentEnds) {
                if (content.substring(i).startsWith(bce)) {
                    inBlockComment = false
                    i += bce.length - 1 // 下で+1するのでこっちで引いておく
                    break
                }
            }

            i += 1
        }

        // EOF: EOLと同じ処理が必要
        if (!(!keepLine && existCommentInCurrentLine && currentLine.matches(Regex("""^\s*$""")))) {
            // (無視した文字があったことによって空白だけになった、かつkeepLineがfalse (マッチは重そうなので最後)) の否定(こっちしか処理がないので)
            res.append(currentLine)
        }

        return res.toString()
    }
}

class JavaCommentRemover : CommentRemover() {
    override fun getLineCommentStart(): List<String> = listOf("//")
    override fun getBlockCommentStartAndEnd(): List<Pair<String, String>> = listOf(Pair("/*", "*/"))
}
