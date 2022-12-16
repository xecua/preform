package page.caffeine.preform.filter.marker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue

class QuickRemedyMarkerTests : FunSpec({
    test("Regex check") {
        QuickRemedyCommitMarker.MESSAGE_PATTERN.containsMatchIn("revert former commit").shouldBeTrue()
        QuickRemedyCommitMarker.MESSAGE_PATTERN.containsMatchIn("remove print from last commit").shouldBeTrue()
        QuickRemedyCommitMarker.MESSAGE_PATTERN.containsMatchIn("previous commit did not fix bug.").shouldBeTrue()
        QuickRemedyCommitMarker.MESSAGE_PATTERN.containsMatchIn("invalid prev commit fix").shouldBeTrue()
    }
})
