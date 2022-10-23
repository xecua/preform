plugins {
    kotlin("jvm") version "1.6.21"
    application
    eclipse
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

group = "page.caffeine"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val kotestVersion = "5.5.1"

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.2")
    implementation("ch.qos.logback:logback-classic:1.4.4")

    implementation("com.github.sh5i:git-stein:v0.5.0")

    implementation("info.picocli:picocli:4.6.3")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.29.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r")
    
    implementation("com.github.tsantalis:refactoring-miner:2.3.2")

    implementation("com.github.gumtreediff:core:3.0.0")
    implementation("com.github.gumtreediff:client:3.0.0")
    implementation("com.github.gumtreediff:gen.jdt:3.0.0")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}

kotlin {
    // toolchain https://blog.jetbrains.com/kotlin/2021/11/gradle-jvm-toolchain-support-in-the-kotlin-plugin/
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(11))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

application {
    mainClass.set("page.caffeine.preform.MainKt")
}
