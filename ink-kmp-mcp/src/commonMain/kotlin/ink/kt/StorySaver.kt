package ink.kt

import kotlinx.serialization.json.*

/**
 * Saves story state to a JsonObject (kotlinx.serialization).
 * KMP-compatible: replaces Jackson JsonGenerator.
 */
object StorySaver {

    fun save(story: Story): JsonObject = buildJsonObject {
        put(StoryJson.FILES, buildJsonArray {
            for (s in story.fileNames) add(s)
        })

        put(StoryJson.CONTENT, buildJsonObject {
            for ((_, c) in story.content) {
                if (c.count > 0) {
                    put(c.id, buildJsonObject {
                        put(StoryJson.COUNT, c.count)
                        if (c is Container && c.index > 0) {
                            put(StoryJson.INDEX, c.index)
                        }
                        if (c is ParameterizedContainer && c.values.isNotEmpty()) {
                            put(StoryJson.VARIABLES, buildJsonObject {
                                for ((key, value) in c.values) {
                                    putValue(this, story, key, value)
                                }
                            })
                        }
                    })
                }
            }
        })

        put(StoryJson.CONTAINER, story.container?.id ?: "")

        put(StoryJson.TEXT, buildJsonArray {
            for (s in story.text) add(s)
        })

        put(StoryJson.CHOICES, buildJsonArray {
            for (choice in story.choices) add(choice.id)
        })

        put(StoryJson.VARIABLES, buildJsonObject {
            for ((key, value) in story.variables) {
                putValue(this, story, key, value)
            }
        })
    }

    private fun putValue(builder: JsonObjectBuilder, story: Story, key: String, value: Any) {
        when (value) {
            is Boolean -> builder.put(key, value)
            is Double -> builder.put(key, value)
            is Int -> builder.put(key, value)
            is String -> builder.put(key, value)
            is InkObject -> builder.put(key, value.id)
            else -> story.wrapper?.logError(
                "StorySaver: Could not save $key: $value. Not Boolean, Number, String, or Content."
            )
        }
    }
}
