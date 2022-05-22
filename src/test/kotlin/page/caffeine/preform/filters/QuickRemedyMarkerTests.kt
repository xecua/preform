package page.caffeine.preform.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue

class QuickRemedyMarkerTests : FunSpec({
    test("Regex check") {
        QuickRemedyMarker.MESSAGE_PATTERN.containsMatchIn("revert former commit").shouldBeTrue()
        QuickRemedyMarker.MESSAGE_PATTERN.containsMatchIn("remove print from last commit").shouldBeTrue()
        QuickRemedyMarker.MESSAGE_PATTERN.containsMatchIn("previous commit did not fix bug.").shouldBeTrue()
        QuickRemedyMarker.MESSAGE_PATTERN.containsMatchIn("invalid prev commit fix").shouldBeTrue()
    }
})
