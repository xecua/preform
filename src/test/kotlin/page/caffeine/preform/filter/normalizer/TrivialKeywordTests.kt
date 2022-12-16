package page.caffeine.preform.filter.normalizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TrivialKeywordTests : FunSpec({
    val it = TrivialKeywordNormalizer()

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

        test("local variable") {
            it.rewriteContent(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        int i = 1;
                        return i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        int i = 1;
                        return i;
                    }
                }
                """.trimIndent()
            )
        }
        test("same name field of another class") {
            it.rewriteContent(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        Foo f = new Foo();
                        return f.i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        Foo f = new Foo();
                        return f.i;
                    }
                }
                """.trimIndent()
            )
        }
        test("`this` is already exist") {
            it.rewriteContent(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        return this.i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        return this.i;
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
        test("field access") {
             it.rewriteContent(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        return i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    int i = 0;
                    int foo() {
                        return this.i;
                    }
                }
                """.trimIndent()
            )       
        }
        test("method invocation") {
            it.rewriteContent(
                """
                class Example {
                    int bar() { return 1; }
                    int foo() {
                        return bar();
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class Example {
                    int bar() { return 1; }
                    int foo() {
                        return this.bar();
                    }
                }
                """.trimIndent()
            )
        }
    }

})
