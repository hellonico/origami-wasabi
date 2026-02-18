val ktorVersion = "1.6.1"
val exposedVersion="0.32.1"
val h2Version="1.4.200"
val hikariCpVersion="4.0.3"
val flywayVersion="7.11.0"
val logbackVersion="1.2.3"
val assertjVersion="3.20.2"
val restAssuredVersion="4.4.0"
val junitVersion="5.7.2"

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repository.hellonico.info/repository/hellonico/")
    }
    maven {
        url = uri("https://clojars.org/repo/")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-html-builder:$ktorVersion")

    implementation("com.h2database:h2:$h2Version")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariCpVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("origami:filters:1.49")
    implementation("origami:origami:4.13.0-2-SNAPSHOT")
    implementation("org.clojure:clojure:1.11.3")

    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("io.rest-assured:rest-assured:$restAssuredVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
}

application {
    mainClass.set("MainKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
