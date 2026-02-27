package ink.kt.mica

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import ink.kt.mica.util.InkLoadingException
import java.math.BigDecimal

object StoryLoader {

    fun loadStream(p: JsonParser, story: Story) {
        while (p.nextToken() != JsonToken.END_OBJECT) {
            when (p.currentName) {
                StoryJson.CONTENT -> {
                    p.nextToken() // START_OBJECT
                    while (p.nextToken() != JsonToken.END_OBJECT) {
                        val cid = p.currentName
                        val content = story.content[cid]
                        p.nextToken() // START_OBJECT
                        while (p.nextToken() != JsonToken.END_OBJECT) {
                            when (p.currentName) {
                                StoryJson.COUNT -> content?.count = p.nextIntValue(0)
                                StoryJson.INDEX -> if (content is Container)
                                    content.index = p.nextIntValue(0)
                                StoryJson.VARIABLES -> {
                                    p.nextToken() // START_OBJECT
                                    val pContainer = content as? ParameterizedContainer
                                    while (p.nextToken() != JsonToken.END_OBJECT) {
                                        val varName = p.currentName
                                        val obj = loadObjectStream(p, story)
                                        if (obj != null && pContainer != null)
                                            pContainer.values[varName] = obj
                                    }
                                }
                                else -> { }
                            }
                        }
                    }
                }
                StoryJson.CONTAINER -> story.container = story.content[p.nextTextValue()] as Container
                StoryJson.TEXT -> {
                    p.nextToken() // START_ARRAY
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        story.text.add(p.text)
                    }
                }
                StoryJson.CHOICES -> {
                    p.nextToken() // START_ARRAY
                    while (p.nextToken() != JsonToken.END_ARRAY) {
                        val cnt = story.content[p.text]
                        if (cnt is Choice)
                            story.choices.add(cnt)
                        else
                            story.wrapper.logException(InkLoadingException("${p.text} is not a choice"))
                    }
                }
                StoryJson.VARIABLES -> {
                    p.nextToken() // START_OBJECT
                    while (p.nextToken() != JsonToken.END_OBJECT) {
                        val varName = p.currentName
                        val obj = loadObjectStream(p, story)
                        if (obj != null) {
                            story.variables[varName] = obj
                        }
                    }
                }
                else -> { }
            }
        }
    }

    private fun loadObjectStream(p: JsonParser, story: Story): Any? {
        val token = p.nextToken()
        if (token == JsonToken.VALUE_NULL) return null
        if (token.isBoolean) return p.booleanValue
        if (token.isNumeric) return BigDecimal(p.text)
        val str = p.text
        return story.wrapper.getStoryObject(str)
    }
}
