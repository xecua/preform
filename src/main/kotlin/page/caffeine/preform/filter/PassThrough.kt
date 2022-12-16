package page.caffeine.preform.filter

import mu.KotlinLogging
import page.caffeine.preform.util.RepositoryRewriter
import picocli.CommandLine.Command

@Command(description = ["Do nothing (for testing)"])
class PassThrough : RepositoryRewriter() {
    init {
        logger.info { "PassThrough" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
