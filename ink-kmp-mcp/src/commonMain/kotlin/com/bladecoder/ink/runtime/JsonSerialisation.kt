package com.bladecoder.ink.runtime

/**
 * JSON ↔ runtime object serialisation — best of C# (inkle/ink), Java (blade-ink), JS (inkjs).
 *
 * Three-way comparison notes:
 * - C# (751 lines): static class Json, sequential instanceof → as-cast dispatch
 * - Java (751 lines): static class Json, sequential instanceof dispatch (identical logic)
 * - JS: Relies on native JSON.parse with reviver + post-processing
 *
 * Kotlin design decisions:
 * - `object` singleton (idiomatic Kotlin, replaces static class)
 * - `when (obj)` with smart casts (replaces if-instanceof chains)
 * - LinkedHashMap for all dictionaries (insertion-order + O(1))
 * - Uses SimpleJson.Writer from the KMP-native SimpleJson port
 * - Replaces the Json stub in VariablesState.kt
 * - Control command names: Array indexed by ordinal (same as C#/Java)
 *
 * Zero 3rd party dependencies. Pure Kotlin stdlib.
 */
object JsonSerialisation {

    // ── JSON Encoding Scheme (from C#/Java docs) ────────────
    //
    // Glue:           "<>"
    // ControlCommand: "ev", "out", "/ev", "du", "pop", "->->", "~ret", "str", "/str", "nop",
    //                 "choiceCnt", "turn", "turns", "readc", "rnd", "srnd", "visit", "seq",
    //                 "thread", "done", "end", "listInt", "range", "lrnd", "#", "/#"
    // NativeFunction: "+", "-", "/", "*", "%", "~", "==", ">", "<", ">=", "<=", "!=", "!"...
    // Void:           "void"
    // Value:          "^string value", "^^string value beginning with ^"
    //                 5, 5.2, true, false
    //                 {"^->": "path.target"}
    //                 {"^var": "varname", "ci": 0}
    // Container:      [..., {namedContent, "#f": flags, "#n": "name"}]
    // Divert:         {"->": "path"}, {"f()": "path"}, {"->t->": "path"}, {"x()": "funcName"}
    // VarAssign:      {"VAR=": "name", "re": true}, {"temp=": "name"}
    // VarRef:         {"VAR?": "name"}, {"CNT?": "path"}
    // ChoicePoint:    {"*": "path", "flg": 18}
    // Tag:            {"#": "text"}
    // List:           {"list": {"origin.item": val}, "origins": [...]}

    // ── Write: Runtime Objects → JSON ────────────────────────

    fun writeListRuntimeObjs(writer: SimpleJson.Writer, list: List<InkObject>) {
        writer.writeArrayStart()
        for (obj in list) {
            writeRuntimeObject(writer, obj)
        }
        writer.writeArrayEnd()
    }

    fun writeDictionaryRuntimeObjs(writer: SimpleJson.Writer, dictionary: Map<String, InkObject>) {
        writer.writeObjectStart()
        for ((key, value) in dictionary) {
            writer.writePropertyStart(key)
            writeRuntimeObject(writer, value)
            writer.writePropertyEnd()
        }
        writer.writeObjectEnd()
    }

    fun writeIntDictionary(writer: SimpleJson.Writer, dict: Map<String, Int>) {
        writer.writeObjectStart()
        for ((key, value) in dict) {
            writer.writeProperty(key, value)
        }
        writer.writeObjectEnd()
    }

    /**
     * Core write dispatcher — serializes any runtime object to JSON.
     * C#: WriteRuntimeObject with sequential as-cast checks
     * Java: writeRuntimeObject with sequential instanceof checks
     * Kotlin: when with smart cast — cleaner, same semantics
     */
    fun writeRuntimeObject(writer: SimpleJson.Writer, obj: InkObject) {
        when (obj) {
            is Container -> {
                writeRuntimeContainer(writer, obj)
            }

            is Divert -> {
                val divTypeKey = when {
                    obj.isExternal -> "x()"
                    obj.pushesToStack && obj.stackPushType == PushPopType.Function -> "f()"
                    obj.pushesToStack && obj.stackPushType == PushPopType.Tunnel -> "->t->"
                    else -> "->"
                }

                val targetStr = if (obj.hasVariableTarget) obj.variableDivertName else obj.targetPathString

                writer.writeObjectStart()
                writer.writeProperty(divTypeKey, targetStr ?: "")

                if (obj.hasVariableTarget) writer.writeProperty("var", true)
                if (obj.isConditional) writer.writeProperty("c", true)
                if (obj.externalArgs > 0) writer.writeProperty("exArgs", obj.externalArgs)

                writer.writeObjectEnd()
            }

            is ChoicePoint -> {
                writer.writeObjectStart()
                writer.writeProperty("*", obj.pathStringOnChoice ?: "")
                writer.writeProperty("flg", obj.flags)
                writer.writeObjectEnd()
            }

            is BoolValue -> writer.write(obj.value)
            is IntValue -> writer.write(obj.value)
            is FloatValue -> writer.write(obj.value)

            is StringValue -> {
                if (obj.isNewline) {
                    writer.write("\\n", escape = false)
                } else {
                    writer.writeStringStart()
                    writer.writeStringInner("^")
                    writer.writeStringInner(obj.value)
                    writer.writeStringEnd()
                }
            }

            is ListValue -> writeInkList(writer, obj)

            is DivertTargetValue -> {
                writer.writeObjectStart()
                writer.writeProperty("^->", obj.value?.componentsString ?: "")
                writer.writeObjectEnd()
            }

            is VariablePointerValue -> {
                writer.writeObjectStart()
                writer.writeProperty("^var", obj.value ?: "")
                writer.writeProperty("ci", obj.contextIndex)
                writer.writeObjectEnd()
            }

            is Glue -> writer.write("<>")

            is ControlCommand -> {
                writer.write(controlCommandNames[obj.commandType.ordinal - 1])
            }

            is NativeFunctionCall -> {
                var name = obj.name ?: ""
                // Avoid collision with ^ used to indicate a string
                if (name == "^") name = "L^"
                writer.write(name)
            }

            is VariableReference -> {
                writer.writeObjectStart()
                val readCountPath = obj.pathStringForCount
                if (readCountPath != null) {
                    writer.writeProperty("CNT?", readCountPath)
                } else {
                    writer.writeProperty("VAR?", obj.name ?: "")
                }
                writer.writeObjectEnd()
            }

            is VariableAssignment -> {
                writer.writeObjectStart()
                val key = if (obj.isGlobal) "VAR=" else "temp="
                writer.writeProperty(key, obj.variableName ?: "")
                // Reassignment?
                if (!obj.isNewDeclaration) writer.writeProperty("re", true)
                writer.writeObjectEnd()
            }

            is Void -> writer.write("void")

            is Tag -> {
                writer.writeObjectStart()
                writer.writeProperty("#", obj.text)
                writer.writeObjectEnd()
            }

            is Choice -> writeChoice(writer, obj)

            else -> throw StoryException("Failed to write runtime object to JSON: $obj")
        }
    }

    // ── Read: JSON → Runtime Objects ─────────────────────────

    /**
     * Convert JSON array to list of typed runtime objects.
     * C#: JArrayToRuntimeObjList<T>, Java: jArrayToRuntimeObjList
     */
    fun jArrayToRuntimeObjList(jArray: List<Any?>, skipLast: Boolean = false): MutableList<InkObject> {
        val count = if (skipLast) jArray.size - 1 else jArray.size
        val list = ArrayList<InkObject>(count)

        for (i in 0 until count) {
            val runtimeObj = jTokenToRuntimeObject(jArray[i])
            if (runtimeObj != null) {
                list.add(runtimeObj)
            }
        }

        return list
    }

    fun jObjectToDictionaryRuntimeObjs(jObject: Map<String, Any?>): LinkedHashMap<String, InkObject> {
        val dict = LinkedHashMap<String, InkObject>(jObject.size)
        for ((key, value) in jObject) {
            val runtimeObj = jTokenToRuntimeObject(value)
            if (runtimeObj != null) {
                dict[key] = runtimeObj
            }
        }
        return dict
    }

    fun jObjectToIntDictionary(jObject: Map<String, Any?>): LinkedHashMap<String, Int> {
        val dict = LinkedHashMap<String, Int>(jObject.size)
        for ((key, value) in jObject) {
            dict[key] = (value as Number).toInt()
        }
        return dict
    }

    /**
     * Core read dispatcher — converts JSON token to runtime object.
     * C#: JTokenToRuntimeObject, Java: jTokenToRuntimeObject
     * Kotlin: when-based dispatch with smart cast
     */
    @Suppress("UNCHECKED_CAST")
    fun jTokenToRuntimeObject(token: Any?): InkObject? {
        if (token == null) return null

        // Primitives → Values
        when (token) {
            is Int -> return Value.create(token)
            is Float -> return Value.create(token)
            is Double -> return Value.create(token.toFloat())
            is Boolean -> return Value.create(token)
        }

        // String tokens
        if (token is String) {
            val str = token
            if (str.isEmpty()) return StringValue("")

            val firstChar = str[0]

            // String value: "^content" or "^^content starting with ^"
            if (firstChar == '^') return StringValue(str.substring(1))

            // Newline
            if (firstChar == '\n' && str.length == 1) return StringValue("\n")

            // Glue
            if (str == "<>") return Glue()

            // Control commands
            for (i in controlCommandNames.indices) {
                if (str == controlCommandNames[i]) {
                    return ControlCommand(ControlCommand.CommandType.entries[i + 1])
                }
            }

            // Native functions — "L^" is escaped "^" operator
            var funcName = str
            if (funcName == "L^") funcName = "^"
            if (NativeFunctionCall.callExistsWithName(funcName)) {
                return NativeFunctionCall.callWithName(funcName)
            }

            // Void
            if (str == "void") return Void()
        }

        // Dictionary-based objects
        if (token is Map<*, *>) {
            val obj = token as Map<String, Any?>
            var propValue: Any?

            // DivertTargetValue: {"^->": "path"}
            propValue = obj["^->"]
            if (propValue != null) {
                return DivertTargetValue(Path(propValue as String))
            }

            // VariablePointerValue: {"^var": "name", "ci": index}
            propValue = obj["^var"]
            if (propValue != null) {
                val varPtr = VariablePointerValue(propValue as String)
                val ci = obj["ci"]
                if (ci != null) varPtr.contextIndex = (ci as Number).toInt()
                return varPtr
            }

            // Divert variants: "->", "f()", "->t->", "x()"
            var isDivert = false
            var pushesToStack = false
            var divPushType = PushPopType.Function
            var external = false

            propValue = obj["->"]
            if (propValue != null) {
                isDivert = true
            } else {
                propValue = obj["f()"]
                if (propValue != null) {
                    isDivert = true
                    pushesToStack = true
                    divPushType = PushPopType.Function
                } else {
                    propValue = obj["->t->"]
                    if (propValue != null) {
                        isDivert = true
                        pushesToStack = true
                        divPushType = PushPopType.Tunnel
                    } else {
                        propValue = obj["x()"]
                        if (propValue != null) {
                            isDivert = true
                            external = true
                            pushesToStack = false
                            divPushType = PushPopType.Function
                        }
                    }
                }
            }

            if (isDivert) {
                val divert = Divert()
                divert.pushesToStack = pushesToStack
                divert.stackPushType = divPushType
                divert.isExternal = external
                val target = propValue.toString()

                if (obj["var"] != null) {
                    divert.variableDivertName = target
                } else {
                    divert.targetPathString = target
                }

                divert.isConditional = obj["c"] != null

                if (external) {
                    val exArgs = obj["exArgs"]
                    if (exArgs != null) divert.externalArgs = (exArgs as Number).toInt()
                }

                return divert
            }

            // ChoicePoint: {"*": "path", "flg": flags}
            propValue = obj["*"]
            if (propValue != null) {
                val choice = ChoicePoint()
                choice.pathStringOnChoice = propValue.toString()
                val flg = obj["flg"]
                if (flg != null) choice.flags = (flg as Number).toInt()
                return choice
            }

            // VariableReference: {"VAR?": "name"} or {"CNT?": "path"}
            propValue = obj["VAR?"]
            if (propValue != null) {
                return VariableReference(propValue.toString())
            }
            propValue = obj["CNT?"]
            if (propValue != null) {
                val readCountVarRef = VariableReference()
                readCountVarRef.pathStringForCount = propValue.toString()
                return readCountVarRef
            }

            // VariableAssignment: {"VAR=": "name"} or {"temp=": "name"}
            var isVarAss = false
            var isGlobalVar = false

            propValue = obj["VAR="]
            if (propValue != null) {
                isVarAss = true
                isGlobalVar = true
            } else {
                propValue = obj["temp="]
                if (propValue != null) {
                    isVarAss = true
                    isGlobalVar = false
                }
            }
            if (isVarAss) {
                val varName = propValue.toString()
                val isNewDecl = obj["re"] == null
                val varAss = VariableAssignment(varName, isNewDecl)
                varAss.isGlobal = isGlobalVar
                return varAss
            }

            // Legacy Tag: {"#": "text"}
            propValue = obj["#"]
            if (propValue != null) {
                return Tag(propValue as String)
            }

            // ListValue: {"list": {...}, "origins": [...]}
            propValue = obj["list"]
            if (propValue != null) {
                val listContent = propValue as Map<String, Any?>
                val rawList = InkList()

                val origins = obj["origins"]
                if (origins != null) {
                    val namesAsObjs = origins as List<String>
                    rawList.setInitialOriginNames(namesAsObjs)
                }

                for ((nameKey, nameVal) in listContent) {
                    val item = InkListItem(nameKey)
                    val intVal = (nameVal as Number).toInt()
                    rawList[item] = intVal
                }

                return ListValue(rawList)
            }

            // Choice (save state only): {"originalChoicePath": ...}
            if (obj["originalChoicePath"] != null) {
                return jObjectToChoice(obj)
            }
        }

        // Array → Container
        if (token is List<*>) {
            return jArrayToContainer(token as List<Any?>)
        }

        throw StoryException("Failed to convert token to runtime object: $token")
    }

    // ── Container ────────────────────────────────────────────

    fun writeRuntimeContainer(writer: SimpleJson.Writer, container: Container, withoutName: Boolean = false) {
        writer.writeArrayStart()

        for (c in container.content) {
            writeRuntimeObject(writer, c)
        }

        // Container final element: named content + flags + name, or null
        val namedOnlyContent = container.namedOnlyContent
        val countFlags = container.countFlags
        val hasNameProperty = container.name != null && !withoutName

        val hasTerminator = namedOnlyContent.isNotEmpty() || countFlags > 0 || hasNameProperty

        if (hasTerminator) writer.writeObjectStart()

        for ((name, contentObj) in namedOnlyContent) {
            val namedContainer = contentObj as? Container
            if (namedContainer != null) {
                writer.writePropertyStart(name)
                writeRuntimeContainer(writer, namedContainer, withoutName = true)
                writer.writePropertyEnd()
            }
        }

        if (countFlags > 0) writer.writeProperty("#f", countFlags)
        if (hasNameProperty) writer.writeProperty("#n", container.name ?: "")

        if (hasTerminator) writer.writeObjectEnd()
        else writer.writeNull()

        writer.writeArrayEnd()
    }

    @Suppress("UNCHECKED_CAST")
    private fun jArrayToContainer(jArray: List<Any?>): Container {
        val container = Container()
        container.addContents(jArrayToRuntimeObjList(jArray, skipLast = true))

        // Final element: named content + flags
        val terminatingObj = jArray[jArray.size - 1] as? Map<String, Any?>
        if (terminatingObj != null) {
            val namedOnlyContent = LinkedHashMap<String, InkObject>()
            for ((key, value) in terminatingObj) {
                when (key) {
                    "#f" -> container.countFlags = (value as Number).toInt()
                    "#n" -> container.setName(value.toString())
                    else -> {
                        val namedContentItem = jTokenToRuntimeObject(value)
                        val namedSubContainer = namedContentItem as? Container
                        namedSubContainer?.setName(key)
                        if (namedContentItem != null) {
                            namedOnlyContent[key] = namedContentItem
                        }
                    }
                }
            }
            container.setNamedOnlyContent(namedOnlyContent as HashMap<String, InkObject>)
        }

        return container
    }

    // ── Choice ───────────────────────────────────────────────

    fun writeChoice(writer: SimpleJson.Writer, choice: Choice) {
        writer.writeObjectStart()
        writer.writeProperty("text", choice.text ?: "")
        writer.writeProperty("index", choice.index)
        writer.writeProperty("originalChoicePath", choice.sourcePath ?: "")
        writer.writeProperty("originalThreadIndex", choice.originalThreadIndex)
        writer.writeProperty("targetPath", choice.pathStringOnChoice ?: "")
        writeChoiceTags(writer, choice)
        writer.writeObjectEnd()
    }

    private fun writeChoiceTags(writer: SimpleJson.Writer, choice: Choice) {
        val tags = choice.tags
        if (tags.isNullOrEmpty()) return
        writer.writePropertyStart("tags")
        writer.writeArrayStart()
        for (tag in tags) {
            writer.write(tag)
        }
        writer.writeArrayEnd()
        writer.writePropertyEnd()
    }

    @Suppress("UNCHECKED_CAST")
    private fun jObjectToChoice(jObj: Map<String, Any?>): Choice {
        val choice = Choice()
        choice.text = jObj["text"].toString()
        choice.index = (jObj["index"] as Number).toInt()
        choice.sourcePath = jObj["originalChoicePath"].toString()
        choice.originalThreadIndex = (jObj["originalThreadIndex"] as Number).toInt()
        choice.pathStringOnChoice = jObj["targetPath"].toString()
        choice.tags = jArrayToTags(jObj)
        return choice
    }

    @Suppress("UNCHECKED_CAST")
    private fun jArrayToTags(jObj: Map<String, Any?>): List<String>? {
        val jArray = jObj["tags"] ?: return null
        return (jArray as List<Any?>).map { it.toString() }
    }

    // ── InkList ──────────────────────────────────────────────

    private fun writeInkList(writer: SimpleJson.Writer, listVal: ListValue) {
        val rawList = listVal.value

        writer.writeObjectStart()

        writer.writePropertyStart("list")
        writer.writeObjectStart()

        for ((item, itemVal) in rawList) {
            writer.writePropertyNameStart()
            writer.writePropertyNameInner(item.originName ?: "?")
            writer.writePropertyNameInner(".")
            writer.writePropertyNameInner(item.itemName ?: "")
            writer.writePropertyNameEnd()

            writer.write(itemVal)

            writer.writePropertyEnd()
        }

        writer.writeObjectEnd()
        writer.writePropertyEnd()

        // Write origins if empty list but has definitions
        val originNames = rawList.originNames
        if (rawList.size == 0 && originNames != null && originNames.size > 0) {
            writer.writePropertyStart("origins")
            writer.writeArrayStart()
            for (name in originNames) writer.write(name)
            writer.writeArrayEnd()
            writer.writePropertyEnd()
        }

        writer.writeObjectEnd()
    }

    // ── List Definitions ─────────────────────────────────────

    fun listDefinitionsToJToken(origin: ListDefinitionsOrigin): LinkedHashMap<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        for (def in origin.lists) {
            val listDefJson = LinkedHashMap<String, Any?>()
            for ((item, value) in def.items) {
                listDefJson[item.itemName ?: ""] = value
            }
            result[def.name ?: ""] = listDefJson
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    fun jTokenToListDefinitions(obj: Any?): ListDefinitionsOrigin {
        val defsObj = obj as Map<String, Any?>

        val allDefs = mutableListOf<ListDefinition>()

        for ((name, value) in defsObj) {
            val listDefJson = value as Map<String, Any?>

            // Cast (string, object) → (string, int) for items
            val items = LinkedHashMap<String, Int>()
            for ((itemName, itemValue) in listDefJson) {
                items[itemName] = (itemValue as Number).toInt()
            }

            allDefs.add(ListDefinition(name, items))
        }

        return ListDefinitionsOrigin(allDefs)
    }

    // ── Control Command Name Table ───────────────────────────

    /**
     * Bidirectional mapping: CommandType ordinal ↔ JSON string token.
     * Array indexed by (ordinal - 1) since NotSet=0 is skipped.
     * C#: static string[] _controlCommandNames, Java: static final String[]
     * Kotlin: Array<String> initialized at object-init time
     */
    private val controlCommandNames: Array<String> = run {
        val entries = ControlCommand.CommandType.entries
        val names = Array(entries.size - 1) { "" } // -1 to skip NotSet

        names[ControlCommand.CommandType.EvalStart.ordinal - 1] = "ev"
        names[ControlCommand.CommandType.EvalOutput.ordinal - 1] = "out"
        names[ControlCommand.CommandType.EvalEnd.ordinal - 1] = "/ev"
        names[ControlCommand.CommandType.Duplicate.ordinal - 1] = "du"
        names[ControlCommand.CommandType.PopEvaluatedValue.ordinal - 1] = "pop"
        names[ControlCommand.CommandType.PopFunction.ordinal - 1] = "~ret"
        names[ControlCommand.CommandType.PopTunnel.ordinal - 1] = "->->"
        names[ControlCommand.CommandType.BeginString.ordinal - 1] = "str"
        names[ControlCommand.CommandType.EndString.ordinal - 1] = "/str"
        names[ControlCommand.CommandType.NoOp.ordinal - 1] = "nop"
        names[ControlCommand.CommandType.ChoiceCount.ordinal - 1] = "choiceCnt"
        names[ControlCommand.CommandType.Turns.ordinal - 1] = "turn"
        names[ControlCommand.CommandType.TurnsSince.ordinal - 1] = "turns"
        names[ControlCommand.CommandType.ReadCount.ordinal - 1] = "readc"
        names[ControlCommand.CommandType.Random.ordinal - 1] = "rnd"
        names[ControlCommand.CommandType.SeedRandom.ordinal - 1] = "srnd"
        names[ControlCommand.CommandType.VisitIndex.ordinal - 1] = "visit"
        names[ControlCommand.CommandType.SequenceShuffleIndex.ordinal - 1] = "seq"
        names[ControlCommand.CommandType.StartThread.ordinal - 1] = "thread"
        names[ControlCommand.CommandType.Done.ordinal - 1] = "done"
        names[ControlCommand.CommandType.End.ordinal - 1] = "end"
        names[ControlCommand.CommandType.ListFromInt.ordinal - 1] = "listInt"
        names[ControlCommand.CommandType.ListRange.ordinal - 1] = "range"
        names[ControlCommand.CommandType.ListRandom.ordinal - 1] = "lrnd"
        names[ControlCommand.CommandType.BeginTag.ordinal - 1] = "#"
        names[ControlCommand.CommandType.EndTag.ordinal - 1] = "/#"

        // Validate completeness — matches C#/Java static init validation
        for (i in names.indices) {
            if (names[i].isEmpty()) {
                throw StoryException("Control command (index $i) not accounted for in serialisation")
            }
        }

        names
    }
}
