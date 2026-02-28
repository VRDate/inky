package ink.java.mica

import ink.java.mica.util.InkLoadingException
import kotlinx.serialization.json.*

/**
 * Loads story state from a JsonObject (kotlinx.serialization).
 * KMP-compatible: replaces Jackson JsonParser.
 */
object StoryLoader {

    fun load(json: JsonObject, story: Story) {
        json[StoryJson.CONTENT]?.jsonObject?.let { contentObj ->
            for ((cid, cValue) in contentObj) {
                val content = story.content[cid] ?: continue
                val cObj = cValue.jsonObject
                cObj[StoryJson.COUNT]?.jsonPrimitive?.intOrNull?.let { content.count = it }
                if (content is Container) {
                    cObj[StoryJson.INDEX]?.jsonPrimitive?.intOrNull?.let { content.index = it }
                }
                cObj[StoryJson.VARIABLES]?.jsonObject?.let { varsObj ->
                    val pContainer = content as? ParameterizedContainer ?: return@let
                    for ((varName, varValue) in varsObj) {
                        val obj = loadValue(varValue, story)
                        if (obj != null) pContainer.values[varName] = obj
                    }
                }
            }
        }

        json[StoryJson.CONTAINER]?.jsonPrimitive?.content?.let { containerId ->
            story.container = story.content[containerId] as Container
        }

        json[StoryJson.TEXT]?.jsonArray?.let { textArr ->
            for (elem in textArr) {
                story.text.add(elem.jsonPrimitive.content)
            }
        }

        json[StoryJson.CHOICES]?.jsonArray?.let { choicesArr ->
            for (elem in choicesArr) {
                val choiceId = elem.jsonPrimitive.content
                val cnt = story.content[choiceId]
                if (cnt is Choice)
                    story.choices.add(cnt)
                else
                    story.wrapper.logException(InkLoadingException("$choiceId is not a choice"))
            }
        }

        json[StoryJson.VARIABLES]?.jsonObject?.let { varsObj ->
            for ((varName, varValue) in varsObj) {
                val obj = loadValue(varValue, story)
                if (obj != null) story.variables[varName] = obj
            }
        }
    }

    private fun loadValue(element: JsonElement, story: Story): Any? {
        if (element is JsonNull) return null
        val prim = element.jsonPrimitive
        if (prim.booleanOrNull != null) return prim.boolean
        if (prim.doubleOrNull != null) return prim.double
        val str = prim.content
        return story.wrapper.getStoryObject(str)
    }
}
