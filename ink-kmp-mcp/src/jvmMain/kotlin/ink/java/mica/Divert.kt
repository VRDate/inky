package ink.java.mica

import ink.java.mica.util.InkRunTimeException

internal class Divert(
    lineNumber: Int,
    text: String,
    parent: Container
) : Content(Content.getId(parent), text, parent, lineNumber) {

    fun resolveDivert(story: Story): Container {
        var d = text.trim()
        if (d.contains(Symbol.BRACE_LEFT))
            d = d.substring(0, d.indexOf(Symbol.BRACE_LEFT))
        d = story.resolveInterrupt(d)
        return story.getDivert(d)
    }
}
