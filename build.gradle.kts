import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

val kotestVersion = "5.4.1"

dependencies {
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    implementation("com.github.sh5i:git-stein:v0.5.0")
    implementation("com.github.YoshikiHigo:CommentRemover:v0.1.1")

    implementation("info.picocli:picocli:4.6.3")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.29.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.2.0.202206071550-r")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

application {
    mainClass.set("page.caffeine.preform.MainKt")
}
