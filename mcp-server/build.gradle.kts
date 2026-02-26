plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "ink.mcp"
version = "0.2.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.1"
val graalVersion = "25.0.2"
val camelVersion = "4.18.0"
val langchain4jVersion = "1.11.0"
val jlamaVersion = "0.8.4"

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

    // GraalJS engine (Oracle GraalVM)
    implementation("org.graalvm.polyglot:polyglot:$graalVersion")
    implementation("org.graalvm.polyglot:js:$graalVersion")

    // Apache Camel
    implementation("org.apache.camel:camel-core:$camelVersion")
    implementation("org.apache.camel:camel-main:$camelVersion")
    implementation("org.apache.camel:camel-direct:$camelVersion")
    implementation("org.apache.camel:camel-langchain4j-chat:$camelVersion")
    implementation("org.apache.camel:camel-langchain4j-tools:$camelVersion")
    implementation("org.apache.camel:camel-jackson:$camelVersion")

    // LangChain4j + JLama + OpenAI (for LM Studio)
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-jlama:$langchain4jVersion-beta19")
    implementation("dev.langchain4j:langchain4j-open-ai:$langchain4jVersion")

    // JLama core + native
    implementation("com.github.tjake:jlama-core:$jlamaVersion")
    implementation("com.github.tjake:jlama-native:$jlamaVersion") {
        artifact {
            classifier = osClassifier()
        }
    }

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
}

fun osClassifier(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osName = when {
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val archName = when {
        arch.contains("aarch64") || arch.contains("arm64") -> "aarch_64"
        else -> "x86_64"
    }
    return "$osName-$archName"
}

sourceSets {
    main {
        kotlin.srcDirs("src")
    }
}

application {
    mainClass.set("ink.mcp.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-modules", "jdk.incubator.vector",
        "--enable-preview"
    )
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
