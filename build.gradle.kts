plugins {
    kotlin("jvm") version "2.3.20"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

javafx {
    version = "25"
    modules("javafx.controls", "javafx.graphics")
    setPlatform("mac-aarch64")
}

application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}