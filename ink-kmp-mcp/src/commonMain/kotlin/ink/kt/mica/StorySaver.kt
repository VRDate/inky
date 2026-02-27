package ink.kt.mica

import com.fasterxml.jackson.core.JsonGenerator
import java.math.BigDecimal

object StorySaver {

    fun saveStream(g: JsonGenerator, story: Story) {
        g.writeStartObject()
        g.writeFieldName(StoryJson.FILES)
        g.writeStartArray()
        for (s in story.fileNames) {
            g.writeString(s)
        }
        g.writeEndArray()
        g.writeFieldName(StoryJson.CONTENT)
        g.writeStartObject()
        for ((_, c) in story.content) {
            if (c.count > 0) {
                g.writeFieldName(c.id)
                g.writeStartObject()
                g.writeNumberField(StoryJson.COUNT, c.count)
                if (c is Container && c.index > 0) {
                    g.writeNumberField(StoryJson.INDEX, c.index)
                }
                if (c is ParameterizedContainer && c.values.isNotEmpty()) {
                    g.writeFieldName(StoryJson.VARIABLES)
                    g.writeStartObject()
                    for ((key, value) in c.values) {
                        saveObject(g, story, key, value)
                    }
                    g.writeEndObject()
                }
                g.writeEndObject()
            }
        }
        g.writeEndObject()
        g.writeStringField(StoryJson.CONTAINER, story.container.id)
        g.writeFieldName(StoryJson.TEXT)
        g.writeStartArray()
        for (s in story.text) {
            g.writeString(s)
        }
        g.writeEndArray()
        g.writeFieldName(StoryJson.CHOICES)
        g.writeStartArray()
        for (choice in story.choices) {
            g.writeString(choice.id)
        }
        g.writeEndArray()
        g.writeFieldName(StoryJson.VARIABLES)
        g.writeStartObject()
        for ((key, value) in story.variables) {
            saveObject(g, story, key, value)
        }
        g.writeEndObject()
        g.writeEndObject()
    }

    private fun saveObject(g: JsonGenerator, story: Story, key: String, value: Any) {
        when (value) {
            is Boolean -> {
                g.writeBooleanField(key, value)
            }
            is BigDecimal -> {
                g.writeNumberField(key, value)
            }
            is String -> {
                g.writeStringField(key, value)
            }
            else -> {
                // Try to get an id from the value (Content subclass)
                if (value is Content) {
                    g.writeStringField(key, value.id)
                } else {
                    story.wrapper.logError(
                        "StorySaver: Could not save $key: $value. " +
                        "Not Boolean, Number, String, or Content."
                    )
                }
            }
        }
    }
}
