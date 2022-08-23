package page.caffeine.preform.filters

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet.Entry
import org.eclipse.jdt.core.ToolFactory
import org.eclipse.jdt.core.compiler.ITerminalSymbols
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.utils.RepositoryRewriter
import picocli.CommandLine
import java.nio.charset.StandardCharsets

@CommandLine.Command(
    name = "RemoveComment",
    description = ["Remove all comments from source code.", "depending on https://github.com/YoshikiHigo/CommentRemover"]
)
class RemoveComment : RepositoryRewriter() {
    override fun rewriteBlob(blobId: ObjectId?, c: Context?): ObjectId {
        val fileName = (c?.get(Context.Key.entry) as? Entry)?.name?.lowercase() ?: return super.rewriteBlob(blobId, c)
        if (!fileName.endsWith(".java")) {
            return super.rewriteBlob(blobId, c)
        }

        val content = String(source.readBlob(blobId, c), StandardCharsets.UTF_8)
        val afterContent = rewriteContent(content)
        return target.writeBlob(afterContent.toByteArray(), c)
    }

    internal fun rewriteContent(content: String): String {
        val remover = JavaCommentRemover()
        return remover.removeComment(content)
    }
}

class JavaCommentRemover {
    fun removeComment(content: String): String {
        val scanner = ToolFactory.createScanner(true, true, true, "8")
        scanner.source = content.toCharArray()
        val dest = StringBuilder()

        while (true) {
            val t = scanner.nextToken
            if (t == ITerminalSymbols.TokenNameEOF) {
                break
            }

            if (t == ITerminalSymbols.TokenNameCOMMENT_LINE) {
                // 行コメント: 改行文字がコメントに含まれている
                val currentLineNo = scanner.getLineNumber(scanner.currentTokenStartPosition)
                val currentLineEndPos = scanner.getLineEnd(currentLineNo)
                // scanner.getLineEnd: 改行に遭遇していない、あるいはこのファイルに改行が存在しないと-1になる(APIの仕様にはないが……)
                if (currentLineEndPos >= 0) {
                    val previousLineEndPos = if (currentLineNo >= 2) scanner.getLineEnd(currentLineNo - 1) else -1
                    // 現在の行のコメントまでの要素: 空白だけだったらその行をなかったことにする(というか改行を追加しない)
                    val currentLine = content.substring(previousLineEndPos + 1 until scanner.currentTokenStartPosition)
                    if (currentLine.isNotBlank()) {
                        dest.append(
                            if (currentLineEndPos == 0 || content[currentLineEndPos - 1] != '\r') {
                                "\n"
                            } else {
                                "\r\n"
                            }

                        )
                    }
                }
            } else if (t == ITerminalSymbols.TokenNameCOMMENT_BLOCK || t == ITerminalSymbols.TokenNameCOMMENT_JAVADOC) {
                // 開始行のコメントまでが空白、かつ終了行のコメント以降が空白なら消す
                val commentStartPos = scanner.currentTokenStartPosition
                val commentStartingLineNo = scanner.getLineNumber(commentStartPos)
                val commentStartingLineStartPos = scanner.getLineStart(commentStartingLineNo)
                // 改行に遭遇してないとcommentStartingLineStartPos(というかlinePtr)が-1になるっぽい
                val startingLineContent = if (commentStartingLineNo == 1) "" else content.substring(
                    commentStartingLineStartPos until commentStartPos
                )

                if (startingLineContent.isBlank()) {
                    val nextToken = scanner.nextToken
                    // コメントの後ろに空白しかないかどうかは、次のトークンに改行文字が含まれる(このとき空白トークンである)、で判定できる
                    if (nextToken == ITerminalSymbols.TokenNameEOF) {
                        // EOF: コメント前の空白を除去
                        dest.setLength(dest.length - startingLineContent.length)
                        break
                    } else if (nextToken == ITerminalSymbols.TokenNameWHITESPACE) {
                        val spaces = String(scanner.currentTokenSource)
                        val newLineIndex = spaces.indexOf('\n')
                        if (newLineIndex >= 0) {
                            // コメント前後が空白のみであることがわかった
                            // コメント前の空白を除去
                            dest.setLength(dest.length - startingLineContent.length)
                            // 改行後のみ追加
                            dest.append(spaces.substring(newLineIndex + 1 until spaces.length))
                        } else {
                            dest.append(spaces)
                        }
                    } else {
                        dest.append(scanner.currentTokenSource)
                    }
                }

            } else {
                dest.append(scanner.currentTokenSource)
            }
        }

        return dest.toString()
    }
}
