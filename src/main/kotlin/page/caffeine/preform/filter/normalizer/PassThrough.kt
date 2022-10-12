package page.caffeine.preform.filter.normalizer

import page.caffeine.preform.util.RepositoryRewriter
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
