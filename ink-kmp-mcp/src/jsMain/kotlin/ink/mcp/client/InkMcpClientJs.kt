@file:OptIn(DelicateCoroutinesApi::class)

package ink.mcp.client

import ink.mcp.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.json.*
import kotlin.js.Promise

/**
 * JS-exported wrapper around [InkMcpClient].
 *
 * Bridges Kotlin suspend functions to JS Promises so React/Node.js can call:
 * ```js
 * import { InkMcpClient } from 'ink-kmp-mcp';
 * const client = new InkMcpClient("http://localhost:3001");
 * const result = await client.compileInk("Hello -> END");
 * ```
 *
 * Methods returning dynamic JSON (mapToJson) return raw JSON strings — parse with JSON.parse() in JS.
 */
@JsExport
@JsName("InkMcpClient")
class InkMcpClientJs(serverUrl: String = "http://localhost:3001") {
    private val client = InkMcpClient(serverUrl)

    // ── Ink (17) ─────────────────────────────────────────────────────

    fun compileInk(source: String): Promise<CompileInkResponse> =
        GlobalScope.promise { client.compileInk(source) }

    fun startStory(source: String, sessionId: String?): Promise<ContinueResponse> =
        GlobalScope.promise { client.startStory(source, sessionId) }

    fun startStoryJson(compiledJson: String, sessionId: String?): Promise<ContinueResponse> =
        GlobalScope.promise { client.startStoryJson(compiledJson, sessionId) }

    fun choose(sessionId: String, choiceIndex: Int): Promise<ContinueResponse> =
        GlobalScope.promise { client.choose(sessionId, choiceIndex) }

    fun continueStory(sessionId: String): Promise<ContinueResponse> =
        GlobalScope.promise { client.continueStory(sessionId) }

    fun getVariable(sessionId: String, name: String): Promise<VariableResponse> =
        GlobalScope.promise { client.getVariable(sessionId, name) }

    fun saveState(sessionId: String): Promise<SaveStateResponse> =
        GlobalScope.promise { client.saveState(sessionId) }

    fun loadState(sessionId: String, stateJson: String): Promise<LoadStateResponse> =
        GlobalScope.promise { client.loadState(sessionId, stateJson) }

    fun resetStory(sessionId: String): Promise<ContinueResponse> =
        GlobalScope.promise { client.resetStory(sessionId) }

    fun getGlobalTags(sessionId: String): Promise<GlobalTagsResponse> =
        GlobalScope.promise { client.getGlobalTags(sessionId) }

    fun listSessions(): Promise<ListSessionsResponse> =
        GlobalScope.promise { client.listSessions() }

    fun endSession(sessionId: String): Promise<EndSessionResponse> =
        GlobalScope.promise { client.endSession(sessionId) }

    fun bidify(text: String): Promise<BidiResponse> =
        GlobalScope.promise { client.bidify(text) }

    fun stripBidi(text: String): Promise<BidiResponse> =
        GlobalScope.promise { client.stripBidi(text) }

    fun bidifyJson(jsonStr: String): Promise<BidiResponse> =
        GlobalScope.promise { client.bidifyJson(jsonStr) }

    // ── Debug (8) ────────────────────────────────────────────────────

    /** Returns raw JSON string — parse with JSON.parse() in JS. */
    fun startDebug(sessionId: String): Promise<String> =
        GlobalScope.promise { client.startDebug(sessionId).toString() }

    fun addBreakpoint(sessionId: String, type: String, target: String): Promise<BreakpointResponse> =
        GlobalScope.promise { client.addBreakpoint(sessionId, type, target) }

    fun removeBreakpoint(sessionId: String, breakpointId: String): Promise<RemoveBreakpointResponse> =
        GlobalScope.promise { client.removeBreakpoint(sessionId, breakpointId) }

    fun debugStep(sessionId: String): Promise<DebugStepResponse> =
        GlobalScope.promise { client.debugStep(sessionId) }

    fun debugContinue(sessionId: String, maxSteps: Int): Promise<DebugStepResponse> =
        GlobalScope.promise { client.debugContinue(sessionId, maxSteps) }

    fun addWatch(sessionId: String, variable: String): Promise<AddWatchResponse> =
        GlobalScope.promise { client.addWatch(sessionId, variable) }

    /** Returns raw JSON string — parse with JSON.parse() in JS. */
    fun debugInspect(sessionId: String): Promise<String> =
        GlobalScope.promise { client.debugInspect(sessionId).toString() }

    /** Returns raw JSON string — parse with JSON.parse() in JS. */
    fun debugTrace(sessionId: String, lastN: Int): Promise<String> =
        GlobalScope.promise { client.debugTrace(sessionId, lastN).toString() }

    // ── Edit (6) ─────────────────────────────────────────────────────

    fun parseInk(source: String): Promise<InkStructureResponse> =
        GlobalScope.promise { client.parseInk(source) }

    fun getSection(source: String, sectionName: String): Promise<SectionInfo> =
        GlobalScope.promise { client.getSection(source, sectionName) }

    fun replaceSection(source: String, sectionName: String, newContent: String): Promise<EditResponse> =
        GlobalScope.promise { client.replaceSection(source, sectionName, newContent) }

    fun insertSection(source: String, afterSection: String, newContent: String): Promise<EditResponse> =
        GlobalScope.promise { client.insertSection(source, afterSection, newContent) }

    fun renameSection(source: String, oldName: String, newName: String): Promise<EditResponse> =
        GlobalScope.promise { client.renameSection(source, oldName, newName) }

    /** Returns raw JSON string — parse with JSON.parse() in JS. */
    fun inkStats(source: String): Promise<String> =
        GlobalScope.promise { client.inkStats(source).toString() }

    // ── Ink+MD (3) ───────────────────────────────────────────────────

    fun parseInkMd(markdown: String): Promise<ParseInkMdResponse> =
        GlobalScope.promise { client.parseInkMd(markdown) }

    fun renderInkMd(markdown: String): Promise<RenderInkMdResponse> =
        GlobalScope.promise { client.renderInkMd(markdown) }

    fun compileInkMd(markdown: String): Promise<CompileInkMdResponse> =
        GlobalScope.promise { client.compileInkMd(markdown) }

    // ── PlantUML (5) ─────────────────────────────────────────────────

    fun ink2puml(source: String, mode: String, title: String): Promise<PumlResponse> =
        GlobalScope.promise { client.ink2puml(source, mode, title) }

    fun ink2svg(source: String, mode: String, title: String): Promise<SvgResponse> =
        GlobalScope.promise { client.ink2svg(source, mode, title) }

    fun puml2svg(puml: String): Promise<SvgResponse> =
        GlobalScope.promise { client.puml2svg(puml) }

    fun inkToc(source: String): Promise<TocResponse> =
        GlobalScope.promise { client.inkToc(source) }

    fun inkTocPuml(source: String, title: String): Promise<TocPumlResponse> =
        GlobalScope.promise { client.inkTocPuml(source, title) }

    // ── LLM (8) ──────────────────────────────────────────────────────

    fun listModels(vramGb: Int?): Promise<ListModelsResponse> =
        GlobalScope.promise { client.listModels(vramGb) }

    fun loadModel(modelId: String?, customRepo: String?): Promise<LoadModelResponse> =
        GlobalScope.promise { client.loadModel(modelId, customRepo) }

    /** Returns raw JSON string — parse with JSON.parse() in JS. */
    fun modelInfo(): Promise<String> =
        GlobalScope.promise { client.modelInfo().toString() }

    fun chat(message: String): Promise<String> =
        GlobalScope.promise { client.chat(message) }

    fun generateInk(prompt: String): Promise<GenerateInkResponse> =
        GlobalScope.promise { client.generateInk(prompt) }

    fun reviewInk(source: String): Promise<ReviewInkResponse> =
        GlobalScope.promise { client.reviewInk(source) }

    fun translateToHebrew(source: String): Promise<TranslateInkResponse> =
        GlobalScope.promise { client.translateToHebrew(source) }

    fun generateCompilePlay(prompt: String): Promise<GenerateCompilePlayResponse> =
        GlobalScope.promise { client.generateCompilePlay(prompt) }

    // ── Services (2) ─────────────────────────────────────────────────

    fun listServices(): Promise<ListServicesResponse> =
        GlobalScope.promise { client.listServices() }

    fun connectService(serviceId: String, apiKey: String?, model: String?): Promise<ConnectServiceResponse> =
        GlobalScope.promise { client.connectService(serviceId, apiKey, model) }

    // ── Health ───────────────────────────────────────────────────────

    fun isAvailable(): Promise<Boolean> =
        GlobalScope.promise { client.isAvailable() }

    fun close() = client.close()
}
