package page.caffeine.preform.filter.normalizer

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import mu.KotlinLogging
import org.eclipse.jdt.core.dom.*
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.RepositoryRewriter
import page.caffeine.preform.util.generateParser
import picocli.CommandLine
import java.nio.charset.StandardCharsets

@CommandLine.Command(
    name = "TrivialkeywordNormalizer",
    description = [
        "Suppress keyword-related trivial changes.",
        "`this` receiver of fields, `super()` call in default constructor, `return` at the last of void method, will be inserted, removed, removed (resp.)"
    ]
)
class TrivialKeywordNormalizer : RepositoryRewriter() {
    // @Option(
    //     names = ["--keyword"],
    //     description = ["Apply keyword supplementation. Default: \${DEFAULT-VALUE}"]
    // )
    // private var applyToKeywords: Boolean = true

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
        parser.setResolveBindings(true)
        parser.setBindingsRecovery(true)
        parser.setStatementsRecovery(true)
        parser.setUnitName("") // setting non-null make resolution work within file
        val tree = try {
            parser.createAST(null) as CompilationUnit
        } catch (e: Exception) {
            logger.warn(e) { "Ignoring." }
            return content
        }
        val visitor = TrivialKeywordVisitor(content, tree)
        tree.accept(visitor)
        return visitor.getRewrittenContent()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
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
        val body = node.body ?: return super.visit(node)

        @Suppress("UNCHECKED_CAST") val statements = body.statements() as List<Statement>

        // Redundant default super constructor invocation in default constructor
        if (node.isConstructor && node.parameters().size == 0) {
            if (statements.isNotEmpty()) {
                val first = statements.first()
                if (first.nodeType == ASTNode.SUPER_CONSTRUCTOR_INVOCATION && (first as SuperConstructorInvocation).arguments().size == 0) {
                    val bodyRewrite = astRewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY)
                    bodyRewrite.remove(first, null)
                }
            }
        } else {
            // return type become null when this is constructor
            // Redundant return statement in void methods
            if (node.returnType2 != null && node.returnType2.isPrimitiveType && (node.returnType2 as PrimitiveType).primitiveTypeCode == PrimitiveType.VOID) {
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

    override fun visit(node: SimpleName): Boolean {
        // Add this. to field references
        when (val binding = node.resolveBinding()) {
            is IVariableBinding -> {
                if (!node.isDeclaration && binding.isField && binding.declaringClass != null
                    && when (val parent = node.parent) {
                        null -> false
                        is QualifiedName -> parent.qualifier.equals(node)
                        is FieldAccess -> parent.expression.equals(node)
                        else -> true
                    }
                ) {
                    // このノードをFieldAccess(ThisExpression + SimpleName)に置換する
                    val newNode = node.parent.ast.newFieldAccess()
                    newNode.expression = newNode.ast.newThisExpression()
                    newNode.name = astRewrite.createMoveTarget(node) as SimpleName

                    astRewrite.replace(node, newNode, null)
                }
            }

            is IMethodBinding -> {
                val parent = node.parent
                if (parent is MethodInvocation && parent.expression == null) {
                    astRewrite.set(parent, MethodInvocation.EXPRESSION_PROPERTY, parent.ast.newThisExpression(), null)
                }
            }
        }

        return super.visit(node)
    }
}
