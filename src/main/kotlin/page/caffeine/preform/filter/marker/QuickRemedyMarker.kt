package page.caffeine.preform.filter.marker

import jp.ac.titech.c.se.stein.core.Context
import mu.KotlinLogging
import org.eclipse.jgit.revwalk.RevCommit
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command
import java.time.Duration

@Command(name = "QuickRemedyMarker", description = ["Mark Quick Remedy Commits."])
class QuickRemedyMarker : RepositoryRewriter() {
    override fun rewriteCommitMessage(message: String?, c: Context?): String {
        val commit = c?.commit ?: return super.rewriteCommitMessage(message, c)

        // of course, quick remedy commit is not merge commit
        if (commit.parentCount != 1) {
            return super.rewriteCommitMessage(message, c)
        }

        // 1: done by same author as the parent commit
        val commitAuthor = commit.authorIdent ?: return super.rewriteCommitMessage(message, c)

        val parentAuthor = (target.parseAny(commit.getParent(0), c) as? RevCommit)?.authorIdent
            ?: return super.rewriteCommitMessage(message, c)

        if (commitAuthor.name != parentAuthor.name) {
            return super.rewriteCommitMessage(message, c)
        }

        // 2: done within 5 minutes from the parent commit (w.r.t. author date)
        val commitTime = commitAuthor.`when`.toInstant().atZone(commitAuthor.timeZone.toZoneId())
        val parentCommitTime = parentAuthor.`when`.toInstant().atZone(parentAuthor.timeZone.toZoneId())
        val duration = Duration.between(parentCommitTime, commitTime)
        if (duration > THRESHOLD_DURATION) {
            return super.rewriteCommitMessage(message, c)
        }

        // 3: message contains "(former|last|prev|previous) commit"
        if (MESSAGE_PATTERN.find(commit.fullMessage) == null) {
            return super.rewriteCommitMessage(message, c)
        }

        if (isDryRunning) {
            logger.info { "${commit.name} is a quick remedy commit" }
        }

        return """
        |${commit.fullMessage}
        |
        |[Preform] Quick Remedy
        """.trimMargin()
    }

    companion object {
        val logger = KotlinLogging.logger {}

        val THRESHOLD_DURATION: Duration = Duration.ofMinutes(5)
        val MESSAGE_PATTERN = Regex("""(former|last|prev|previous) commit""")
    }
}
