package page.caffeine.preform

import ch.qos.logback.classic.Level
import jp.ac.titech.c.se.stein.core.Context
import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import mu.KotlinLogging
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable
import java.util.function.BiConsumer

@Command(
    name = "preform",
    subcommandsRepeatable = true,
)
class Preform : Callable<Int> {
    @Parameters(index = "0", paramLabel = "<repo>", description = ["Source repository path"])
    lateinit var source: File

    @Parameters(index = "1", paramLabel = "<filter>", description = ["Using filter"], arity="1..*")
    lateinit var filters: List<String>

    @ArgGroup(exclusive = false, multiplicity = "0..1")
    var output: OutputOptions? = null

    class OutputOptions {
        @Option(names = ["-o", "--output"], description = ["Output directory"])
        var target: File? = null

        @Option(names = ["-d", "--duplicate"], description = ["Duplicate source repo and overwrite it"])
        var isDuplicating: Boolean = false

        @Option(names = ["--clean"], description = ["Delete destination repo beforehand if exists"])
        var isCleaning: Boolean = false
    }


    @Option(names = ["--bare"], description = ["treat that repos are bare"])
    var isBare = false

    @Option(
        names = ["--log"],
        paramLabel = "<level>",
        description = ["log level (default: \${DEFAULT-VALUE})"],
        order = LOW,
        converter = [LevelConverter::class]
    )
    var logLevel: Level = Level.INFO

    class LevelConverter : ITypeConverter<Level> {
        override fun convert(value: String): Level {
            return Level.valueOf(value)
        }
    }

    @Option(names = ["-q", "--quiet"], description = ["quiet mode (same as --log=ERROR)"], order = LOW)
    fun setQuiet(isQuiet: Boolean) {
        if (isQuiet) {
            logLevel = Level.ERROR
        }
    }

    @Option(names = ["-v", "--verbose"], description = ["verbose mode (same as --log=DEBUG)"], order = LOW)
    fun setVerbose(isVerbose: Boolean) {
        if (isVerbose) {
            logLevel = Level.DEBUG
        }
    }

    @Option(names = ["--help"], description = ["show this help message and exit"], order = LAST, usageHelp = true)
    var helpRequested = false

    @Option(
        names = ["--version"], description = ["print version information and exit"], order = LAST,
        versionHelp = true
    )
    var versionInfoRequested = false

    val loader = Loader<RepositoryRewriter>()

    private fun setLoggerLevel(name: String, level: Level) {
        val logger = LoggerFactory.getLogger(name) as ch.qos.logback.classic.Logger
        logger.level = level
        log.debug("Set log level of {} to {}", name, level)
    }

    override fun call(): Int {
        setLoggerLevel(Logger.ROOT_LOGGER_NAME, logLevel)
        if (logLevel == Level.DEBUG) {
            setLoggerLevel("org.eclipse.jgit", Level.INFO)
        }

        openRepositories { source, target ->
            // とりあえず順番に?
            filters.forEach {
                val filter = loader.load(it)
                if (filter == null) {
                    log.warn { "Filter $it was not found. Skipping." }
                    return@forEach
                }

                filter.initialize(source, target)
                log.info { "Start rewriting by $it, ${source.directory} -> ${target.directory}" }
                val c = Context.init()
                filter.rewrite(c)

            }
        }
        
        return 0
    }

    private fun openRepositories(action: BiConsumer<Repository, Repository>) {
        if (output == null) {
            createRepository(source, false).use { repo ->
                action.accept(repo, repo)
            }
            return
        }

        if (output?.isCleaning == true && output?.target?.exists() == true) {
            log.info { "Cleaning destination repository ${output?.target}" }
            output?.target?.deleteRecursively()
        }

        if (output?.isDuplicating == true) {
            log.info { "Duplicating source repository $source to ${output?.target}" }
            source.copyRecursively(output?.target!!, overwrite = true)
            createRepository(output?.target!!, false).use { repo ->
                action.accept(repo, repo)
            }
            return
        }

        createRepository(source, false).use { source ->
            createRepository(output?.target!!, true).use { target ->
                action.accept(source, target)
            }
        }

    }

    private fun createRepository(dir: File, createIfAbsent: Boolean): Repository {
        val builder = FileRepositoryBuilder()
        if (isBare) {
            builder.setGitDir(dir).setBare()
        } else {
            val dotgit = File(dir, Constants.DOT_GIT)
            builder.setWorkTree(dir).setGitDir(dotgit)
        }

        val result = builder.readEnvironment().build()
        if (!dir.exists() && createIfAbsent) {
            result.create(isBare)
        }

        return result
    }

    companion object {
        private val log = KotlinLogging.logger {}

        private const val MIDDLE = 5
        private const val LOW = 8
        private const val LAST = 10
    }

}
