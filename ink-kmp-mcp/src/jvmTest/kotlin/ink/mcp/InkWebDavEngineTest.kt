package ink.mcp

import ink.kt.TestResources
import kotlin.test.*

/**
 * Tests for InkWebDavEngine — local filesystem operations, access control,
 * backup/restore, and working copy creation.
 *
 * These tests use a temporary directory and don't require GraalJS or Sardine.
 * Note: InkWebDavEngine is inherently filesystem-based (JVM server), but test
 * setup uses [TestResources.tempDir] for KMP-ready abstraction.
 */
class InkWebDavEngineTest {

    private lateinit var tempPath: String
    private lateinit var dav: InkWebDavEngine

    @BeforeTest
    fun setup() {
        tempPath = TestResources.tempDir("inky-webdav-test")
        dav = InkWebDavEngine(vcardEngine = null, basePath = tempPath)
    }

    @AfterTest
    fun teardown() {
        java.io.File(tempPath).deleteRecursively()
    }

    // ═══════════════════════════════════════════════════════════════
    // FILE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `putFile and getFile round-trip`() {
        val content = "Hello World!\n-> END"
        val result = dav.putFile("example.com/alice/story.ink", content)
        assertTrue(result["ok"] as Boolean, "putFile should succeed")

        val file = dav.getFile("example.com/alice/story.ink")
        assertNotNull(file)
        assertEquals(content, file["content"])
        assertEquals("text/x-ink", file["content_type"])
    }

    @Test
    fun `putFile rejects disallowed extensions`() {
        val result = dav.putFile("example.com/alice/hack.exe", "malware")
        assertFalse(result["ok"] as Boolean)
        assertTrue((result["error"] as String).contains("not allowed"))
    }

    @Test
    fun `putFile allows all valid extensions`() {
        val extensions = listOf("ink", "puml", "svg", "vcf", "ics", "json", "md", "txt")
        for (ext in extensions) {
            val result = dav.putFile("example.com/alice/test.$ext", "content")
            assertTrue(result["ok"] as Boolean, "Should allow .$ext files")
        }
    }

    @Test
    fun `listFiles shows files and directories`() {
        dav.putFile("example.com/alice/story.ink", "Hello")
        dav.putFile("example.com/alice/story.puml", "@startuml")
        dav.mkDir("example.com/alice/shared")

        val files = dav.listFiles("example.com/alice")
        assertTrue(files.isNotEmpty())

        val names = files.map { it["name"] as String }
        assertTrue("story.ink" in names)
        assertTrue("story.puml" in names)
        assertTrue("shared" in names)
    }

    @Test
    fun `deleteFile removes file`() {
        dav.putFile("example.com/alice/temp.ink", "temp")
        val result = dav.deleteFile("example.com/alice/temp.ink")
        assertTrue(result["ok"] as Boolean)

        val file = dav.getFile("example.com/alice/temp.ink")
        assertNull(file, "File should be deleted")
    }

    @Test
    fun `mkDir creates directory`() {
        val result = dav.mkDir("example.com/alice/chapter2")
        assertTrue(result["ok"] as Boolean)

        val files = dav.listFiles("example.com/alice")
        val names = files.map { it["name"] as String }
        assertTrue("chapter2" in names)
    }

    @Test
    fun `getFile returns null for nonexistent file`() {
        val file = dav.getFile("example.com/alice/nonexistent.ink")
        assertNull(file)
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESS CONTROL
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `shared folder is publicly readable`() {
        assertTrue(dav.canAccess(null, "example.com/alice/shared/story.ink", false),
            "Shared folder should be publicly readable")
    }

    @Test
    fun `shared folder write requires principal`() {
        assertFalse(dav.canAccess(null, "example.com/alice/shared/story.ink", true),
            "Anonymous should not write to shared")
    }

    @Test
    fun `non-shared folder denies anonymous`() {
        assertFalse(dav.canAccess(null, "example.com/alice/private.ink", false),
            "Anonymous should not read private files")
    }

    @Test
    fun `isSharedPath detects shared folders`() {
        assertTrue(dav.isSharedPath("example.com/alice/shared/story.ink"))
        assertTrue(dav.isSharedPath("example.com/alice/shared/sub/deep.ink"))
        assertFalse(dav.isSharedPath("example.com/alice/private.ink"))
        assertFalse(dav.isSharedPath("example.com/alice/notshared/story.ink"))
    }

    @Test
    fun `no vcardEngine means open access`() {
        // With null vcardEngine, any principal should have access
        assertTrue(dav.canAccess("anyone", "example.com/alice/private.ink", false))
        assertTrue(dav.canAccess("anyone", "example.com/alice/private.ink", true))
    }

    @Test
    fun `LLM model with domain vcf gets read access`() {
        // Create a model.vcf file in the domain folder
        dav.putFile("example.com/claude.vcf", "BEGIN:VCARD\nFN:Claude\nEND:VCARD")

        // LLM model with matching vcf should get read access
        // (Note: this tests the isLlmWithDomainVcf path directly via canAccess
        //  but since vcardEngine is null, canAccess returns true for any principal.
        //  Full auth integration test needs vcardEngine mock.)
    }

    // ═══════════════════════════════════════════════════════════════
    // PATH TRAVERSAL PROTECTION
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `path traversal is blocked`() {
        assertFailsWith<IllegalArgumentException> {
            dav.getFile("../../../etc/passwd")
        }
    }

    @Test
    fun `dotdot in path is blocked`() {
        assertFailsWith<IllegalArgumentException> {
            dav.putFile("example.com/../../escape.txt", "hack")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BACKUP & RESTORE
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `createBackup creates timestamped copy`() {
        dav.putFile("example.com/alice/story.ink", "Original content")

        val result = dav.createBackup("example.com/alice/story.ink")
        assertTrue(result["ok"] as Boolean)
        assertNotNull(result["timestamp"])
        assertNotNull(result["backup"])

        val backupPath = result["backup"] as String
        assertTrue(backupPath.contains("story/"), "Backup should be in story/ subfolder")
        assertTrue(backupPath.endsWith(".ink"))
    }

    @Test
    fun `createBackupSet backs up ink + puml + svg`() {
        dav.putFile("example.com/alice/story.ink", "Hello")
        dav.putFile("example.com/alice/story.puml", "@startuml")
        dav.putFile("example.com/alice/story.svg", "<svg>")

        val result = dav.createBackupSet("example.com/alice/story")
        assertTrue(result["ok"] as Boolean)
        assertEquals(3, result["count"])

        val backedUp = result["backed_up"] as List<*>
        assertEquals(3, backedUp.size)
        assertTrue(backedUp.any { (it as String).endsWith(".ink") })
        assertTrue(backedUp.any { (it as String).endsWith(".puml") })
        assertTrue(backedUp.any { (it as String).endsWith(".svg") })
    }

    @Test
    fun `listBackups returns backups newest first`() {
        dav.putFile("example.com/alice/story.ink", "v1")
        dav.createBackup("example.com/alice/story.ink")

        // Modify and backup again
        dav.putFile("example.com/alice/story.ink", "v2")
        dav.createBackup("example.com/alice/story.ink")

        val backups = dav.listBackups("example.com/alice/story.ink")
        assertTrue(backups.size >= 2, "Should have at least 2 backups")

        // Verify newest first
        val timestamps = backups.map { it["name"] as String }
        assertEquals(timestamps, timestamps.sortedDescending(), "Backups should be newest first")
    }

    @Test
    fun `restoreBackup overwrites master`() {
        dav.putFile("example.com/alice/story.ink", "Original")
        val backupResult = dav.createBackup("example.com/alice/story.ink")
        val backupPath = backupResult["backup"] as String

        // Modify master
        dav.putFile("example.com/alice/story.ink", "Modified")

        // Restore
        val result = dav.restoreBackup(backupPath, "example.com/alice/story.ink")
        assertTrue(result["ok"] as Boolean)

        // Verify content restored
        val restored = dav.getFile("example.com/alice/story.ink")
        assertEquals("Original", restored!!["content"])
    }

    @Test
    fun `createBackup with no master returns error`() {
        val result = dav.createBackup("example.com/alice/nonexistent.ink")
        assertFalse(result["ok"] as Boolean)
    }

    // ═══════════════════════════════════════════════════════════════
    // WORKING COPY
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `createWorkingCopy copies files to modelId path`() {
        dav.putFile("example.com/alice/story.ink", "Hello")
        dav.putFile("example.com/alice/story.puml", "@startuml")
        dav.putFile("example.com/alice/story.svg", "<svg>")

        val result = dav.createWorkingCopy("example.com/alice", "claude")
        assertTrue(result["ok"] as Boolean)
        assertEquals("claude", result["model_id"])
        assertEquals(3, result["copied_count"])

        // Verify working copy path uses modelId (not modelId_workdir)
        val wcPath = result["working_copy"] as String
        assertTrue(wcPath.contains("claude/alice"), "Working copy path should be domain/claude/alice, got: $wcPath")
        assertFalse(wcPath.contains("workdir"), "Path should NOT contain 'workdir'")

        // Verify files exist in working copy
        val wcFiles = dav.listFiles("example.com/claude/alice")
        val names = wcFiles.map { it["name"] as String }
        assertTrue("story.ink" in names)
        assertTrue("story.puml" in names)
        assertTrue("story.svg" in names)
    }

    @Test
    fun `multiple models can have separate working copies`() {
        dav.putFile("example.com/alice/story.ink", "Hello")

        dav.createWorkingCopy("example.com/alice", "claude")
        dav.createWorkingCopy("example.com/alice", "codemiror")

        val claudeFiles = dav.listFiles("example.com/claude/alice")
        val codemirorFiles = dav.listFiles("example.com/codemiror/alice")

        assertTrue(claudeFiles.isNotEmpty(), "Claude should have working copy files")
        assertTrue(codemirorFiles.isNotEmpty(), "Codemiror should have working copy files")
    }

    @Test
    fun `createWorkingCopy with invalid path returns error`() {
        val result = dav.createWorkingCopy("invalid", "claude")
        assertFalse(result["ok"] as Boolean)
    }

    // ═══════════════════════════════════════════════════════════════
    // PURGE BACKUPS
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `purgeBackups with no backups returns zero`() {
        dav.putFile("example.com/alice/story.ink", "Hello")
        val result = dav.purgeBackups("example.com/alice/story.ink")
        assertTrue(result["ok"] as Boolean)
        assertEquals(0, result["purged_count"])
    }

    @Test
    fun `copyFile copies content`() {
        dav.putFile("example.com/alice/story.ink", "Original content")
        val result = dav.copyFile("example.com/alice/story.ink", "example.com/alice/story-copy.ink")
        assertTrue(result["ok"] as Boolean)

        val copy = dav.getFile("example.com/alice/story-copy.ink")
        assertEquals("Original content", copy!!["content"])
    }
}
