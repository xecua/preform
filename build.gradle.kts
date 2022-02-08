import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

group = "page.caffeine"
version = "0.0.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies { 
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.16")
    implementation("ch.qos.logback:logback-classic:1.2.8")
    
    implementation("com.github.sh5i:git-stein:v0.5.0")
    
    implementation("info.picocli:picocli:4.6.2")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.28.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.0.0.202111291000-r")
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("page.caffeine.preform.MainKt")
}
