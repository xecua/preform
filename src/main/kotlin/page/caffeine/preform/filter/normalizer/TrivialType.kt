package page.caffeine.preform.filter.normalizer

import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.EntrySet
import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import mu.KotlinLogging
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.ImportDeclaration
import org.eclipse.jdt.core.dom.Name
import org.eclipse.jdt.core.dom.PackageDeclaration
import org.eclipse.jdt.core.dom.SimpleType
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite
import org.eclipse.jface.text.Document
import org.eclipse.jgit.lib.ObjectId
import page.caffeine.preform.util.generateParser
import picocli.CommandLine.Command
import java.nio.charset.StandardCharsets

@Command(name = "TrivialType", description = ["Normalize types"])
class TrivialType : RepositoryRewriter() {
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
        parser.setUnitName("")
        val tree = try {
            parser.createAST(null) as CompilationUnit
        } catch (e: Exception) {
            logger.warn(e) { "Ignoring." }
            return content
        }
        
        val visitor = TrivialTypeVisitor(content, tree)
        tree.accept(visitor)
       return visitor.getRewrittenContent()
    }


    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

class TrivialTypeVisitor(private val content: String, rootNode: CompilationUnit): ASTVisitor() {
    private var astRewrite = ASTRewrite.create(rootNode.ast)
    private var packageName: String? = null
    private val importedTypes: MutableMap<String, Name> = mutableMapOf()
    
    fun getRewrittenContent(): String {
        val doc = Document(content)
        val edits = astRewrite.rewriteAST(doc, null)
        edits.apply(doc)
        return doc.get() ?: content
    }

    override fun visit(node: PackageDeclaration): Boolean {
        this.packageName = node.name.fullyQualifiedName
        return super.visit(node)
    }
    
    override fun visit(node: ImportDeclaration): Boolean {
        if (!node.isStatic && !node.isOnDemand) {
            val lastPart = node.name.fullyQualifiedName.split(".").last()
            importedTypes[lastPart] = node.name
            // Remove this import statement
            astRewrite.remove(node, null)
        }
        return super.visit(node)
    }
    
    override fun visit(node: SimpleType): Boolean {
        val name = node.name.fullyQualifiedName
        if (importedTypes.containsKey(name)) {
            val qualifiedName = astRewrite.createCopyTarget(importedTypes[name]) as Name
            val newNode = node.parent.ast.newSimpleType(qualifiedName)
            astRewrite.replace(node, newNode, null)
        }
        
        return super.visit(node)
    }
    
    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
