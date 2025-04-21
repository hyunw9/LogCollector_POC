import org.w3c.dom.DOMImplementation

plugins {
    kotlin("jvm") version "2.1.10"
}

group = "hyunw9"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.linecorp.armeria:armeria:1.32.5")
    implementation("io.micrometer:micrometer-core:1.11.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.0")
    implementation("com.linecorp.armeria:armeria-kotlin:1.25.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("ch.qos.logback:logback-classic:1.4.11")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
