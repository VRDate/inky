package ink.mcp

import ink.kt.Story
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ink-proof conformance test harness.
 *
 * Runs [ink-proof](https://github.com/chromy/ink-proof) bytecode test cases
 * against [ink.kt.Story] (the compiled-JSON runtime). Each test case provides:
 * - `bytecode.json` — compiled ink story
 * - `input.txt` — choice inputs (one per line, 1-indexed)
 * - `transcript.txt` — expected output
 * - `metadata.json` — test name and tags
 *
 * The transcript format from ink-proof is:
 * ```
 * Text line
 *
 * 1: Choice A
 * 2: Choice B
 * ?> Selected choice text
 * More text...
 * ```
 *
 * @see <a href="https://github.com/chromy/ink-proof">chromy/ink-proof</a>
 */
class InkProofTest {

    private val bytecodeDir = File(
        System.getProperty("user.dir"),
        "src/test/resources/ink-proof/bytecode"
    )

    @Test
    fun `B001 hello world`() = runBytecodeTest("B001")

    @Test
    fun `B002 nop is supported`() = runBytecodeTest("B002")

    @Test
    fun `B003 evaluate numbers`() = runBytecodeTest("B003")

    // B004 is hidden (platform-specific comically large number)

    @Test
    fun `B005 choices`() = runBytecodeTest("B005")

    @Test
    fun `B006 raw ints and doubles not printed`() = runBytecodeTest("B006")

    @Test
    fun `B007 coercive operations on types`() = runBytecodeTest("B007")

    private fun runBytecodeTest(testId: String) {
        val testDir = File(bytecodeDir, testId)
        if (!testDir.exists()) return

        val bytecodeJson = File(testDir, "bytecode.json").readText()
        val expectedTranscript = File(testDir, "transcript.txt").readText().trimEnd()
        val inputFile = File(testDir, "input.txt")
        val inputs = if (inputFile.exists())
            inputFile.readText().trim().lines().filter { it.isNotEmpty() }
        else
            emptyList()

        val story = Story(bytecodeJson)
        val actualOutput = StringBuilder()
        var inputIdx = 0

        while (story.canContinue() || story.currentChoices.isNotEmpty()) {
            while (story.canContinue()) {
                val text = story.continueStory()
                actualOutput.append(text)
            }

            val choices = story.currentChoices
            if (choices.isNotEmpty()) {
                actualOutput.appendLine()
                for (choice in choices) {
                    actualOutput.appendLine("${choice.index + 1}: ${choice.text}")
                }

                if (inputIdx < inputs.size) {
                    val choiceNum = inputs[inputIdx].trim().toInt()
                    inputIdx++
                    val selectedChoice = choices[choiceNum - 1]
                    actualOutput.appendLine("?> ${selectedChoice.text}")
                    story.chooseChoiceIndex(choiceNum - 1)
                } else {
                    break
                }
            }
        }

        val actual = actualOutput.toString().trimEnd()
        assertEquals(
            expectedTranscript,
            actual,
            "ink-proof $testId transcript mismatch"
        )
    }
}
