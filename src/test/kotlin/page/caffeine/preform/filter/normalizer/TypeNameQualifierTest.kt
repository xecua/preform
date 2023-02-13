package page.caffeine.preform.filter.normalizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TypeNameQualifierTest : FunSpec ({
    val it = TypeNameQualifier()
    
    context("invalid cases") {
        test("there is a wildcard import") {
            it.rewriteContent("""
                import java.util.*;
                
                class Example {
                    void foo() {
                        HashMap<String, Integer> foo = new HashMap<>();
                        System.out.println(foo);
                    }
                }
            """.trimIndent()).shouldBe("""
                import java.util.*;
                
                class Example {
                    void foo() {
                        HashMap<String, Integer> foo = new HashMap<>();
                        System.out.println(foo);
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
                        HashMap<String, Integer> foo = new HashMap<>();
                        System.out.println(foo);
                    }
                }
            """.trimIndent()).shouldBe("""
                package main;
                
                class Example {
                    void foo() {
                        java.util.HashMap<String, Integer> foo = new java.util.HashMap<>();
                        System.out.println(foo);
                    }
                }
            """.trimIndent())
        }
    }
})
