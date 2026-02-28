package ink.kt

/**
 * A generated Choice from the story. A single ChoicePoint in the Story
 * could potentially generate different Choices dynamically, so they're separated.
 *
 * All three implementations (C#, Java, JS) are structurally identical.
 *
 * Kotlin improvements:
 * - Comparable<Choice> for sorted collections (ordered by index)
 * - equals/hashCode based on index for LinkedHashSet<Choice> membership
 * - Story exposes Flow<LinkedHashSet<Choice>> for reactive choice event streaming
 *   (LinkedHashSet preserves insertion order + O(1) lookup + deduplication)
 */
class Choice() : Container(), Comparable<Choice> {

    override var text: String = ""
    var targetPath: Path? = null
    var sourcePath: String? = null
    var isInvisibleDefault: Boolean = false
    var originalThreadIndex: Int = 0
    var tags: List<String>? = null

    // ── Parser fields (from mica Choice — unused in compiled mode) ─────
    var level: Int = 0
    internal var conditions: MutableList<String> = mutableListOf()
    internal var repeatable: Boolean = false

    // C#: Thread property, Java: getter/setter, JS: untyped field
    // Kotlin: properly typed now that CallStack is ported
    var threadAtGeneration: CallStack.Thread? = null

    var pathStringOnChoice: String?
        get() = targetPath?.toString()
        set(value) { targetPath = if (value != null) Path(value) else null }

    // ── Parser constructor ────────────────────────────────────────────

    internal constructor(
        textBase: String,
        level: Int,
        parent: Container,
        lineNumber: Int
    ) : this() {
        this.level = level
        this.id = getId(textBase, parent)
        this.text = extractChoiceText(textBase)
        this.parent = parent
        this.lineNumber = lineNumber
        this.repeatable = (textBase[0] == Symbol.CHOICE_PLUS)

        val notation = textBase[0]
        var header = textBase
        while (header[0] == notation)
            header = header.substring(1).trim()
        if (header.startsWith(Symbol.BRACE_LEFT)) {
            header = header.substring(header.indexOf(Symbol.BRACE_RIGHT) + 1).trim()
        }
        while (header.startsWith(Symbol.CBRACE_LEFT)) {
            conditions.add(
                header.substring(
                    header.indexOf(Symbol.CBRACE_LEFT) + 1,
                    header.indexOf(Symbol.CBRACE_RIGHT)
                ).trim()
            )
            header = header.substring(header.indexOf(Symbol.CBRACE_RIGHT) + 1).trim()
        }
        val result = if (header.contains("]"))
            header.replace("\\[.*\\]".toRegex(), "").trim()
        else
            header
        if (result.isNotEmpty()) {
            if (result.contains(Symbol.DIVERT))
                InkParser.parseDivert(lineNumber, result, this)
            else
                children.add(InkObject().apply {
                    this.id = "${this@Choice.id}${Symbol.DOT}${this@Choice.size}"
                    this.text = result
                    this.parent = this@Choice
                    this.lineNumber = lineNumber
                })
        }
    }

    // ── Parser methods ────────────────────────────────────────────────

    fun isFallBack(): Boolean = text.isEmpty()

    fun evaluateConditions(story: VariableMap): Boolean {
        if (count > 0 && !repeatable) return false
        for (condition in conditions) {
            try {
                val obj = Declaration.evaluate(condition, story)
                if (obj is Boolean && !obj) return false
                if (obj is Double && obj.toInt() <= 0) return false
            } catch (e: InkRunTimeException) {
                story.logException(e)
                return false
            }
        }
        return true
    }

    // ── Runtime methods ─────────────────────────────────────────────────

    /** Natural ordering by index — enables sorted set/list usage. */
    override fun compareTo(other: Choice): Int = index.compareTo(other.index)

    /** Identity by index — for LinkedHashSet deduplication. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Choice) return false
        return index == other.index
    }

    override fun hashCode(): Int = index

    fun clone(): Choice = Choice().also { copy ->
        copy.text = text
        copy.sourcePath = sourcePath
        copy.index = index
        copy.targetPath = targetPath
        copy.originalThreadIndex = originalThreadIndex
        copy.isInvisibleDefault = isInvisibleDefault
        copy.tags = tags
        copy.threadAtGeneration = threadAtGeneration?.copy()
        copy.level = level
        copy.conditions = conditions.toMutableList()
        copy.repeatable = repeatable
    }

    companion object {
        fun getChoiceDepth(line: String): Int {
            val notation = line[0]
            var lvl = 0
            var s = line.substring(1).trim()
            while (s[0] == notation) {
                lvl++
                s = s.substring(1).trim()
            }
            return lvl
        }

        fun getId(header: String, parent: Container): String {
            val notation = header[0]
            var id = header
            while (id.startsWith(notation))
                id = id.substring(1).trim()
            if (id.startsWith(Symbol.BRACE_LEFT)) {
                id = id.substring(
                    id.indexOf(Symbol.BRACE_LEFT) + 1,
                    id.indexOf(Symbol.BRACE_RIGHT)
                ).trim()
                return "${parent.id}${Symbol.DOT}$id"
            }
            return "${parent.id}${Symbol.DOT}${parent.size}"
        }

        fun extractChoiceText(header: String): String {
            val notation = header[0]
            var text = header
            while (text[0] == notation)
                text = text.substring(1).trim()
            if (text.startsWith(Symbol.BRACE_LEFT)) {
                text = text.substring(text.indexOf(Symbol.BRACE_RIGHT) + 1).trim()
            }
            while (text.startsWith(Symbol.CBRACE_LEFT)) {
                text = text.substring(text.indexOf(Symbol.CBRACE_RIGHT) + 1).trim()
            }
            if (text.contains("]"))
                text = text.substring(0, text.indexOf(Symbol.SBRACE_RIGHT))
                    .replace(Symbol.SBRACE_LEFT.toString(), "").trim()
            return text
        }

        fun getParent(currentContainer: Container, lvl: Int): Container {
            var ret = currentContainer
            while (true) {
                if ((ret is Choice && lvl <= ret.level) || (ret is Gather && lvl <= ret.level))
                    ret = ret.parent!!
                else
                    return ret
            }
        }
    }
}
