package page.caffeine.preform.filter.normalizer

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import mu.KotlinLogging
import org.eclipse.jdt.core.dom.*
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import page.caffeine.preform.util.RepositoryRewriter
import page.caffeine.preform.util.generateParser
import picocli.CommandLine.Command
import java.nio.charset.StandardCharsets

@Command(name = "LocalVariableInliner", description = ["Revert Local variable extraction by inlining it"])
class LocalVariableInliner : RepositoryRewriter() {
    private var numOfInstances = 0
    private var numOfFilesWithInstance = 0
    private var numOfFilesWithInstanceInCommit = 0
    private var numOfCommitsWithInstance = 0

    override fun cleanUp(c: Context?) {
        println(
            """
                #Instance: $numOfInstances
                #File containing at least one instance: $numOfFilesWithInstance
                #Commit containing at least one instance: $numOfCommitsWithInstance
            """.trimIndent()
        )

        super.cleanUp(c)
    }

    override fun rewriteCommit(commit: RevCommit?, c: Context?): ObjectId {
        numOfFilesWithInstanceInCommit = 0

        val ret = super.rewriteCommit(commit, c)
        
        numOfFilesWithInstance += numOfFilesWithInstanceInCommit
        if (numOfFilesWithInstanceInCommit > 0) {
            numOfCommitsWithInstance++
        }
        
        return ret
    }
    
    override fun rewriteBlob(blobId: ObjectId?, c: Context?): ObjectId {
        val fileName =
            (c?.get(Context.Key.entry) as? EntrySet.Entry)?.name?.lowercase() ?: return super.rewriteBlob(blobId, c)
        if (!fileName.endsWith(".java")) {
            return super.rewriteBlob(blobId, c)
        }

        // 直後の一文のみで使われているローカル変数の宣言を見つける
        val content = String(source.readBlob(blobId, c), StandardCharsets.UTF_8)
        val afterContent = rewriteContent(content)

        return target.writeBlob(afterContent.toByteArray(), c)
    }

    internal fun rewriteContent(content: String): String {
        val parser = generateParser()
        parser.setSource(content.toCharArray())
        val tree = parser.createAST(null) as CompilationUnit
        if (tree.problems?.size != 0) {
            return content
        }
        val visitor = LocalVariableVisitor(content, tree)
        tree.accept(visitor)
        
        numOfInstances += visitor.numOfInstances
        if (visitor.numOfInstances > 0) {
            numOfFilesWithInstanceInCommit++
        }
        
        return visitor.getRewrittenContent()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }

}

class LocalVariableVisitor(
    private val content: String, rootNode: CompilationUnit
) : ASTVisitor() {
    private var isInsideMethod = false
    private val variableDefUse = mutableMapOf<String, DefUse>()

    // if the last statement is variable declaration
    private var definingStmt: VariableDeclarationStatement? = null

    // name of variable in `definingStmt`
    private var definedVarName: String? = null

    // whether currentStatement is suitable for replacement
    private var canReplace = false

    private var astRewrite: ASTRewrite = ASTRewrite.create(rootNode.ast)
    
    var numOfInstances = 0
        private set

    fun getRewrittenContent(): String {
        val doc = Document(content)
        val edits = astRewrite.rewriteAST(doc, null)
        edits.apply(doc)
        return doc.get() ?: content
    }

    override fun visit(node: MethodDeclaration): Boolean {
        variableDefUse.clear()
        isInsideMethod = true
        node.body?.accept(this)
        return false
    }

    override fun endVisit(node: MethodDeclaration) {
        variableDefUse.forEach { (_, usage) ->
            if (usage.appearanceCount == 1) {
                // 直後の文で一回だけ使われた
                @Suppress("UNCHECKED_CAST") val replacingExpr =
                    (usage.def.fragments() as List<VariableDeclarationFragment>).last()
                if (usage.def.fragments().size == 1) {
                    astRewrite.remove(usage.def, null)
                }
                val replacing = astRewrite.createCopyTarget(replacingExpr.initializer)
                astRewrite.replace(usage.rightAfterUse, replacing, null)
                numOfInstances++
            }
        }

        isInsideMethod = false
    }

    override fun visit(node: VariableDeclarationStatement): Boolean {
        @Suppress("UNCHECKED_CAST") val expr = (node.fragments() as List<VariableDeclarationFragment>).last()

        // 初期化してないと意味ない
        if (expr.initializer != null) {
            definedVarName = expr.name.toString()
            definingStmt = node
        }

        return false
    }

    // AssertStatement
    override fun visit(node: AssertStatement): Boolean {
        canReplace = true
        node.expression?.accept(this)
        canReplace = true

        clearPreviousStatement()
        return false
    }

    // IfStatement
    override fun visit(node: IfStatement): Boolean {
        canReplace = true
        node.expression?.accept(this)
        canReplace = false
        clearPreviousStatement()

        node.thenStatement?.accept(this)
        node.elseStatement?.accept(this)

        return false

    }

    // SwitchStatement
    override fun visit(node: SwitchStatement): Boolean {
        canReplace = true
        node.expression.accept(this)
        canReplace = false
        clearPreviousStatement()

        // node.statements().forEach { it.accept(this) }

        return false
    }

    // ExpressionStatement
    override fun visit(node: ExpressionStatement): Boolean {
        canReplace = true
        node.expression.accept(this)
        canReplace = false

        clearPreviousStatement()
        return false
    }

    // ReturnStatement
    override fun visit(node: ReturnStatement): Boolean {
        canReplace = true
        node.expression?.accept(this)
        canReplace = false

        clearPreviousStatement()
        return false
    }

    // ThrowStatement
    override fun visit(node: ThrowStatement): Boolean {
        canReplace = true
        node.expression.accept(this)
        canReplace = false

        clearPreviousStatement()
        return false
    }


    // ConstructorInvocation
    override fun visit(node: ConstructorInvocation): Boolean {
        // expressionの一部という感じがする?
        canReplace = true
        // 第1引数だけ? (すでにカウントが1ならそもそも使われないのでいいか)
        node.arguments().forEach { (it as ASTNode).accept(this) }
        canReplace = false

        clearPreviousStatement()
        return false
    }

    // SuperConstructorInvocation
    override fun visit(node: SuperConstructorInvocation): Boolean {
        canReplace = true
        if (node.expression != null) {
            node.expression.accept(this)
            canReplace = false
        }
        node.arguments().forEach { (it as ASTNode).accept(this) }
        canReplace = false
        clearPreviousStatement()
        return false
    }

    // TryStatement
    override fun visit(node: TryStatement): Boolean {
        canReplace = true
        if (node.resources().isNotEmpty()) {
            node.resources().forEach { (it as ASTNode).accept(this) }
            canReplace = false
        }
        // 1文目が終わった時点でclearPreviousStatementが呼ばれる(はず)
        node.body?.statements()?.forEach { (it as ASTNode).accept(this) }
        canReplace = false

        node.catchClauses()?.forEach { (it as ASTNode).accept(this) }
        node.finally?.accept(this)

        clearPreviousStatement()
        return false
    }

    // Identifier
    override fun visit(node: SimpleName): Boolean {
        if (!isInsideMethod) {
            return false
        }

        val name = node.identifier
        // 直前の文で宣言したローカル変数と名前が一致した
        if (definedVarName == name && canReplace) {
            variableDefUse.putIfAbsent(name, DefUse(definingStmt!!, node, 0))
        }

        variableDefUse[name]?.let {
            it.appearanceCount += 1
        }

        return false
    }

    override fun visit(node: QualifiedName?): Boolean {
        // Name -> QualifiedName -> SimpleName を防止
        return false
    }


    private fun clearPreviousStatement() {
        definedVarName = null
        definingStmt = null
    }
}

data class DefUse(
    val def: VariableDeclarationStatement, var rightAfterUse: SimpleName, var appearanceCount: Int
)
