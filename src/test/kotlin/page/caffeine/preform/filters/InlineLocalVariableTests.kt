package page.caffeine.preform.filters

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InlineLocalVariableTests : FunSpec({
    val it = InlineLocalVariable()

    context("invalid cases") {
        test("simple statements") {
            it.rewriteContent(
                """
                int i = 1;
                foi(i);
                """.trimIndent()
            ).shouldBe(
                """
                int i = 1;
                foi(i);
                """.trimIndent()
            )
        }

        test("outside method") {
            it.rewriteContent(
                """
                class A {
                    int i = 1;
                    foi(i);
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    int i = 1;
                    foi(i);
                }
            """.trimIndent()
            )
        }

        test("in case there are many use") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = 1 + 2 - 3 * 4;
                        foi(i);
                        System.out.println("Hello");
                        System.out.println(i);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        int i = 1 + 2 - 3 * 4;
                        foi(i);
                        System.out.println("Hello");
                        System.out.println(i);
                    }
                }
                """.trimIndent()
            )
        }
    }
    context("simple cases") {
        test("literal to method invocation") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = 1;
                        foi(i);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        foi(1);
                    }
                }
            """.trimIndent()
            )
        }
        test("expression to method invocation") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = 1 + 2 - 3 * 4;
                        foi(i);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        foi(1 + 2 - 3 * 4);
                    }
                }
                """.trimIndent()
            )
        }
        test("expression with newline to method invocation") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = 1 + 2
                                - 3 * 4;
                        foi(i);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        foi(1 + 2
                                - 3 * 4);
                    }
                }
                """.trimIndent() // 空白とかもそのまま置換される: formatting推奨?
            )
        }

        test("in case there are many subjects") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = 1 + 2 - 3 * 4;
                        foi(i);
                        int j = 5;
                        baz(j);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        foi(1 + 2 - 3 * 4);
                        baz(5);
                    }
                }
                """.trimIndent()
            )
        }
        test("in case there are many methods") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = 1 + 2 - 3 * 4;
                        foi(i);
                    }
                    void buz() {
                        int j = 5;
                        baz(j);
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        foi(1 + 2 - 3 * 4);
                    }
                    void buz() {
                        baz(5);
                    }
                }
                """.trimIndent()
            )
        }
    }

    context("supplement cases") {
        test("IfStatement") {
            it.rewriteContent(
                """
                class A {
                    void bar() {
                        int i = foo();
                        if (i < 100) {
                          System.out.println("foo");
                        } else {
                          System.out.println("i");
                        }
                    }
                }
                """.trimIndent()
            ).shouldBe(
                """
                class A {
                    void bar() {
                        if (foo() < 100) {
                          System.out.println("foo");
                        } else {
                          System.out.println("i");
                        }
                    }
                }
            """.trimIndent()
            )
        }
    }
})
