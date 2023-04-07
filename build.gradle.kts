plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.0-Beta")
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