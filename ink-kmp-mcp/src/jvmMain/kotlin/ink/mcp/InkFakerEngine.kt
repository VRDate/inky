package ink.mcp

import io.github.serpro69.kfaker.Faker
import io.github.serpro69.kfaker.fakerConfig
import io.github.serpro69.kfaker.games.GamesFaker
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * Faker + POI formula engine for generating game data from emoji categories.
 *
 * Uses kotlin-faker 2.0 (modular) for name/item generation,
 * kotlin.random.Random(seed) for deterministic stat ranges,
 * and Apache POI XSSFWorkbook for formula evaluation.
 *
 * Emoji category → faker method mapping:
 *   🗡️ → game.elderScrolls weapon, 🧙 → name + dnd class/race,
 *   ⚗️ → science element, 🗝️ → ancient god, etc.
 */
class InkFakerEngine(
    private val manifest: EmojiAssetManifest = EmojiAssetManifest()
) {

    private val log = LoggerFactory.getLogger(InkFakerEngine::class.java)

    data class FakerConfig(
        val seed: Long = 42L,
        val locale: String = "en",
        val count: Int = 5,
        val level: Int = 1,
        val categories: List<String> = emptyList()
    )

    /** Generate items MD table with emoji categories + faker names + stat formulas */
    fun generateItems(config: FakerConfig): InkMdEngine.MdTable {
        val random = Random(config.seed)
        val faker = createFaker(config)
        val gamesFaker = createGamesFaker(config)

        val columns = listOf("emoji", "name", "type", "base_dmg", "per_level", "level", "total_dmg")

        val itemCategories = if (config.categories.isNotEmpty()) {
            config.categories.mapNotNull { manifest.resolveByName(it)?.category }
        } else {
            manifest.allCategories().filter { it.type in setOf("weapon", "armor", "consumable") }
        }

        val rows = mutableListOf<Map<String, String>>()

        repeat(config.count) { i ->
            val cat = itemCategories[i % itemCategories.size]
            val name = generateItemName(cat, faker, gamesFaker)
            val baseDmg = randomInRange(random, 8..20)
            val perLevel = randomInRange(random, 1..5)

            rows.add(mapOf(
                "emoji" to cat.emoji,
                "name" to name,
                "type" to cat.type,
                "base_dmg" to baseDmg.toString(),
                "per_level" to perLevel.toString(),
                "level" to config.level.toString(),
                "total_dmg" to "=D${i + 2}+E${i + 2}*F${i + 2}"
            ))
        }

        return InkMdEngine.MdTable(name = "items", columns = columns, rows = rows)
    }

    /** Generate DnD characters MD table with faker names + stat formulas */
    fun generateCharacters(config: FakerConfig): InkMdEngine.MdTable {
        val random = Random(config.seed)
        val faker = createFaker(config)
        val gamesFaker = createGamesFaker(config)

        val columns = listOf("emoji", "name", "class", "race", "STR", "DEX", "CON", "INT", "WIS", "CHA", "HP")
        val rows = mutableListOf<Map<String, String>>()

        repeat(config.count) { i ->
            val name = faker.name.name()
            val klass = gamesFaker.dnd.klasses()
            val race = gamesFaker.dnd.races()
            val str = randomInRange(random, 3..18)
            val dex = randomInRange(random, 3..18)
            val con = randomInRange(random, 3..18)
            val int = randomInRange(random, 3..18)
            val wis = randomInRange(random, 3..18)
            val cha = randomInRange(random, 3..18)

            rows.add(mapOf(
                "emoji" to "🧙",
                "name" to name,
                "class" to klass,
                "race" to race,
                "STR" to str.toString(),
                "DEX" to dex.toString(),
                "CON" to con.toString(),
                "INT" to int.toString(),
                "WIS" to wis.toString(),
                "CHA" to cha.toString(),
                "HP" to "=10+G${i + 2}*2"  // HP = 10 + CON * 2
            ))
        }

        return InkMdEngine.MdTable(name = "characters", columns = columns, rows = rows)
    }

    /** Generate a full story MD document with characters + items + formulas + ink blocks */
    fun generateStoryMd(config: FakerConfig): String {
        val characters = generateCharacters(config)
        val items = generateItems(config)
        val itemNames = items.rows.map { it["name"]?.lowercase()?.replace(" ", "_") ?: "item" }

        return buildString {
            // Characters section
            appendLine(
                """
                <!-- seed: ${config.seed}, level: ${config.level} -->

                # characters

                ${tableToMarkdown(characters)}

                ```ink
                // Auto-generated characters
                """.trimIndent()
            )
            if (characters.rows.isNotEmpty()) {
                val first = characters.rows[0]
                appendLine("VAR player_name = \"${first["name"]}\"")
                appendLine("VAR player_class = \"${first["class"]}\"")
                appendLine("VAR player_race = \"${first["race"]}\"")
            }
            appendLine(
                """
                === start ===
                You are {player_name}, a {player_race} {player_class}.
                -> tavern
                ```

                # items

                ${tableToMarkdown(items)}

                ```ink
                // Auto-generated items
                LIST inventory = ${itemNames.joinToString(", ")}
                === tavern ===
                The shopkeeper shows you the wares.
                """.trimIndent()
            )
            for ((i, row) in items.rows.withIndex()) {
                appendLine("+ [Buy ${row["name"]}] -> bought_${itemNames[i]}")
            }
            appendLine(
                """
                + [Leave] -> END
                ```
                """.trimIndent()
            )
        }
    }

    /**
     * Evaluate POI XLSX formulas in an MdTable.
     *
     * Creates an in-memory XSSFWorkbook, populates cells, evaluates formulas,
     * and returns a new MdTable with computed values.
     */
    fun evaluateFormulas(table: InkMdEngine.MdTable): InkMdEngine.MdTable {
        if (table.rows.isEmpty()) return table

        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet(table.name)

            // Header row (row 0)
            val headerRow = sheet.createRow(0)
            for ((colIdx, col) in table.columns.withIndex()) {
                headerRow.createCell(colIdx).setCellValue(col)
            }

            // Data rows (row 1+)
            for ((rowIdx, row) in table.rows.withIndex()) {
                val xlRow = sheet.createRow(rowIdx + 1)
                for ((colIdx, col) in table.columns.withIndex()) {
                    val value = row[col] ?: ""
                    val cell = xlRow.createCell(colIdx)

                    if (value.startsWith("=")) {
                        cell.cellFormula = value.removePrefix("=")
                    } else {
                        // Try numeric first
                        value.toDoubleOrNull()?.let {
                            cell.setCellValue(it)
                        } ?: cell.setCellValue(value)
                    }
                }
            }

            // Evaluate all formulas
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            evaluator.evaluateAll()

            // Read back evaluated values
            val evaluatedRows = mutableListOf<Map<String, String>>()
            for ((rowIdx, row) in table.rows.withIndex()) {
                val xlRow = sheet.getRow(rowIdx + 1)
                val evaluatedRow = mutableMapOf<String, String>()

                for ((colIdx, col) in table.columns.withIndex()) {
                    val cell = xlRow.getCell(colIdx)
                    val originalValue = row[col] ?: ""

                    evaluatedRow[col] = if (originalValue.startsWith("=") && cell != null) {
                        when (cell.cachedFormulaResultType) {
                            CellType.NUMERIC -> {
                                val num = cell.numericCellValue
                                if (num == num.toLong().toDouble()) num.toLong().toString()
                                else num.toString()
                            }
                            CellType.STRING -> cell.stringCellValue
                            CellType.BOOLEAN -> cell.booleanCellValue.toString()
                            else -> originalValue
                        }
                    } else {
                        originalValue
                    }
                }
                evaluatedRows.add(evaluatedRow)
            }

            return InkMdEngine.MdTable(
                name = table.name,
                columns = table.columns,
                rows = evaluatedRows
            )
        } finally {
            workbook.close()
        }
    }

    /** Seeded random integer in range */
    fun randomInRange(random: Random, range: IntRange): Int =
        random.nextInt(range.first, range.last + 1)

    // ── Private helpers ──────────────────────────────────────────

    private fun createFaker(config: FakerConfig): Faker {
        return Faker(fakerConfig { random = java.util.Random(config.seed) })
    }

    private fun createGamesFaker(config: FakerConfig): GamesFaker {
        return GamesFaker(fakerConfig { random = java.util.Random(config.seed) })
    }

    private fun generateItemName(
        category: EmojiAssetManifest.AssetCategory,
        faker: Faker,
        gamesFaker: GamesFaker
    ): String {
        return when (category.name) {
            "sword", "bow", "staff" -> gamesFaker.elderScrolls.weapon()
            "shield" -> "${faker.color.name()} Shield"
            "potion" -> "${faker.color.name()} Potion"
            "key" -> "Key of ${faker.name.lastName()}"
            "map" -> "Map to ${faker.address.city()}"
            "coin" -> "${faker.name.lastName()} Coin"
            "crown" -> "Crown of ${faker.name.lastName()}"
            else -> faker.name.name()
        }
    }

    private fun tableToMarkdown(table: InkMdEngine.MdTable): String = buildString {
        // Header
        appendLine("| ${table.columns.joinToString(" | ")} |")
        // Separator
        appendLine("| ${table.columns.joinToString(" | ") { "---" }} |")
        // Rows
        for (row in table.rows) {
            appendLine("| ${table.columns.joinToString(" | ") { row[it] ?: "" }} |")
        }
    }.trimEnd()
}
