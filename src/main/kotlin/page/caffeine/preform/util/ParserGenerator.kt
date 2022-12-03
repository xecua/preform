package page.caffeine.preform.util

import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants

fun generateParser(): ASTParser = ASTParser.newParser(AST.getJLSLatest()).also {
    @Suppress("UNCHECKED_CAST")
    val options =
        (DefaultCodeFormatterConstants.getEclipseDefaultSettings() as MutableMap<String, String>).also {
            it[JavaCore.COMPILER_COMPLIANCE] = JavaCore.VERSION_1_8
            it[JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM] = JavaCore.VERSION_1_8
            it[JavaCore.COMPILER_SOURCE] = JavaCore.VERSION_1_8
            it[JavaCore.COMPILER_DOC_COMMENT_SUPPORT] = JavaCore.ENABLED
        }
    it.setCompilerOptions(options)

    it.setEnvironment(null, null, null, true)
}
