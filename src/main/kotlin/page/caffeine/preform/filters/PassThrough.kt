package page.caffeine.preform.filters

import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import mu.KotlinLogging

class PassThrough : RepositoryRewriter() {
    init {
        log.info { "PassThrough" }
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
