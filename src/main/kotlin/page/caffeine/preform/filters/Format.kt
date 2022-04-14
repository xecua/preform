package page.caffeine.preform.filters

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import mu.KotlinLogging
import org.eclipse.jdt.core.ToolFactory
import org.eclipse.jdt.core.formatter.CodeFormatter
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import picocli.CommandLine
import java.nio.charset.StandardCharsets

class Format : RepositoryRewriter() {
    override fun rewriteBlob(blobId: ObjectId, c: Context): ObjectId {
        if (!c.entry.name.lowercase().endsWith(".java")) {
            return super.rewriteBlob(blobId, c)
        }

        val content = String(source.readBlob(c.entry.id, c), StandardCharsets.UTF_8)
        val formatter = ToolFactory.createCodeFormatter(null)

        val formattingOperation = try {
            formatter.format(
                CodeFormatter.K_COMPILATION_UNIT or CodeFormatter.F_INCLUDE_COMMENTS,
                content, 0, content.length, 0, "\n"
            )
        } catch (ignore: Exception) {
            null
        }

        if (formattingOperation == null) {
            logger.warn("${blobId.name} was not able to format.")
            return blobId
        }

        val doc = Document(content)
        formattingOperation.apply(doc)

        val newId = target.writeBlob(doc.get().toByteArray(), c)

        logger.debug { "Rewrite blob: ${blobId.name} -> ${newId.name} $c" }

        return newId
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
