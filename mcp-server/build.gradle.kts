plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "ink.mcp"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.1"
val graalVersion = "25.0.2"

dependencies {
    // Ktor 3.x server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // GraalJS engine (community edition)
    implementation("org.graalvm.polyglot:polyglot:$graalVersion")
    implementation("org.graalvm.polyglot:js-community:$graalVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

sourceSets {
    main {
        kotlin.srcDirs("src")
    }
}

application {
    mainClass.set("ink.mcp.MainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "ink.mcp.MainKt"
    }
}

// Fat JAR
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "ink.mcp.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
}

kotlin {
    jvmToolchain(21)
}
