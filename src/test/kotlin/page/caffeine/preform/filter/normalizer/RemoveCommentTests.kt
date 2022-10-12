package page.caffeine.preform.filter.normalizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RemoveCommentTests : FunSpec({
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
            /* a */ int j = 1; /* b */
            String foo = "foo"; // after comment
            String bar = "bar";
                /* po */
            """.trimIndent()
            // comment in last line
        ).shouldBe(
            """
            int i = 0;
             int j = 1; 
            String foo = "foo"; 
            String bar = "bar";
            
            """.trimIndent()
        )
        // 注: コメントによって消滅した行を消すことはできるが、
        // コメントの前後の空白を取り除くことはできない(そのため、Formatの実行を推奨)
        // また、最後にコメントがあった場合、その前の行の改行文字は残る
        it.rewriteContent(
            """
            String foo = "foo"; // after comment
            // comment in last line
            """.trimIndent()
            // comment in last line
        ).shouldBe(
            """
            String foo = "foo"; 
            
            """.trimIndent()
        )
    }
})
