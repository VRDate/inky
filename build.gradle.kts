// ═══════════════════════════════════════════════════════════════
// Inky Monorepo — Root Gradle 9 Build
// Compare & integrate ink across frameworks: KMP · Electron · JS · C# · Unity
// Orchestrates: Kotlin (Gradle) · JS (npm/Mocha) · TS (npm/node --test) · C# (dotnet/xUnit)
// ═══════════════════════════════════════════════════════════════

val npmCmd = "npm"
val dotnetCmd = providers.provider {
    val home = System.getProperty("user.home")
    val dotnetPath = "$home/.dotnet/dotnet"
    if (file(dotnetPath).exists()) dotnetPath else "dotnet"
}.get()

// ── ink/js/[component] — JS ecosystem ──────────────────────

val inkJsDir = "ink-kmp-mcp/src/jsMain/ink/js"
val inkJsCommonDir = "$inkJsDir/common"
val inkJsElectronDir = "$inkJsDir/electron"
val inkJsPwaDir = "$inkJsDir/pwa"
val inkJsAiDir = "$inkJsDir/ai"

// ── ink/js/common — Shared JS/TS utilities (@inky/common) ──

tasks.register<Exec>("npmInstallCommon") {
    description = "Install npm dependencies for ink/js/common (@inky/common)"
    group = "setup"
    workingDir = file(inkJsCommonDir)
    commandLine(npmCmd, "install")
    inputs.file("$inkJsCommonDir/package.json")
    outputs.dir("$inkJsCommonDir/node_modules")
}

tasks.register<Exec>("buildCommon") {
    description = "Build @inky/common (TypeScript → CJS + ESM)"
    group = "build"
    dependsOn("npmInstallCommon")
    workingDir = file(inkJsCommonDir)
    commandLine(npmCmd, "run", "build")
}

// ── ink/js/electron — Desktop Electron app ── Mocha + Playwright ──

tasks.register<Exec>("npmInstallApp") {
    description = "Install npm dependencies for ink/js/electron"
    group = "setup"
    workingDir = file(inkJsElectronDir)
    commandLine(npmCmd, "install", "--ignore-scripts")
    inputs.file("$inkJsElectronDir/package.json")
    outputs.dir("$inkJsElectronDir/node_modules")
}

tasks.register<Exec>("testJavaScript") {
    description = "Run Mocha tests in ink/js/electron"
    group = "verification"
    dependsOn("npmInstallApp")
    workingDir = file(inkJsElectronDir)
    commandLine(npmCmd, "run", "test:unit")
}

tasks.register<Exec>("testJavaScriptUnit") {
    description = "Run only unit tests in ink/js/electron (no Electron/Playwright)"
    group = "verification"
    dependsOn("npmInstallApp")
    workingDir = file(inkJsElectronDir)
    commandLine(npmCmd, "run", "test:unit")
}

// ── ink/js/editor (TypeScript — npm workspaces + node --test) ──

val inkJsEditorDir = "$inkJsDir/editor"

tasks.register<Exec>("npmInstallInkEditor") {
    description = "Install npm dependencies for ink/js/editor (CodeMirror + Remirror + React)"
    group = "setup"
    workingDir = file(inkJsEditorDir)
    commandLine(npmCmd, "install")
    inputs.file("$inkJsEditorDir/package.json")
    outputs.dir("$inkJsEditorDir/node_modules")
}

tasks.register<Exec>("testTypeScript") {
    description = "Run node --test across ink/js/editor workspaces"
    group = "verification"
    dependsOn("npmInstallInkEditor")
    workingDir = file(inkJsEditorDir)
    commandLine(npmCmd, "test")
}

// ── C# (InkMdTable.Tests + InkBidiTdd.Tests — dotnet + xUnit) ──

tasks.register<Exec>("dotnetRestore") {
    description = "Restore NuGet packages for C# test projects"
    group = "setup"
    workingDir = file("InkMdTable.Tests")
    commandLine(dotnetCmd, "restore")
    inputs.file("InkMdTable.Tests/InkMdTable.Tests.csproj")
    outputs.dir("InkMdTable.Tests/obj")
}

tasks.register<Exec>("dotnetRestoreBidiTdd") {
    description = "Restore NuGet packages for InkBidiTdd.Tests"
    group = "setup"
    workingDir = file("InkBidiTdd.Tests")
    commandLine(dotnetCmd, "restore")
    inputs.file("InkBidiTdd.Tests/InkBidiTdd.Tests.csproj")
    outputs.dir("InkBidiTdd.Tests/obj")
}

tasks.register<Exec>("compileCSharp") {
    description = "Build all C# test projects"
    group = "build"
    dependsOn("dotnetRestore", "dotnetRestoreBidiTdd")
    workingDir = file("InkBidiTdd.Tests")
    commandLine(dotnetCmd, "build", "--no-restore")
}

tasks.register<Exec>("testCSharp") {
    description = "Run xUnit tests in InkMdTable.Tests/"
    group = "verification"
    dependsOn("dotnetRestore")
    workingDir = file("InkMdTable.Tests")
    commandLine(dotnetCmd, "test", "--no-restore", "--logger", "console;verbosity=normal")
}

tasks.register<Exec>("testCSharpBidiTdd") {
    description = "Run xUnit tests in InkBidiTdd.Tests/ (ink compiler + runtime + OneJS bridge)"
    group = "verification"
    dependsOn("dotnetRestoreBidiTdd")
    workingDir = file("InkBidiTdd.Tests")
    commandLine(dotnetCmd, "test", "--no-restore", "--logger", "console;verbosity=normal")
}

// ── ink/cs — Common C# library + MAUI + Unity ──────────────

val inkCsDir = "ink-kmp-mcp/src/csMain/ink/cs"
val inkCsTestDir = "ink-kmp-mcp/src/csTest/ink/cs"

tasks.register<Exec>("dotnetRestoreInkCommon") {
    description = "Restore NuGet packages for Ink.Common"
    group = "setup"
    workingDir = file(inkCsDir)
    commandLine(dotnetCmd, "restore")
    inputs.file("$inkCsDir/Ink.Common.csproj")
}

tasks.register<Exec>("compileCSharpInkCommon") {
    description = "Build Ink.Common (shared C# library)"
    group = "build"
    dependsOn("dotnetRestoreInkCommon")
    workingDir = file(inkCsDir)
    commandLine(dotnetCmd, "build", "--no-restore")
}

tasks.register<Exec>("testCSharpInkCommon") {
    description = "Run xUnit tests for Ink.Common"
    group = "verification"
    workingDir = file(inkCsTestDir)
    commandLine(dotnetCmd, "test", "--logger", "console;verbosity=normal")
}

// ── ink/js/pwa — Browser extension (Chrome/Edge/Firefox/Kiwi) ──

// No npm install needed — pure JS extension (no node_modules)
// Build = copy manifest-{browser}.json → manifest.json + zip

tasks.register<Copy>("buildPwaChrome") {
    description = "Package ink/js/pwa for Chrome (copy manifest-chrome.json → manifest.json)"
    group = "build"
    from(inkJsPwaDir) {
        exclude("manifest-*.json")
    }
    from("$inkJsPwaDir/manifest-chrome.json") {
        rename { "manifest.json" }
    }
    into(layout.buildDirectory.dir("pwa/chrome"))
}

tasks.register<Copy>("buildPwaFirefox") {
    description = "Package ink/js/pwa for Firefox (copy manifest-firefox.json → manifest.json)"
    group = "build"
    from(inkJsPwaDir) {
        exclude("manifest-*.json")
    }
    from("$inkJsPwaDir/manifest-firefox.json") {
        rename { "manifest.json" }
    }
    into(layout.buildDirectory.dir("pwa/firefox"))
}

tasks.register<Copy>("buildPwaEdge") {
    description = "Package ink/js/pwa for Edge (copy manifest-edge.json → manifest.json)"
    group = "build"
    from(inkJsPwaDir) {
        exclude("manifest-*.json")
    }
    from("$inkJsPwaDir/manifest-edge.json") {
        rename { "manifest.json" }
    }
    into(layout.buildDirectory.dir("pwa/edge"))
}

tasks.register<Copy>("buildPwaKiwi") {
    description = "Package ink/js/pwa for Kiwi (copy manifest-kiwi.json → manifest.json)"
    group = "build"
    from(inkJsPwaDir) {
        exclude("manifest-*.json")
    }
    from("$inkJsPwaDir/manifest-kiwi.json") {
        rename { "manifest.json" }
    }
    into(layout.buildDirectory.dir("pwa/kiwi"))
}

tasks.register("buildPwaAll") {
    description = "Package ink/js/pwa for all browsers"
    group = "build"
    dependsOn("buildPwaChrome", "buildPwaFirefox", "buildPwaEdge", "buildPwaKiwi")
}

// ── ink/js/ai — AI Assistant Electron app ──

tasks.register<Exec>("npmInstallAi") {
    description = "Install npm dependencies for ink/js/ai (AI Assistant)"
    group = "setup"
    workingDir = file(inkJsAiDir)
    commandLine(npmCmd, "install")
    inputs.file("$inkJsAiDir/package.json")
    outputs.dir("$inkJsAiDir/node_modules")
}

// ── ink-kmp-mcp (Kotlin Multiplatform MCP server) ────────────

tasks.register("testKotlin") {
    description = "Run Kotlin tests in ink-kmp-mcp/"
    group = "verification"
    //dependsOn("npmInstallApp")  // BidiTddInkTest.kt needs ink/js/electron/node_modules/inkjs
    dependsOn(":ink-kmp-mcp:test")
}

tasks.register("compileKotlin") {
    description = "Compile Kotlin sources in ink-kmp-mcp/"
    group = "build"
    dependsOn(":ink-kmp-mcp:compileKotlin")
}

// ── Aggregate tasks ──────────────────────────────────────────

tasks.register("installAll") {
    description = "Install all dependencies (npm + dotnet restore)"
    group = "setup"
    dependsOn("npmInstallCommon", "npmInstallApp", "npmInstallAi", "npmInstallInkEditor", "dotnetRestore", "dotnetRestoreInkCommon")
}

tasks.register("compileAll") {
    description = "Compile all ecosystems"
    group = "build"
    dependsOn(":ink-kmp-mcp:compileKotlin", "buildCommon", "compileCSharp", "compileCSharpInkCommon", "buildPwaAll")
}

tasks.register("testAll") {
    description = "Run all tests across all ecosystems"
    group = "verification"
    dependsOn("testKotlin", "testJavaScript", "testTypeScript", "testCSharp", "testCSharpBidiTdd", "testCSharpInkCommon")
}
