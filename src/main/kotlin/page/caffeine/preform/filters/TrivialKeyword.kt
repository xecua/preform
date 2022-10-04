package page.caffeine.preform.filters

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.Block
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.PrimitiveType
import org.eclipse.jdt.core.dom.Statement
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.utils.RepositoryRewriter
import page.caffeine.preform.utils.generateParser
import picocli.CommandLine
import picocli.CommandLine.Option
import java.nio.charset.StandardCharsets

@CommandLine.Command(
    name = "FullQualifier",
    description = ["Fully-Qualify class names, keywords, etc."]
)
class TrivialKeyword : RepositoryRewriter() {
    // ループ末尾のcontinue: 入らないよな……
    @Option(names = ["--keyword"], description = ["Apply keyword supplementation. Default: \${DEFAULT-VALUE}"])
    private var applyToKeywords: Boolean = true

    // @Option(names= [ "--idents"], description = ["Make identifiers fully-qualified"])
    // private var applyToIdents: Boolean = true

    override fun rewriteBlob(blobId: ObjectId?, c: Context?): ObjectId {
        val fileName =
            (c?.get(Context.Key.entry) as? EntrySet.Entry)?.name?.lowercase() ?: return super.rewriteBlob(blobId, c)
        if (!fileName.endsWith(".java")) {
            return super.rewriteBlob(blobId, c)
        }

        val content = String(source.readBlob(blobId, c), StandardCharsets.UTF_8)
        val afterContent = rewriteContent(content)
        return target.writeBlob(afterContent.toByteArray(), c)
    }

    fun rewriteContent(content: String): String {
        val parser = generateParser()
        parser.setSource(content.toCharArray())
        // parser.setResolveBindings(true)
        val tree = parser.createAST(null) as CompilationUnit
        val visitor = TrivialKeywordVisitor(content, tree)
        tree.accept(visitor)
        return visitor.getRewrittenContent()
    }
}

class TrivialKeywordVisitor(private val content: String, rootNode: CompilationUnit) : ASTVisitor() {
    private var astRewrite = ASTRewrite.create(rootNode.ast)

    fun getRewrittenContent(): String {
        val doc = Document(content)
        val edits = astRewrite.rewriteAST(doc, null)
        edits.apply(doc)
        return doc.get() ?: content
    }

    override fun visit(node: MethodDeclaration): Boolean {
        val body = node.body
        @Suppress("UNCHECKED_CAST") val statements = body.statements() as List<Statement>

        // Redundant default super constructor invocation
        if (node.isConstructor) {
            if (statements.isNotEmpty()) {
                val first = statements.first()
                if (first.nodeType == ASTNode.SUPER_CONSTRUCTOR_INVOCATION) {
                    val bodyRewrite = astRewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY)
                    bodyRewrite.remove(first, null)
                }
            }
        } else {
            // return type become null when this is constructor

            // Redundant return statement in void methods
            if (node.returnType2.isPrimitiveType &&
                (node.returnType2 as PrimitiveType).primitiveTypeCode ==
                PrimitiveType.VOID
            ) {
                if (statements.isNotEmpty()) {
                    val last = statements.last()
                    if (last.nodeType == ASTNode.RETURN_STATEMENT) {
                        val bodyRewrite = astRewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY)
                        bodyRewrite.remove(last, null)
                    }
                }
            }

        }
        return super.visit(node)
    }
}
