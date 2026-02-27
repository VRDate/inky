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

// ── ink-electron (Electron app — Mocha + Playwright) ─────────

tasks.register<Exec>("npmInstallApp") {
    description = "Install npm dependencies for ink-electron/"
    group = "setup"
    workingDir = file("ink-electron")
    commandLine(npmCmd, "install", "--ignore-scripts")
    inputs.file("ink-electron/package.json")
    outputs.dir("ink-electron/node_modules")
}

tasks.register<Exec>("testJavaScript") {
    description = "Run Mocha tests in ink-electron/ (Electron JS)"
    group = "verification"
    dependsOn("npmInstallApp")
    workingDir = file("ink-electron")
    commandLine(npmCmd, "run", "test:unit")
}

tasks.register<Exec>("testJavaScriptUnit") {
    description = "Run only unit tests in ink-electron/ (no Electron/Playwright)"
    group = "verification"
    dependsOn("npmInstallApp")
    workingDir = file("ink-electron")
    commandLine(npmCmd, "run", "test:unit")
}

// ── ink-js/inkey (TypeScript — npm workspaces + node --test) ──

tasks.register<Exec>("npmInstallInkEditor") {
    description = "Install npm dependencies for ink-js/inkey/"
    group = "setup"
    workingDir = file("ink-js/inkey")
    commandLine(npmCmd, "install")
    inputs.file("ink-js/inkey/package.json")
    outputs.dir("ink-js/inkey/node_modules")
}

tasks.register<Exec>("testTypeScript") {
    description = "Run node --test across ink-js/inkey workspaces"
    group = "verification"
    dependsOn("npmInstallInkEditor")
    workingDir = file("ink-js/inkey")
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

// ── ink-kmp-mcp (Kotlin Multiplatform MCP server) ────────────

tasks.register("testKotlin") {
    description = "Run Kotlin tests in ink-kmp-mcp/"
    group = "verification"
    dependsOn("npmInstallApp")  // BidiTddInkTest.kt needs ink-electron/node_modules/inkjs
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
    dependsOn("npmInstallApp", "npmInstallInkEditor", "dotnetRestore")
}

tasks.register("compileAll") {
    description = "Compile all ecosystems"
    group = "build"
    dependsOn(":ink-kmp-mcp:compileKotlin", "compileCSharp")
}

tasks.register("testAll") {
    description = "Run all tests across all 4 ecosystems (KT 145 + C# 42 + TS 48 + JS 64 = 299)"
    group = "verification"
    dependsOn("testKotlin", "testJavaScript", "testTypeScript", "testCSharp", "testCSharpBidiTdd")
}
