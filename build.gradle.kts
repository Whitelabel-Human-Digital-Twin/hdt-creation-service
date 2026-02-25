val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.2.1"
    kotlin("plugin.serialization") version "2.1.10"
}

group = "com.example.com"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Whitelabel-Human-Digital-Twin/whdt") // or the correct GitHub repo
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
        }
    }
    mavenCentral()
}

dependencies {
    /** KTOR SERVER DEPENDENCIES */
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    /** KTOR CLIENT DEPENDENCIES */
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")

    /** KOTLIN DEPENDENCIES */
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("io.github.whdt:whdt-core:0.4.0")
    implementation("io.github.whdt:whdt-distributed:0.2.0")
    implementation("io.github.whdt:whdt-csv-parser:0.1.2")
    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

kotlin {
    jvmToolchain(23)
}
