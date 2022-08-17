package page.caffeine.preform.filters

import page.caffeine.preform.utils.RepositoryRewriter
import mu.KotlinLogging
import picocli.CommandLine.Command

@Command(name = "PassThrough")
class PassThrough : RepositoryRewriter() {
    init {
        logger.info { "PassThrough" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
