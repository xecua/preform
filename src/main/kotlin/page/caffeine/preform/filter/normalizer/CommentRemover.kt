package page.caffeine.preform.filter.normalizer

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet.Entry
import org.eclipse.jdt.core.ToolFactory
import org.eclipse.jdt.core.compiler.ITerminalSymbols
import org.eclipse.jdt.core.compiler.InvalidInputException
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine
import java.nio.charset.StandardCharsets

@CommandLine.Command(
    name = "CommentRemover",
    description = ["Remove all comments from source code.", "Inspired by https://github.com/YoshikiHigo/CommentRemover"]
)
class CommentRemover : RepositoryRewriter() {
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
            val t = try {
                scanner.nextToken
            } catch (e: InvalidInputException) {
                // Syntax error. Skip this file
                return content
            }
            if (t == ITerminalSymbols.TokenNameEOF) {
                break
            }

            if (t == ITerminalSymbols.TokenNameCOMMENT_LINE) {
                val currentLineNo = scanner.getLineNumber(scanner.currentTokenStartPosition)
                val currentLineStartPos = if (currentLineNo == 1) 0 else scanner.getLineStart(currentLineNo)
                val currentLineContent = content.substring(currentLineStartPos until scanner.currentTokenStartPosition)
                if (currentLineContent.isBlank()) {
                    // 現在の行のコメントまでの要素が空白だけだったらその行をなかったことにする
                    dest.setLength(dest.length - currentLineContent.length)
                } else {
                    // コメントまでになんかあったら改行入れる(行コメントは改行文字も含むので)
                    if (content[scanner.currentTokenEndPosition] == '\n') {
                        if (content[scanner.currentTokenEndPosition - 1] == '\r') {
                            dest.append("\r\n")
                        } else {
                            dest.append("\n")
                        }
                    }
                }
            } else if (t == ITerminalSymbols.TokenNameCOMMENT_BLOCK || t == ITerminalSymbols.TokenNameCOMMENT_JAVADOC) {
                // 開始行のコメントまでが空白、かつ終了行のコメント以降が空白なら消す
                val commentStartPos = scanner.currentTokenStartPosition
                val commentStartingLineNo = scanner.getLineNumber(commentStartPos)
                val commentStartingLineStartPos =
                    if (commentStartingLineNo == 1) 0 else scanner.getLineStart(commentStartingLineNo)
                // 改行に遭遇してないとcommentStartingLineStartPos(というかlinePtr)が-1になるっぽい
                val startingLineContent = content.substring(
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
