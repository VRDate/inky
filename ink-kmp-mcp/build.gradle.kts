plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    // Protobuf codegen: java plugin incompatible with KMP 2.3 — use pre-generated sources
    // or move to ink-model-proto submodule with java-library plugin
}

group = "ink.mcp"
version = "0.3.0"

repositories {
    mavenCentral()
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

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    jvm()  // MCP server + blade-ink + mica (Java compiled by default in Kotlin 2.3+ KMP)
    js(IR) {
        nodejs()  // ink-electron (Electron/Node.js UI — JS actual)
    }
    wasmJs {
        nodejs()  // ink.kt compiled to WASM — replaces legacy pure JS (inkjs)
    }
    // Future KMP targets:
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        // ── Common (ink.kt pure-Kotlin runtime — all targets) ────────
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            // Ktor HTTP client (MCP client for all platforms)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // ── JVM (MCP server, mica, blade-ink, GraalJS, Ktor) ────────
        jvmMain {
            // mica test specs live in jvmMain/kotlin/ink/java/mica/test/ — exclude from main
            kotlin.exclude("**/ink/java/mica/test/**")

            dependencies {
                // Ktor 3.x server
                implementation(libs.bundles.ktor.server)
                // Ktor CIO client engine (JVM)
                implementation(libs.ktor.client.cio)

                // GraalJS engine (Oracle GraalVM)
                implementation(libs.bundles.graal)

                // Apache Camel
                implementation(libs.bundles.camel)

                // LangChain4j + JLama + OpenAI
                implementation(libs.bundles.langchain4j.all)

                // JLama native
                implementation(libs.jlama.core)
                implementation("com.github.tjake:jlama-native:${libs.versions.jlama.get()}") {
                    artifact { classifier = osClassifier() }
                }

                // PlantUML (MIT) for diagram rendering
                implementation(libs.plantuml)

                // Calendar + vCard + WebDAV
                implementation(libs.ical4j)
                implementation(libs.ezvcard)
                implementation(libs.sardine)

                // Protocol Buffers — pre-generated ink.model contract
                implementation(libs.bundles.protobuf)

                // RSocket-Kotlin — event-driven transport
                implementation(libs.bundles.rsocket)

                // msgpack + Jackson serialization
                implementation(libs.bundles.jackson)

                // kotlin-faker — emoji category mapping
                implementation(libs.bundles.faker.all)

                // Apache POI — XLSX formula evaluation
                implementation(libs.poi.ooxml)

                // Logging
                implementation(libs.logback)
            }
        }
        jvmTest.dependencies {
            implementation(libs.ktor.server.test.host)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.wiremock)
            implementation(libs.flexmark)
        }

        // ── JS (Electron UI — jsActual) ──────────────────────────────
        jsMain.dependencies {
            implementation(libs.ktor.client.js)  // Ktor JS client engine
        }

        // ── WASM (ink.kt compiled to WASM — replaces legacy inkjs) ───
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)  // Ktor JS client engine (also works for WASM/JS)
        }
    }
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ── JVM run task (replaces application plugin) ───────────────────
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run the ink MCP server"
    mainClass.set("ink.mcp.MainKt")
    val jvmCompilation = kotlin.jvm().compilations["main"]
    classpath = files(
        jvmCompilation.output.allOutputs,
        configurations.named("jvmRuntimeClasspath")
    )
    jvmArgs("--add-modules", "jdk.incubator.vector", "--enable-preview")
}

// ── JVM JAR ──────────────────────────────────────────────────────
tasks.named<Jar>("jvmJar") {
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
    from(configurations.named("jvmRuntimeClasspath").get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.named<Jar>("jvmJar").get())
}

// ── JVM test configuration ───────────────────────────────────────
tasks.named<Test>("jvmTest") {
    dependsOn(":npmInstallApp")  // BidiTddInkTest.kt needs ink/js/electron/node_modules/inkjs
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    setForkEvery(50)
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    // GraalJS tests are slow (cold-start ~10s per context). ink.kt replaces GraalJS
    // so these are legacy parity tests only. 120s class-level @Timeout on @Tag("graaljs").
    // Run fast (no GraalJS): ./gradlew jvmTest -PexcludeGraalJs
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
    systemProperty("junit.jupiter.execution.timeout.mode", "disabled_on_debug")
    if (project.hasProperty("excludeGraalJs")) {
        useJUnitPlatform { excludeTags("graaljs") }
    }
}

// Fast test task — pure ink.kt + non-GraalJS engine tests only
tasks.register<Test>("testFast") {
    description = "Run tests excluding slow GraalJS engine tests"
    group = "verification"
    useJUnitPlatform { excludeTags("graaljs") }
    val jvmTestTask = tasks.named<Test>("jvmTest").get()
    classpath = jvmTestTask.classpath
    testClassesDirs = jvmTestTask.testClassesDirs
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    setForkEvery(50)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// ── PlantUML -> SVG build task ──────────────────────────────────────
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
