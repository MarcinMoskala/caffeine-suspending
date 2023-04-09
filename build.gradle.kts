plugins {
    kotlin("jvm") version "1.8.0"
    application
    `maven-publish`
}

group = "com.marcinmoskala"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    api("com.github.ben-manes.caffeine:caffeine:3.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.0-Beta")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }
}