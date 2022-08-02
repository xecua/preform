package page.caffeine.preform.filters

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.AssertStatement
import org.eclipse.jdt.core.dom.BreakStatement
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.ConstructorInvocation
import org.eclipse.jdt.core.dom.ContinueStatement
import org.eclipse.jdt.core.dom.DoStatement
import org.eclipse.jdt.core.dom.EmptyStatement
import org.eclipse.jdt.core.dom.EnhancedForStatement
import org.eclipse.jdt.core.dom.Expression
import org.eclipse.jdt.core.dom.ExpressionStatement
import org.eclipse.jdt.core.dom.ForStatement
import org.eclipse.jdt.core.dom.IfStatement
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.QualifiedName
import org.eclipse.jdt.core.dom.ReturnStatement
import org.eclipse.jdt.core.dom.SimpleName
import org.eclipse.jdt.core.dom.Statement
import org.eclipse.jdt.core.dom.SuperConstructorInvocation
import org.eclipse.jdt.core.dom.SwitchCase
import org.eclipse.jdt.core.dom.SwitchStatement
import org.eclipse.jdt.core.dom.SynchronizedStatement
import org.eclipse.jdt.core.dom.ThrowStatement
import org.eclipse.jdt.core.dom.TryStatement
import org.eclipse.jdt.core.dom.TypeDeclarationStatement
import org.eclipse.jdt.core.dom.VariableDeclarationFragment
import org.eclipse.jdt.core.dom.VariableDeclarationStatement
import org.eclipse.jdt.core.dom.WhileStatement
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.utils.generateParser
import picocli.CommandLine.Command
import java.nio.charset.StandardCharsets

// TODO: テストを書く
@Command(name = "InlineLocalVariable", description = ["Revert Local variable extraction by inlining it"])
class InlineLocalVariable : RepositoryRewriter() {
    override fun rewriteBlob(blobId: ObjectId?, c: Context?): ObjectId {
        // 直後の一文のみで使われているローカル変数の宣言を見つける
        val content = String(source.readBlob(blobId, c), StandardCharsets.UTF_8)
        val afterContent = rewriteContent(content) ?: return super.rewriteBlob(blobId, c)

        return target.writeBlob(afterContent.toByteArray(), c)
    }

    fun rewriteContent(content: String): String? {
        val parser = generateParser()
        parser.setSource(content.toCharArray())
        val tree = generateParser().createAST(null) as CompilationUnit
        val visitor = LocalVariableVisitor(content)
        tree.accept(visitor)
        return visitor.rewrittenContent
    }

}

class LocalVariableVisitor(private val content: String) : ASTVisitor() {
    private var isInsideMethod = false
    private val variableDefUse = mutableMapOf<String, DefUse>()
    private var definingStmt: VariableDeclarationStatement? = null
    private var definedVarName: String? = null
    private var currentStatement: Statement? = null
    var rewrittenContent: String? = null
        private set

    override fun visit(node: MethodDeclaration): Boolean {
        variableDefUse.clear()
        isInsideMethod = true
        return true
    }

    override fun endVisit(node: MethodDeclaration) {
        variableDefUse.forEach { (name, usage) ->
            if (usage.allAppearances.size == 1) {
                // 直後の文で一回だけ使われた
                @Suppress("UNCHECKED_CAST")
                val replacingExpr = (usage.def.fragments() as List<VariableDeclarationFragment>).last()
                if (usage.def.fragments().size == 1) {
                    usage.def.delete()
                }
                val astRewriter = ASTRewrite.create(usage.rightAfterUse.statement.ast)
                val replacing = astRewriter.createCopyTarget(replacingExpr.initializer)
                astRewriter.replace(usage.rightAfterUse.usage, replacing, null)

                val sourceDoc = Document(content)
                val edits = astRewriter.rewriteAST(sourceDoc, null)
                edits.apply(sourceDoc)
                rewrittenContent = sourceDoc.get()
            }
        }

        isInsideMethod = false
    }

    override fun visit(node: VariableDeclarationStatement): Boolean {
        @Suppress("UNCHECKED_CAST")
        val expr = (node.fragments() as List<VariableDeclarationFragment>).last()

        // initializeしている場合のみ?
        definedVarName = expr.name.toString() // 最低1つは宣言している変数がある
        definingStmt = node

        return false
    }

    // AssertStatement
    override fun visit(node: AssertStatement): Boolean {
        currentStatement = node
        node.expression.accept(this)
        return false
    }

    override fun endVisit(node: AssertStatement) {
        endVisitStatement()
    }

    // IfStatement
    override fun visit(node: IfStatement): Boolean {
        currentStatement = node
        node.expression.accept(this)
        return false
    }

    override fun endVisit(node: IfStatement) {
        endVisitStatement()
    }

    // SwitchStatement
    override fun visit(node: SwitchStatement): Boolean {
        currentStatement = node
        node.expression.accept(this)
        return false
    }

    override fun endVisit(node: SwitchStatement) {
        endVisitStatement()
    }

    // ExpressionStatement
    override fun visit(node: ExpressionStatement): Boolean {
        currentStatement = node
        node.expression.accept(this)
        return false
    }

    override fun endVisit(node: ExpressionStatement) {
        endVisitStatement()
    }

    // ReturnStatement
    override fun visit(node: ReturnStatement): Boolean {
        currentStatement = node
        node.expression.accept(this)
        return false
    }

    override fun endVisit(node: ReturnStatement) {
        endVisitStatement()
    }

    // ThrowStatement
    override fun visit(node: ThrowStatement): Boolean {
        currentStatement = node
        node.expression.accept(this)
        return false
    }

    override fun endVisit(node: ThrowStatement) {
        endVisitStatement()
    }


    // ConstructorInvocation
    override fun visit(node: ConstructorInvocation): Boolean {
        // expressionの一部という感じがする?
        currentStatement = node
        node.arguments().forEach { (it as Expression).accept(this) }
        return false
    }

    override fun endVisit(node: ConstructorInvocation) {
        endVisitStatement()
    }

    // SuperConstructorInvocation
    override fun visit(node: SuperConstructorInvocation): Boolean {
        currentStatement = node
        node.expression.accept(this)
        node.arguments().forEach { (it as Expression).accept(this) }
        return false
    }

    override fun endVisit(node: SuperConstructorInvocation) {
        endVisitStatement()
    }

    // Identifier
    override fun visit(node: SimpleName): Boolean {
        val name = node.identifier
        // 位置情報はstartPositionとgetLengthで手に入りはする
        // locationInParentは親より上のノードでも使えるのだろうか……


        // 直前の文で宣言したローカル変数と名前が一致した: mapに追加する
        if (definedVarName == name) {
            variableDefUse.putIfAbsent(name, DefUse(definingStmt!!, Use(currentStatement!!, node), mutableListOf()))

        }

        // ローカル変数ならmapに存在するので、追加する
        variableDefUse[name]?.allAppearances?.add(Use(currentStatement!!, node))

        return false
    }

    override fun visit(node: QualifiedName?): Boolean {
        // Name -> QualifiedNameを防止: SimpleNameだけを見るように
        return false
    }

    // TryStatement
    override fun visit(node: TryStatement): Boolean {
        currentStatement = node

        if (node.resources().isEmpty()) {
            // resourcesがない: bodyの最初の1文を判定に使う(ほんまか)
            node.body.statements().firstOrNull()?.let {
                (it as Statement).accept(this)
            }
        } else {
            node.resources().forEach { (it as Expression).accept(this) }
        }

        return false
    }

    override fun endVisit(node: TryStatement) {
        endVisitStatement()
    }

    // Ignoring
    override fun visit(node: DoStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: ForStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: WhileStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: EnhancedForStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: SwitchCase): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: SynchronizedStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: TypeDeclarationStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: BreakStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: ContinueStatement): Boolean {
        endVisitStatement()
        return false
    }

    override fun visit(node: EmptyStatement): Boolean {
        endVisitStatement()
        return false
    }

    private fun endVisitStatement() {
        currentStatement = null
        definedVarName = null
        definingStmt = null
    }
}

data class Use(
    val statement: Statement,
    val usage: SimpleName
)

data class DefUse(
    val def: VariableDeclarationStatement,
    var rightAfterUse: Use,
    val allAppearances: MutableList<Use>
)
