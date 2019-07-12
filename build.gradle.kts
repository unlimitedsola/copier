import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "love.sola.copier"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.3.41"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
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
    implementation("com.github.thomasnield:rxkotlinfx:2.2.2")
}
