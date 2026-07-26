plugins {
    kotlin("jvm") version "2.0.0"
    id("io.ktor.plugin") version "3.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "com.wearsic.server"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-http-content")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

ktor {
    fatJar {
        archiveFileName.set("wearsic-server.jar")
    }
}
