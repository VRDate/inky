package ink.java.mica

internal class Tag(
    content: String,
    parent: Container?,
    lineNumber: Int
) : Content(
    if (parent == null) content else Content.getId(parent),
    content,
    parent,
    lineNumber
)
