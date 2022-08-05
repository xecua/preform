package page.caffeine.preform.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RemoveCommentTest: FunSpec({
    val it = RemoveComment()
    
    test("check basic functionality") {
        it.rewriteContent(
            """
            // a
            // b
            int i = 0;
            /* multiple
             * lines
             * comment */
            """.trimIndent()
        ).shouldBe(
            """
            int i = 0;
            """.trimIndent()
        )
    }
})
