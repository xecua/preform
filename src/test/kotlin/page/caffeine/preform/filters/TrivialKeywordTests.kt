package page.caffeine.preform.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TrivialKeywordTests : FunSpec({
    val it = TrivialKeyword()

    context("invalid cases") {
        test("non-void method") {
            it.rewriteContent(
                """
                class Example {
                    int foo() {
                        return 1;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    int foo() {
                        return 1;
                    }
                }
                """.trimIndent()
            )
        }
        
        test("already exist non-empty constructor call") {
            it.rewriteContent(
                """
                class Example {
                    void foo() {
                        super(1);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    void foo() {
                        super(1);
                    }
                }
                """.trimIndent()
            )
        }
    }
    context("valid cases") {
        test("last return in void method") {
            it.rewriteContent(
                """
                class Example {
                    void foo() {
                        int foo = 1;
                        return;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    void foo() {
                        int foo = 1;
                    }
                }
                """.trimIndent()
            )
        }
        test("last return in void method") {
            it.rewriteContent(
                """
                class Example {
                    void foo(boolean flag) {
                        if (flag) {
                            return;
                        }
                        int foo = 1;
                        return;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    void foo(boolean flag) {
                        if (flag) {
                            return;
                        }
                        int foo = 1;
                    }
                }
                """.trimIndent()
            )
        }
        
        test("default constructor call") {
            it.rewriteContent(
                """
                class Example {
                    private int foo;
                    Example() {
                        super();
                        this.foo = 2;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    private int foo;
                    Example() {
                        this.foo = 2;
                    }
                }
                """.trimIndent()
            )
        }
    }

})
