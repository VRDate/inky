package ink.kt

/**
 * Common ink runtime interface — the contract that any ink runtime must fulfill.
 *
 * Both [ink.kt.Story] (compiled JSON runtime, blade-ink port) and
 * [ink.kt.mica.Story] (parser-based runtime) can implement this interface,
 * enabling conformance testing via ink-proof and runtime-agnostic consumers.
 *
 * This interface mirrors the core API surface shared across all ink ports:
 * - C# (inkle/ink)
 * - Java (bladecoder/blade-ink)
 * - JS/TS (y-lohse/inkjs)
 * - Kotlin (ink.kt)
 *
 * @see <a href="https://github.com/chromy/ink-proof">ink-proof conformance tests</a>
 */
interface InkRuntime {

    // ── Core narrative loop ─────────────────────────────────────────

    /** Returns true if more content is available to continue. */
    fun canContinue(): Boolean

    /** Advance one line and return the text output. */
    fun continueStory(): String

    /** Advance until a choice point or story end, returning all accumulated text. */
    fun continueMaximally(): String

    // ── Output ──────────────────────────────────────────────────────

    /** The latest text line from [continueStory]. */
    val currentText: String

    /** Choices available at the current point (invisible defaults filtered). */
    val currentChoices: List<InkChoice>

    /** Tags from the latest [continueStory] call. */
    val currentTags: List<String>

    // ── Choice selection ────────────────────────────────────────────

    /** Select a choice by its index in [currentChoices]. */
    fun chooseChoiceIndex(choiceIdx: Int)

    // ── Error state ─────────────────────────────────────────────────

    /** True if errors occurred during the last continue. */
    val hasError: Boolean

    /** True if warnings occurred during the last continue. */
    val hasWarning: Boolean

    /** Current errors (null if none). */
    val currentErrors: List<String>?

    /** Current warnings (null if none). */
    val currentWarnings: List<String>?

    // ── Tags ────────────────────────────────────────────────────────

    /** Tags at the very top of the story. */
    fun getGlobalTags(): List<String>?

    /** Tags for a specific knot or knot.stitch path. */
    fun tagsForContentAtPath(path: String): List<String>?

    // ── Variable access ─────────────────────────────────────────────

    /** Get a global variable value by name. */
    fun getVariable(name: String): Any?

    /** Set a global variable value by name. */
    fun setVariable(name: String, value: Any?)

    // ── State ───────────────────────────────────────────────────────

    /** True when the story has reached an END or DONE. */
    val isEnded: Boolean
}

/**
 * A single choice presented to the player.
 * Abstracts over [ink.kt.Choice] and [ink.kt.mica.Choice].
 */
interface InkChoice {
    /** The display text for this choice. */
    val text: String
    /** The index of this choice in the current choices list. */
    val index: Int
}
