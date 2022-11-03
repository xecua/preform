package page.caffeine.preform.filter.normalizer

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import org.eclipse.jdt.core.dom.*
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import page.caffeine.preform.util.RepositoryRewriter
import page.caffeine.preform.util.generateParser
import picocli.CommandLine
import java.nio.charset.StandardCharsets

@CommandLine.Command(
    name = "KeywordNormalizer",
    description = [
        "Suppress keyword-related trivial changes.",
        "`this` receiver of fields, `super()` call in default constructor, `return` at the last of void method, will be inserted, removed, removed (resp.)"
    ]
)
class KeywordNormalizer : RepositoryRewriter() {
    // @Option(
    //     names = ["--keyword"],
    //     description = ["Apply keyword supplementation. Default: \${DEFAULT-VALUE}"]
    // )
    // private var applyToKeywords: Boolean = true

    // @Option(names= [ "--idents"], description = ["Make identifiers fully-qualified"])
    // private var applyToIdents: Boolean = true

    private var numOfReturnStmtInVoidMethod = 0
    private var numOfFilesWithReturnStmtInVoidMethod = 0
    private var numOfFilesWithReturnStmtInVoidMethodInCommit = 0
    private var numOfCommitsWithReturnStmtInVoidMethod = 0
    private var numOfSuperInDefaultConstructor = 0
    private var numOfFilesWithSuperInDefaultConstructor = 0
    private var numOfFilesWithSuperInDefaultConstructorInCommit = 0
    private var numOfCommitsWithSuperInConstructor = 0
    private var numOfMissingThisReceiver = 0
    private var numOfFilesWithMissingThisReceiver = 0
    private var numOfFilesWithMissingThisReceiverInCommit = 0
    private var numOfCommitsWithMissingThisReceiver = 0

    override fun cleanUp(c: Context?) {
        println(
            """
            #Instance:
                `return` in void method: $numOfReturnStmtInVoidMethod
                `super()` in default constructor: $numOfSuperInDefaultConstructor
                expressions with missing `this` receiver: $numOfMissingThisReceiver
            #File containing at least one instance:
                `return` in void method: $numOfFilesWithReturnStmtInVoidMethod
                `super()` in default constructor: $numOfFilesWithSuperInDefaultConstructor
                expressions with missing `this` receiver: $numOfFilesWithMissingThisReceiver
            #Commit containing at least one instance:
                `return` in void method: $numOfCommitsWithReturnStmtInVoidMethod
                `super()` in default constructor: $numOfCommitsWithSuperInConstructor
                expressions with missing `this` receiver: $numOfCommitsWithMissingThisReceiver
        """.trimIndent()
        )

        super.cleanUp(c)
    }

    override fun rewriteCommit(commit: RevCommit?, c: Context?): ObjectId {
        // commit level aggregation
        numOfFilesWithMissingThisReceiverInCommit = 0
        numOfFilesWithSuperInDefaultConstructorInCommit = 0
        numOfFilesWithReturnStmtInVoidMethodInCommit = 0

        val ret = super.rewriteCommit(commit, c)

        numOfFilesWithSuperInDefaultConstructor += numOfFilesWithSuperInDefaultConstructorInCommit
        if (numOfFilesWithSuperInDefaultConstructorInCommit > 0) {
            numOfCommitsWithSuperInConstructor++
        }
        numOfFilesWithReturnStmtInVoidMethod += numOfFilesWithReturnStmtInVoidMethodInCommit
        if (numOfFilesWithReturnStmtInVoidMethodInCommit > 0) {
            numOfCommitsWithReturnStmtInVoidMethod++
        }
        numOfFilesWithMissingThisReceiver += numOfFilesWithMissingThisReceiverInCommit
        if (numOfFilesWithMissingThisReceiverInCommit > 0) {
            numOfCommitsWithMissingThisReceiver++
        }

        return ret
    }

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
        val tree = parser.createAST(null) as CompilationUnit 
        if (tree.problems?.size != 0) {
            return content
        }

        val visitor = KeywordVisitor(content, tree)
        tree.accept(visitor)

        // file level aggregation
        numOfSuperInDefaultConstructor += visitor.numOfSuperInConstructor
        if (visitor.numOfSuperInConstructor > 0) {
            numOfFilesWithSuperInDefaultConstructorInCommit++
        }
        numOfReturnStmtInVoidMethod += visitor.numOfReturnStmtInVoidMethod
        if (visitor.numOfReturnStmtInVoidMethod > 0) {
            numOfFilesWithReturnStmtInVoidMethodInCommit++
        }
        numOfMissingThisReceiver += visitor.numOfMissingThisReceiver
        if (visitor.numOfMissingThisReceiver > 0) {
            numOfFilesWithMissingThisReceiverInCommit++
        }


        return visitor.getRewrittenContent()
    }
}

class KeywordVisitor(private val content: String, rootNode: CompilationUnit) : ASTVisitor() {
    private var astRewrite = ASTRewrite.create(rootNode.ast)

    var numOfReturnStmtInVoidMethod = 0
        private set
    var numOfSuperInConstructor = 0
        private set
    var numOfMissingThisReceiver = 0
        private set

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
        if (node.isConstructor) {
            if (node.parameters().size == 0 && statements.isNotEmpty()) {
                val first = statements.first()
                if (first.nodeType == ASTNode.SUPER_CONSTRUCTOR_INVOCATION && (first as SuperConstructorInvocation).arguments().size == 0) {
                    val bodyRewrite = astRewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY)
                    bodyRewrite.remove(first, null)
                    numOfSuperInConstructor++
                }
            }
        } else {
            // return type become null when this is constructor

            // Redundant return statement in void methods
            if (node.returnType2?.isPrimitiveType == true && (node.returnType2 as PrimitiveType).primitiveTypeCode == PrimitiveType.VOID) {
                if (statements.isNotEmpty()) {
                    val last = statements.last()
                    if (last.nodeType == ASTNode.RETURN_STATEMENT) {
                        val bodyRewrite = astRewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY)
                        bodyRewrite.remove(last, null)
                        numOfReturnStmtInVoidMethod++
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
                if (!node.isDeclaration && binding.isField && binding.declaringClass != null && when (val parent =
                        node.parent) {
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
                    numOfMissingThisReceiver++
                }
            }

            is IMethodBinding -> {
                val parent = node.parent
                if (parent is MethodInvocation && parent.expression == null) {
                    astRewrite.set(parent, MethodInvocation.EXPRESSION_PROPERTY, parent.ast.newThisExpression(), null)
                    numOfMissingThisReceiver++
                }
            }
        }

        return super.visit(node)
    }
}
