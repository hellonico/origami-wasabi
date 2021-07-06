plugins {
    kotlin("jvm") version "1.5.20"
    kotlin("plugin.serialization") version "1.5.20"
    application
}

repositories {
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

    implementation("io.ktor:ktor-server-netty:$project.ktorVersion")
    implementation("io.ktor:ktor-serialization:$project.ktorVersion")
    implementation("io.ktor:ktor-websockets:$project.ktorVersion")
    implementation("io.ktor:ktor-html-builder:$project.ktorVersion")

    implementation("com.h2database:h2:$project.h2Version")
    implementation("org.jetbrains.exposed:exposed-core:$project.exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$project.exposedVersion")
    implementation("com.zaxxer:HikariCP:$project.hikariCpVersion")
    implementation("org.flywaydb:flyway-core:$project.flywayVersion")
    implementation("ch.qos.logback:logback-classic:$project.logbackVersion")
    implementation("origami:filters:1.30")
    implementation("origami:origami:4.5.1-5")

    testImplementation("org.assertj:assertj-core:$project.assertjVersion")
    testImplementation("io.rest-assured:rest-assured:$project.restAssuredVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$project.junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$project.junitVersion")
    testImplementation("io.ktor:ktor-client-cio:$project.ktorVersion")
}

application {
    mainClass.set("MainKt")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
