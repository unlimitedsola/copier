import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "love.sola.copier"
version = "1.0"

plugins {
    application
    kotlin("jvm") version "1.3.41"
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

application {
    mainClassName = "love.sola.copier.MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx32M", "-XX:+UseSerialGC", "-XX:ReservedCodeCacheSize=16m")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=enable")
    }
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("no.tornado:tornadofx:1.7.19")
    implementation("net.java.dev.jna:jna-platform:5.3.1")
    implementation("com.github.thomasnield:rxkotlinfx:2.2.2")
    implementation("io.reactivex.rxjava2:rxjava:2.2.10")
}
