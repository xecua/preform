package dev.koffein.preform

import jp.ac.titech.c.se.stein.core.RepositoryRewriter
import mu.KotlinLogging

class Format : RepositoryRewriter() {
    init {
        logger.debug { "foo" }
        print("bar")
    }
    
    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
