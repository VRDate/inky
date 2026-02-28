package ink.mcp

/**
 * Common interfaces for MCP engine operations.
 *
 * JVM engines (InkEngine, LlmEngine, etc.) implement these interfaces,
 * allowing McpTools to be shared across JVM/JS/WASM targets.
 */

/** Ink compilation & story sessions (InkEngine on JVM) */
interface McpInkOps {
    data class CompileResult(
        val success: Boolean,
        val json: String? = null,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    )

    data class ContinueResult(
        val text: String,
        val canContinue: Boolean,
        val choices: List<ChoiceInfo> = emptyList(),
        val tags: List<String> = emptyList()
    )

    data class ChoiceInfo(
        val index: Int,
        val text: String,
        val tags: List<String> = emptyList()
    )

    fun compile(source: String): CompileResult
    fun startSession(source: String, sessionId: String? = null): Pair<String, ContinueResult>
    fun startSessionFromJson(json: String, sessionId: String? = null): Pair<String, ContinueResult>
    fun choose(sessionId: String, choiceIndex: Int): ContinueResult
    fun continueStory(sessionId: String): ContinueResult
    fun getVariable(sessionId: String, name: String): Any?
    fun setVariable(sessionId: String, name: String, value: Any?)
    fun saveState(sessionId: String): String
    fun loadState(sessionId: String, stateJson: String)
    fun resetStory(sessionId: String): ContinueResult
    fun evaluateFunction(sessionId: String, funcName: String, args: List<Any?> = emptyList()): Any?
    fun getGlobalTags(sessionId: String): List<String>
    fun listSessions(): List<String>
    fun endSession(sessionId: String)
    fun bidify(text: String): String
    fun stripBidi(text: String): String
    fun bidifyJson(json: String): String
}

/** Debugging (wraps a story session with breakpoints/watches) */
interface McpDebugOps {
    data class Breakpoint(
        val id: String,
        val type: String,
        val target: String,
        val enabled: Boolean = true
    )

    data class WatchInfo(val name: String, val lastValue: Any? = null)

    data class StepResult(
        val text: String,
        val canContinue: Boolean,
        val choices: List<McpInkOps.ChoiceInfo>,
        val tags: List<String>,
        val stepNumber: Int,
        val isPaused: Boolean,
        val hitBreakpoint: Breakpoint? = null,
        val watchChanges: Map<String, Pair<Any?, Any?>> = emptyMap()
    )

    fun startDebug(sessionId: String): Map<String, Any?>
    fun addBreakpoint(sessionId: String, type: String, target: String): Breakpoint
    fun removeBreakpoint(sessionId: String, breakpointId: String): Boolean
    fun step(sessionId: String): StepResult
    fun continueDebug(sessionId: String, maxSteps: Int = 100): StepResult
    fun addWatch(sessionId: String, varName: String): WatchInfo
    fun inspect(sessionId: String): Map<String, Any?>
    fun getTrace(sessionId: String, lastN: Int = 50): List<Map<String, Any?>>
}

/** PlantUML diagram generation + optional SVG rendering */
interface McpPumlOps {
    fun inkToPuml(source: String, mode: String = "activity", title: String = "Ink Story Flow"): String
    fun pumlToSvg(puml: String): String
    fun generateToc(source: String): String
    fun generateTocPuml(source: String, title: String = "Ink Story TOC"): String
}

/** LLM operations — abstracts local JLama + external ChatModel + Camel routing */
interface McpLlmOps {
    fun chat(message: String): String
    fun generateInk(prompt: String): String
    fun reviewInk(source: String): String
    fun translateToHebrew(source: String): String
    fun getModelInfo(): Map<String, Any?>
    fun loadModel(modelId: String): String
    fun loadCustomModel(repo: String): String
}

/** LLM service provider management */
interface McpServiceOps {
    fun listServices(): List<Map<String, Any>>
    /** @return map with keys: ok, service, model, message */
    fun connect(serviceId: String, apiKey: String?, model: String?, baseUrl: String?): Map<String, Any>
    /** Currently connected service ID, or null */
    val connectedServiceId: String?
}

/** Collaboration (Yjs) */
interface McpColabOps {
    fun listDocuments(): List<Map<String, Any>>
    val totalClients: Int
    fun getDocumentInfo(docId: String): Map<String, Any>?
}

/** Calendar (iCal4j) */
interface McpCalendarOps {
    fun createEvent(calId: String, summary: String, description: String?, dtStart: String, dtEnd: String?, category: String?): Map<String, Any>
    fun listEvents(calId: String, category: String? = null): List<Map<String, Any>>
    fun exportIcs(calId: String): String
    fun importIcs(calId: String, ics: String): Map<String, Any>
}

/** vCard principals */
interface McpVCardOps {
    fun createPrincipal(id: String, name: String, email: String?, role: String, isLlm: Boolean, folderPath: String?): Map<String, Any>
    fun listPrincipals(isLlm: Boolean? = null): List<Map<String, Any>>
    fun getPrincipal(id: String): Map<String, Any>?
    fun deletePrincipal(id: String): Map<String, Any>
}

/** Auth (JWT/OIDC) */
interface McpAuthOps {
    fun getAuthStatus(): Map<String, Any>
    fun createLlmCredential(modelName: String, host: String = "localhost", port: Int = 3001, jcard: String? = null): Map<String, Any>
}

/** WebDAV file operations */
interface McpWebDavOps {
    fun canAccess(principalId: String?, path: String, write: Boolean): Boolean
    fun isSharedPath(path: String): Boolean
    fun listFiles(path: String): List<Map<String, Any>>
    fun getFile(path: String): Map<String, Any>?
    fun putFile(path: String, content: String): Map<String, Any>
    fun deleteFile(path: String): Map<String, Any>
    fun mkDir(path: String): Map<String, Any>
    fun syncFromRemote(remoteUrl: String, localPath: String, username: String?, password: String?): Map<String, Any>
    fun createBackupSet(scriptPath: String): Map<String, Any>
    fun listBackups(path: String): List<Map<String, Any>>
    fun restoreBackup(backupPath: String, masterPath: String): Map<String, Any>
    fun createWorkingCopy(originPath: String, modelId: String): Map<String, Any>
}

/** Asset pipeline (emoji manifest + faker + events) */
interface McpAssetOps {
    fun resolveEmoji(emoji: String): Map<String, Any>?
    fun parseAssetTags(tags: List<String>): List<Map<String, Any>>
    fun generateItems(seed: Long, count: Int, level: Int, categories: List<String>): Map<String, Any>
    fun generateCharacters(seed: Long, count: Int): Map<String, Any>
    fun generateStoryMd(seed: Long, level: Int, count: Int): String
    fun evaluateFormulas(tables: List<InkMdEngine.MdTable>): List<Map<String, Any>>
    fun listEmojiGroups(filter: String?): Map<String, Any>
    fun resolveUnicodeBlock(block: String): Map<String, Any>
    fun emitAssetEvent(sessionId: String, tags: List<String>, knot: String): Map<String, Any>
    fun listAssetEvents(sessionId: String?, channel: String?, limit: Int): Map<String, Any>
}
