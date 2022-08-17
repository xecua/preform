package page.caffeine.preform.filters

import page.caffeine.preform.utils.RepositoryRewriter
import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.lib.ObjectId
import picocli.CommandLine
import picocli.CommandLine.Command
import java.nio.charset.StandardCharsets

@Command(name = "Linebreak")
class Linebreak : RepositoryRewriter() {
    @CommandLine.Option(
        names = ["--to"],
        description = ["Type of line break convert into."],
        defaultValue = "LF",
        converter = [LineBreakerConverterConverter::class]
    )
    lateinit var lineBreakConverter: LineBreakConverter

    class LineBreakerConverterConverter : CommandLine.ITypeConverter<LineBreakConverter> {
        override fun convert(value: String): LineBreakConverter =
            when (value) {
                "CR", "cr" -> LineBreakConverter.CR
                "LF", "lf" -> LineBreakConverter.LF
                "CRLF", "crlf" -> LineBreakConverter.CRLF
                else -> throw Exception("--to: Invalid argument")
            }
    }


    override fun rewriteBlob(blobId: ObjectId, c: Context): ObjectId {
        if (!c.entry.name.lowercase().endsWith(".java")) {
            return super.rewriteBlob(blobId, c)
        }

        val content = String(source.readBlob(c.entry.id, c), StandardCharsets.UTF_8)
        // そもそも変換すべきかどうかを判定する? (最初の1行目で判定できるか?)
        val newContent = lineBreakConverter.convert(content)

        val newId = target.writeBlob(newContent.toByteArray(), c)

        if (newId != blobId) {
            logger.debug { "Rewrite blob: ${blobId.name} -> ${newId.name} $c" }
        }

        return newId
    }


    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

sealed interface LineBreakConverter {
    fun convert(txt: String): String

    object CR : LineBreakConverter {
        override fun convert(txt: String): String =
            txt.lines().joinToString("\r")
    }

    object LF : LineBreakConverter {
        override fun convert(txt: String): String =
            txt.lines().joinToString("\n")
    }

    object CRLF : LineBreakConverter {
        override fun convert(txt: String): String =
            txt.lines().joinToString("\r\n")
    }
}
