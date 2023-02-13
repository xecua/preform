package page.caffeine.preform.filter.normalizer

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KeywordNormalizerTest : FunSpec({
    val it = KeywordNormalizer()

    context("invalid cases") {
        test("non-void method") {
            it.rewriteContent(
                """
                package main;
                class Example {
                    int foo() {
                        return 1;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
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
                package main;
                class Base {
                    int i = 0;
                    Base(int i) {
                        this.i = i;
                    }
                }
                class Example extends Base {
                    Example() {
                        super(1);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
                class Base {
                    int i = 0;
                    Base(int i) {
                        this.i = i;
                    }
                }
                class Example extends Base {
                    Example() {
                        super(1);
                    }
                }
                """.trimIndent()
            )
        }

        test("local variable") {
            it.rewriteContent(
                """
                package main;
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
                package main;
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
                package main;
                class Example {
                    int i = 0;
                    int foo() {
                        Example f = new Example();
                        return f.i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
                class Example {
                    int i = 0;
                    int foo() {
                        Example f = new Example();
                        return f.i;
                    }
                }
                """.trimIndent()
            )
        }
        test("`this` is already exist") {
            it.rewriteContent(
                """
                package main;
                class Example {
                    int i = 0;
                    int foo() {
                        return this.i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
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
                package main;
                class Example {
                    void foo() {
                        int foo = 1;
                        System.out.println(foo);
                        return;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
                class Example {
                    void foo() {
                        int foo = 1;
                        System.out.println(foo);
                    }
                }
                """.trimIndent()
            )
        }
        test("last return in void method") {
            it.rewriteContent(
                """
                package main;
                class Example {
                    void foo(boolean flag) {
                        if (flag) {
                            return;
                        }
                        int foo = 1;
                        System.out.println(foo);
                        return;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
                class Example {
                    void foo(boolean flag) {
                        if (flag) {
                            return;
                        }
                        int foo = 1;
                        System.out.println(foo);
                    }
                }
                """.trimIndent()
            )
        }

        test("default constructor call") {
            it.rewriteContent(
                """
                package main;
                class Example {
                    private int foo;
                    Example() {
                        super();
                        this.foo = 2;
                        System.out.println(foo);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
                class Example {
                    private int foo;
                    Example() {
                        this.foo = 2;
                        System.out.println(foo);
                    }
                }
                """.trimIndent()
            )
        }
        test("field access") {
             it.rewriteContent(
                """
                package main;
                class Example {
                    int i = 0;
                    int foo() {
                        return i;
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
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
                package main;
                class Example {
                    int bar() { return 1; }
                    int foo() {
                        return bar();
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                package main;
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
