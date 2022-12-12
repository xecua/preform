package page.caffeine.preform.filter.normalizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TrivialTypeTests : FunSpec ({
    val it = TrivialType()
    
    context("invalid cases") {
        test("there is a wildcard import") {
            it.rewriteContent("""
                import java.util.*;
                
                class Example {
                    void foo() {
                        HashMap<String, Int> foo = new HashMap<>();
                    }
                }
            """.trimIndent()).shouldBe("""
                import java.util.*;
                
                class Example {
                    void foo() {
                        HashMap<String, Int> foo = new HashMap<>();
                    }
                }
            """.trimIndent())
        }
    }
    context("valid cases") {
        test("there is an import statement") {
            it.rewriteContent("""
                package main;
                import java.util.HashMap;
                
                class Example {
                    void foo() {
                        HashMap<String, Int> foo = new HashMap<>();
                    }
                }
            """.trimIndent()).shouldBe("""
                package main;
                
                class Example {
                    void foo() {
                        java.util.HashMap<String, Int> foo = new java.util.HashMap<>();
                    }
                }
            """.trimIndent())
        }
    }
})
