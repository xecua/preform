package page.caffeine.preform

import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import picocli.CommandLine
import picocli.CommandLine.ExecutionException
import picocli.CommandLine.ParameterException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val cmdline = CommandLine(Preform())
    cmdline.isExpandAtFiles = true
    cmdline.setExecutionStrategy { parseResult ->
        val spec = parseResult.commandSpec()
        val preformApp = spec.userObject() as Preform

        if (CommandLine.printHelpIfRequested(parseResult)) return@setExecutionStrategy 0

        if (parseResult.subcommands().size == 0) {
            throw ParameterException(spec.commandLine(), "Subcommands are required.")
        }

        parseResult.subcommands().forEach {
            val userObject = it.commandSpec().userObject() as RepositoryRewriter
            preformApp.filters.add(userObject)
        }

        return@setExecutionStrategy try {
            preformApp.call()
        } catch (e: Exception) {
            throw ExecutionException(spec.commandLine(), "Execution failed.", e)
        }
    }
    exitProcess(cmdline.execute(*args))
}
