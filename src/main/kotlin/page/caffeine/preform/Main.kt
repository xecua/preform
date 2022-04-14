package page.caffeine.preform

import picocli.CommandLine

fun main(args: Array<String>) {
    val cmdline = CommandLine(Preform())
    cmdline.setExpandAtFiles(true)
    System.exit(cmdline.execute(*args))
}
