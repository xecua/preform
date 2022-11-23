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
import page.caffeine.preform.filter.marker.QuickRemedyMarker
import page.caffeine.preform.filter.marker.RevertCommitMarker
import page.caffeine.preform.filter.normalizer.Format
import page.caffeine.preform.filter.normalizer.InlineLocalVariable
import page.caffeine.preform.filter.normalizer.Linebreak
import page.caffeine.preform.filter.normalizer.PassThrough
import page.caffeine.preform.filter.normalizer.RemoveComment
import page.caffeine.preform.filter.normalizer.TrivialKeyword
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
        Format::class,
        PassThrough::class,
        Linebreak::class,
        QuickRemedyMarker::class,
        InlineLocalVariable::class,
        RemoveComment::class,
        RevertCommitMarker::class,
        NonEssentialDiffMarker::class,
        TrivialKeyword::class,
    ]
)
class Preform : Callable<Int> {
    @Parameters(index = "0", paramLabel = "<repo>", description = ["Source repository path"])
    lateinit var source: File

    // @Parameters(index = "1", paramLabel = "<filter>", description = ["Using filter"], arity="1..*")
    var filters: MutableList<RepositoryRewriter> = mutableListOf()
    var directories: MutableList<File> = mutableListOf()

    @Parameters(index = "1", paramLabel = "<repo>", description = ["Target repository path"])
    lateinit var target: File

    @Option(names = ["-d", "--duplicate"], description = ["Duplicate source repo before rewriting"])
    var isDuplicating: Boolean = false

    @Option(names = ["--clean"], description = ["Delete destination repo beforehand if exists"])
    var isCleaning: Boolean = false

    @Option(names = ["--bare"], description = ["treat that repos are bare"])
    var isBare = false

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

        openRepositories(filters.size) { source, target, i ->
            val filter = filters[i]

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

    private fun openRepositories(times: Int, action: (Repository, Repository, Int) -> Unit) {
        if (times <= 0) {
            return
        }

        for (i in 0 until times) {
            val src = if (i == 0) {
                source
            } else {
                directories[i - 1]
            }

            val dst = if (i == times - 1) {
                if (isCleaning && target.exists()) {
                    log.info { "Cleaning destination repository $target" }
                    target.deleteRecursively()
                }
                target
            } else {
                val dir = Files.createTempDirectory(i.toString()).toFile()
                dir.deleteRecursively() // 名前だけ確保して削除
                directories.add(dir)
                dir
            }

            createRepository(src, false).use { source ->
                createRepository(dst, true).use { target ->
                    action(source, target, i)
                }
            }
        }

        // 最後にsrcのファイルをdstに移す? (若干効率が悪いかもしれない)
        if (isDuplicating) {
            log.info { "Duplicating source repository $source to $target" }
            source.copyTo(target, true)
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
