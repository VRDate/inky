plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    application
}

group = "ink.mcp"
version = "0.3.0"

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
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")

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

    // PlantUML (MIT license) for diagram rendering
    implementation("net.sourceforge.plantuml:plantuml-mit:1.2024.8")

    // iCal4j for calendar/event management
    implementation("org.mnode.ical4j:ical4j:4.0.7")

    // ez-vcard for vCard principal management
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1")

    // Sardine WebDAV client
    implementation("com.github.lookfirst:sardine:5.12")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.15")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.0")

    // WireMock — HTTP/WebSocket mock server for HocusPocus Yjs event monitoring
    testImplementation("org.wiremock:wiremock:3.10.0")

    // Flexmark — CommonMark markdown parser for testing md+ink integration
    testImplementation("com.vladsch.flexmark:flexmark-all:0.64.8")
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
        kotlin.exclude("test/**")
        resources.exclude("test/**")
    }
    test {
        kotlin.srcDirs("src/test/kotlin")
        resources.srcDirs("src/test/resources")
    }
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

// ── PlantUML → SVG build task ──────────────────────────────────────
// Converts all .puml files in docs/architecture/ to .svg
tasks.register("plantUml") {
    description = "Convert PlantUML diagrams to SVG"
    group = "documentation"

    val pumlDir = file("${rootProject.projectDir}/docs/architecture")
    val svgDir = file("${rootProject.projectDir}/docs/architecture/svg")

    inputs.dir(pumlDir)
    outputs.dir(svgDir)

    doLast {
        svgDir.mkdirs()
        pumlDir.listFiles()?.filter { it.extension == "puml" }?.forEach { pumlFile ->
            val svgFile = File(svgDir, pumlFile.nameWithoutExtension + ".svg")
            logger.lifecycle("PlantUML: ${pumlFile.name} -> svg/${svgFile.name}")

            try {
                val readerClass = Class.forName("net.sourceforge.plantuml.SourceStringReader")
                val fileFormatClass = Class.forName("net.sourceforge.plantuml.FileFormat")
                val fileFormatOptionClass = Class.forName("net.sourceforge.plantuml.FileFormatOption")

                val svgFormat = fileFormatClass.getField("SVG").get(null)
                val formatOption = fileFormatOptionClass.getConstructor(fileFormatClass).newInstance(svgFormat)

                val reader = readerClass.getConstructor(String::class.java).newInstance(pumlFile.readText())
                val fosClass = Class.forName("java.io.FileOutputStream")
                val osClass = Class.forName("java.io.OutputStream")
                val fos = fosClass.getConstructor(File::class.java).newInstance(svgFile)
                val outputMethod = readerClass.getMethod("outputImage", osClass, fileFormatOptionClass)
                outputMethod.invoke(reader, fos, formatOption)
                fosClass.getMethod("close").invoke(fos)
            } catch (e: Exception) {
                logger.warn("Failed to render ${pumlFile.name}: ${e.message}")
            }
        }
    }
}
