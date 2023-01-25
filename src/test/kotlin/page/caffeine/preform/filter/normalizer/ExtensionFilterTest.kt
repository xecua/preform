package page.caffeine.preform.filter.normalizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class ExtensionFilterTest : FunSpec({
    val it = ExtensionFilter()

    test("rewriteBlob") {
        it.extensions = arrayOf("java")
        
        it.testFileName("a.c").shouldBeFalse()
        it.testFileName("A.javascript").shouldBeFalse()
        it.testFileName("a.rjava").shouldBeFalse()
        
        it.testFileName("A.java").shouldBeTrue()
        it.testFileName("A.tmp.java").shouldBeTrue()
    }
})
