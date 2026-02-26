package ink.mcp

import org.apache.camel.CamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.slf4j.LoggerFactory

/**
 * Apache Camel routes integrating LangChain4j chat with Inky's ink engine.
 *
 * Routes:
 *   direct:llm-chat           → Send chat message to loaded LLM
 *   direct:llm-generate-ink   → Generate ink code from natural language
 *   direct:llm-review-ink     → Review ink code for issues
 *   direct:llm-translate-he   → Translate ink story text to Hebrew
 *   direct:ink-compile         → Compile ink source via InkEngine
 *   direct:ink-play            → Start interactive story session
 *   direct:ink-choose          → Make choice in story session
 *   direct:llm-compile-chain  → Generate ink → compile → play (full pipeline)
 */
class CamelRoutes(
    private val inkEngine: InkEngine,
    private val llmEngine: LlmEngine
) {
    private val log = LoggerFactory.getLogger(CamelRoutes::class.java)
    private var camelContext: CamelContext? = null

    /** Start the Camel context with all routes */
    fun start() {
        val ctx = DefaultCamelContext()

        // Register the LangChain4j ChatModel if loaded
        llmEngine.getChatModel()?.let { model ->
            ctx.registry.bind("chatModel", model)
            log.info("ChatModel registered in Camel registry")
        }

        // Register engines for processor access
        ctx.registry.bind("inkEngine", inkEngine)
        ctx.registry.bind("llmEngine", llmEngine)

        ctx.addRoutes(buildRoutes())
        ctx.start()

        camelContext = ctx
        log.info("Camel routes started ({} routes)", ctx.routes.size)
    }

    /** Stop Camel context */
    fun stop() {
        camelContext?.stop()
        camelContext = null
        log.info("Camel routes stopped")
    }

    /** Re-register ChatModel after model change */
    fun refreshChatModel() {
        camelContext?.let { ctx ->
            llmEngine.getChatModel()?.let { model ->
                ctx.registry.bind("chatModel", model)
                log.info("ChatModel refreshed in Camel registry")
            }
        }
    }

    /** Send a message to a direct route and get the result */
    fun sendToRoute(routeName: String, body: Any?, headers: Map<String, Any?> = emptyMap()): Any? {
        val ctx = camelContext ?: throw IllegalStateException("Camel not started")
        val producer = ctx.createProducerTemplate()
        return producer.requestBodyAndHeaders("direct:$routeName", body, headers)
    }

    private fun buildRoutes() = object : RouteBuilder() {
        override fun configure() {

            // ── LLM Chat Route ──
            from("direct:llm-chat")
                .routeId("llm-chat")
                .log("LLM chat: \${body}")
                .process { exchange ->
                    val message = exchange.getIn().getBody(String::class.java)
                    val llm = exchange.context.registry.lookupByName("llmEngine") as LlmEngine
                    val response = llm.chat(message)
                    exchange.getIn().body = response
                }
                .log("LLM response: \${body}")

            // ── LLM → Camel LangChain4j Chat (when chatModel is registered) ──
            from("direct:llm-langchain4j")
                .routeId("llm-langchain4j")
                .log("LangChain4j chat: \${body}")
                .choice()
                    .`when`().simple("\${bean:llmEngine?method=isLoaded}")
                        .to("langchain4j-chat:inky?chatModel=#chatModel")
                    .otherwise()
                        .setBody().constant("Error: No model loaded. Use load_model tool first.")
                .end()

            // ── Generate Ink Code ──
            from("direct:llm-generate-ink")
                .routeId("llm-generate-ink")
                .log("Generate ink from prompt: \${body}")
                .process { exchange ->
                    val prompt = exchange.getIn().getBody(String::class.java)
                    val llm = exchange.context.registry.lookupByName("llmEngine") as LlmEngine
                    val inkCode = llm.generateInk(prompt)
                    exchange.getIn().body = inkCode
                }
                .log("Generated ink code (${"\${body.length()}"} chars)")

            // ── Review Ink Code ──
            from("direct:llm-review-ink")
                .routeId("llm-review-ink")
                .log("Review ink code")
                .process { exchange ->
                    val inkSource = exchange.getIn().getBody(String::class.java)
                    val llm = exchange.context.registry.lookupByName("llmEngine") as LlmEngine
                    val review = llm.reviewInk(inkSource)
                    exchange.getIn().body = review
                }

            // ── Translate to Hebrew ──
            from("direct:llm-translate-he")
                .routeId("llm-translate-he")
                .log("Translate ink to Hebrew")
                .process { exchange ->
                    val inkSource = exchange.getIn().getBody(String::class.java)
                    val llm = exchange.context.registry.lookupByName("llmEngine") as LlmEngine
                    val translated = llm.translateToHebrew(inkSource)
                    exchange.getIn().body = translated
                }

            // ── Ink Compile Route ──
            from("direct:ink-compile")
                .routeId("ink-compile")
                .log("Compile ink source")
                .process { exchange ->
                    val source = exchange.getIn().getBody(String::class.java)
                    val ink = exchange.context.registry.lookupByName("inkEngine") as InkEngine
                    val result = ink.compile(source)
                    exchange.getIn().body = mapOf(
                        "success" to result.success,
                        "json" to result.json,
                        "errors" to result.errors,
                        "warnings" to result.warnings
                    )
                }

            // ── Ink Play (start session) Route ──
            from("direct:ink-play")
                .routeId("ink-play")
                .log("Start ink story session")
                .process { exchange ->
                    val source = exchange.getIn().getBody(String::class.java)
                    val ink = exchange.context.registry.lookupByName("inkEngine") as InkEngine
                    val (sessionId, result) = ink.startSession(source)
                    exchange.getIn().body = mapOf(
                        "session_id" to sessionId,
                        "text" to result.text,
                        "choices" to result.choices.map { mapOf("index" to it.index, "text" to it.text) },
                        "can_continue" to result.canContinue
                    )
                }

            // ── Ink Choose Route ──
            from("direct:ink-choose")
                .routeId("ink-choose")
                .log("Choose in ink story")
                .process { exchange ->
                    val sessionId = exchange.getIn().getHeader("sessionId", String::class.java)
                    val choiceIndex = exchange.getIn().getHeader("choiceIndex", Int::class.java)
                    val ink = exchange.context.registry.lookupByName("inkEngine") as InkEngine
                    val result = ink.choose(sessionId, choiceIndex)
                    exchange.getIn().body = mapOf(
                        "session_id" to sessionId,
                        "text" to result.text,
                        "choices" to result.choices.map { mapOf("index" to it.index, "text" to it.text) },
                        "can_continue" to result.canContinue
                    )
                }

            // ── Full Pipeline: Generate → Compile → Play ──
            from("direct:llm-compile-chain")
                .routeId("llm-compile-chain")
                .log("Full pipeline: prompt → ink → compile → play")
                .to("direct:llm-generate-ink")
                .setHeader("generatedInk").body()
                .to("direct:ink-compile")
                .choice()
                    .`when`().simple("\${body[success]} == true")
                        .setBody().simple("\${header.generatedInk}")
                        .to("direct:ink-play")
                    .otherwise()
                        .log("Compilation failed, returning errors")
                .end()
        }
    }
}
