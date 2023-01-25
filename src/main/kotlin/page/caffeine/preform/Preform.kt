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
import page.caffeine.preform.filter.marker.NonEssentialDiffMarker
import page.caffeine.preform.filter.marker.RevertCommitMarker
import page.caffeine.preform.filter.PassThrough
import page.caffeine.preform.filter.normalizer.*
import page.caffeine.preform.filter.restructurer.RevertCommitSquasher
import picocli.CommandLine.Command
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable

@Command(
    name = "preform",
    mixinStandardHelpOptions = true,
    subcommandsRepeatable = true,
    subcommands = [
        Formatter::class,
        PassThrough::class,
        LinebreakNormalizer::class,
        ExtensionFilter::class,
        LocalVariableInliner::class,
        CommentRemover::class,
        RevertCommitSquasher::class,
        RevertCommitMarker::class,
        NonEssentialDiffMarker::class,
        KeywordNormalizer::class,
        TypeNameQualifier::class,
    ]
)
class Preform : Callable<Int> {
    @Parameters(index = "0", paramLabel = "<source>", description = ["Source repository path"])
    lateinit var source: File

    // @Parameters(index = "1", paramLabel = "<filter>", description = ["Using filter"], arity="1..*")
    var filters: MutableList<RepositoryRewriter> = mutableListOf()
    var directories: MutableList<File> = mutableListOf()

    @Parameters(index = "1", paramLabel = "<destination>", description = ["Destination path"])
    lateinit var target: File

    @Option(names = ["-d", "--duplicate"], description = ["Duplicate source repo before rewriting"])
    var isDuplicating: Boolean = false

    @Option(names = ["--clean"], description = ["Delete destination repo beforehand if exists"])
    var isCleaning: Boolean = false

    @Option(names = ["--bare"], description = ["treat that repos are bare"])
    var isBare = false

    @Option(
        names = ["-s", "--save"],
        description = ["Save intermediate results.", "In this case, <destination> become parent directory of the repositories."]
    )
    var saveRepository = false
    // 中間リポジトリの命名規則を指定できるようにしたい

    @Option(
        names = ["--log"],
        paramLabel = "<level>",
        description = ["log level (default: \${DEFAULT-VALUE})"],
        converter = [LevelConverter::class]
    )
    var logLevel: Level = Level.INFO

    class LevelConverter : ITypeConverter<Level> {
        override fun convert(value: String): Level {
            return Level.valueOf(value)
        }
    }

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

        openRepositories { source, target, filter ->
            filter.initialize(source, target)
            log.info { "Start rewriting by $filter, ${source.directory} -> ${target.directory}" }
            val c = Context.init()
            val start = Instant.now()
            filter.rewrite(c)
            val finish = Instant.now()
            log.info { "Finished rewriting. Runtime: ${Duration.between(start, finish).toMillis()} ms" }
        }

        // TODO: cleanup through all filters

        return 0
    }

    private fun openRepositories(action: (Repository, Repository, RepositoryRewriter) -> Unit) {
        if (filters.isEmpty()) {
            return
        }

        if (isCleaning && target.exists()) {
            log.info { "Cleaning destination repository $target" }
            target.deleteRecursively()
        }

        var repoName = source.name
        directories.add(source)
        filters.forEachIndexed { i, filter ->
            val src = directories[i]

            val dst = if (saveRepository) {
                repoName += "-${filter::class.simpleName}"
                target.resolve(repoName)
            } else {
                if (i == filters.size - 1) {
                    target
                } else {
                    val dir = Files.createTempDirectory(i.toString()).toFile()
                    dir.deleteRecursively() // 名前だけ確保して削除
                    dir
                }
            }
            directories.add(dst)

            if (i == filters.size - 1 && isDuplicating) {
                log.info { "Duplicating source repository $source to ${directories.last()}" }
                source.copyRecursively(directories.last(), true)
            }

            createRepository(src, false).use { source ->
                createRepository(dst, true).use { target ->
                    action(source, target, filter)
                }
            }
        }
    }

    private fun createRepository(dir: File, createIfAbsent: Boolean): Repository {
        val result = FileRepositoryBuilder().apply {
            if (isBare) {
                gitDir = dir
                setBare()
            } else {
                gitDir = File(dir, Constants.DOT_GIT)
                workTree = dir
            }
            readEnvironment()
        }.build()

        if (!dir.exists() && createIfAbsent) {
            result.create(isBare)
        }

        return result
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }

}
