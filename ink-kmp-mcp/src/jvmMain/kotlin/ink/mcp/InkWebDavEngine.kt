package ink.mcp

import com.github.sardine.SardineFactory
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * WebDAV engine for ink script file management.
 *
 * Provides:
 *   1. Local filesystem operations (Ktor WebDAV server routes)
 *   2. Remote WebDAV client via Sardine (sync with external servers)
 *
 * Access control (matches InkVCardEngine domain hierarchy):
 *   - domain/user/shared/  → public read (anyone can list/get)
 *   - domain/user/shared/  → write requires edit role + domain membership
 *   - domain/user/ (non-shared) → private to user and org members only
 *   - Write always requires edit role
 *
 * File types served: .ink, .puml, .svg, .vcf, .ics, .json, .md
 *
 * LLM model access:
 *   - LLM models need domain/model.vcf to get read access to domain users
 *   - LLM has a local working copy of user files for editing
 *   - Origin = user's files, working copy = LLM's copy
 *   - Yjs/HocusPocus sync merges changes if enabled
 *
 * Backup convention:
 *   - Script without timestamp = master (main merged copy)
 *   - Backups: domain/user/script/yyyy-MM-dd_HH-mm-ss.SSSSSSSSS.[ext]
 *   - Retention: last N days (default 14 days / 2 weeks)
 *
 * Filesystem layout:
 *   ink-scripts/
 *     example.com/
 *       org.vcf                    ← domain org vCard (grants edit)
 *       claude.vcf                 ← LLM model vCard (grants read to domain)
 *       alice.vcf                  ← user vCard
 *       alice/
 *         script.ink               ← master ink script (main merged copy)
 *         script.puml              ← master PlantUML diagram
 *         script.svg               ← master rendered SVG
 *         script/                  ← backup folder
 *           2026-02-26_14-30-00.000000000.ink  ← timestamped backup
 *           2026-02-26_14-30-00.000000000.puml
 *           2026-02-26_14-30-00.000000000.svg
 *         shared/                  ← public folder
 *           story.ink              ← public ink file
 *           story.puml             ← public PlantUML
 *           story.svg              ← public SVG
 */
class InkWebDavEngine(
    private val vcardEngine: InkVCardEngine? = null,
    private val basePath: String = "./ink-scripts"
) {

    private val log = LoggerFactory.getLogger(InkWebDavEngine::class.java)
    private val baseDir = File(basePath)

    /** Allowed file extensions for WebDAV operations */
    private val allowedExtensions = setOf("ink", "puml", "svg", "vcf", "ics", "json", "md", "txt")

    /** Backup timestamp format: yyyy-MM-dd_HH-mm-ss.SSSSSSSSS */
    private val backupTimestampFormat = DateTimeFormatter
        .ofPattern("yyyy-MM-dd_HH-mm-ss.SSSSSSSSS")
        .withZone(ZoneOffset.UTC)

    /** Default backup retention in days */
    private val defaultRetentionDays = 14L

    init {
        if (!baseDir.exists()) baseDir.mkdirs()
    }

    // ════════════════════════════════════════════════════════════════════
    // ACCESS CONTROL
    // ════════════════════════════════════════════════════════════════════

    /**
     * Check if a principal can access a path.
     *
     * @param principalId Principal ID (null for anonymous)
     * @param path Relative path under ink-scripts/ (e.g. "example.com/alice/script.ink")
     * @param write True for write operations (PUT, DELETE, MKCOL)
     * @return true if access is allowed
     */
    fun canAccess(principalId: String?, path: String, write: Boolean): Boolean {
        val normalized = path.removePrefix("/").removePrefix("ink-scripts/")
        val parts = normalized.split("/").filter { it.isNotEmpty() }

        // Shared folders are publicly readable: domain/user/shared/
        if (!write && parts.size >= 3 && parts[2] == "shared") {
            return true
        }

        // No principal = no access for non-shared paths
        if (principalId == null) return false

        val vc = vcardEngine ?: return true  // No vcard engine = open access

        val folder = vc.getFolderPath(principalId) ?: return false

        if (parts.size >= 2) {
            val domain = parts[0]
            val user = parts[1]
            val pathFolder = "./ink-scripts/$domain/$user/"

            // Owner has full access to their own folder
            if (folder == pathFolder) return true

            // Org members can read/edit if domain/org.vcf exists
            if (folder.startsWith("./ink-scripts/$domain/")) {
                val role = vc.resolveScriptRole(principalId, "./ink-scripts/$normalized")
                return role == "edit" || !write
            }

            // LLM model with domain/model.vcf gets read access to domain users
            if (!write && isLlmWithDomainVcf(principalId, domain)) {
                return true
            }
        }

        return false
    }

    /**
     * Check if a principal is an LLM model with a .vcf file in the domain folder.
     * domain/model.vcf grants read access to all domain users' files.
     */
    private fun isLlmWithDomainVcf(principalId: String, domain: String): Boolean {
        val vcfFile = File(baseDir, "$domain/$principalId.vcf")
        return vcfFile.exists()
    }

    /**
     * Check if a path is in a shared folder (publicly readable).
     */
    fun isSharedPath(path: String): Boolean {
        val normalized = path.removePrefix("/").removePrefix("ink-scripts/")
        val parts = normalized.split("/").filter { it.isNotEmpty() }
        return parts.size >= 3 && parts[2] == "shared"
    }

    // ════════════════════════════════════════════════════════════════════
    // LOCAL FILESYSTEM OPERATIONS (WebDAV server)
    // ════════════════════════════════════════════════════════════════════

    /**
     * List files and directories at a path (PROPFIND).
     * Returns file metadata including name, size, type, and modification time.
     */
    fun listFiles(path: String): List<Map<String, Any>> {
        val dir = resolveFile(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles()?.mapNotNull { file ->
            if (file.isDirectory || isAllowedFile(file)) {
                mapOf(
                    "name" to file.name,
                    "path" to file.relPath(),
                    "is_directory" to file.isDirectory,
                    "size" to if (file.isFile) file.length() else 0L,
                    "last_modified" to file.lastModified(),
                    "content_type" to contentTypeFor(file)
                )
            } else null
        }?.sortedWith(compareBy({ !(it["is_directory"] as Boolean) }, { it["name"] as String }))
            ?: emptyList()
    }

    /**
     * Get file content (GET).
     * Returns file content as string with metadata.
     */
    fun getFile(path: String): Map<String, Any>? {
        val file = resolveFile(path)
        if (!file.exists() || !file.isFile) return null
        if (!isAllowedFile(file)) return null

        return mapOf(
            "name" to file.name,
            "path" to file.relPath(),
            "size" to file.length(),
            "content_type" to contentTypeFor(file),
            "content" to file.readText(StandardCharsets.UTF_8),
            "last_modified" to file.lastModified()
        )
    }

    /**
     * Write file content (PUT).
     * Creates parent directories if needed.
     */
    fun putFile(path: String, content: String): Map<String, Any> {
        val file = resolveFile(path)

        // Validate extension
        if (!isAllowedExtension(file.extension)) {
            return mapOf(
                "ok" to false,
                "error" to "File type not allowed: .${file.extension}",
                "allowed" to allowedExtensions.toList()
            )
        }

        file.parentFile?.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)

        log.info("WebDAV PUT: {} ({} bytes)", file.relPath(), content.length)
        return mapOf(
            "ok" to true,
            "path" to file.relPath(),
            "size" to file.length(),
            "content_type" to contentTypeFor(file)
        )
    }

    /**
     * Delete a file or empty directory (DELETE).
     */
    fun deleteFile(path: String): Map<String, Any> {
        val file = resolveFile(path)
        if (!file.exists()) {
            return mapOf("ok" to false, "error" to "Not found: $path")
        }

        val deleted = if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }

        log.info("WebDAV DELETE: {} (ok={})", file.relPath(), deleted)
        return mapOf(
            "ok" to deleted,
            "path" to file.relPath()
        )
    }

    /**
     * Create a directory (MKCOL).
     */
    fun mkDir(path: String): Map<String, Any> {
        val dir = resolveFile(path)
        if (dir.exists()) {
            return mapOf(
                "ok" to dir.isDirectory,
                "path" to dir.relPath(),
                "message" to if (dir.isDirectory) "Directory already exists" else "Path exists as file"
            )
        }

        val created = dir.mkdirs()
        log.info("WebDAV MKCOL: {} (ok={})", dir.relPath(), created)
        return mapOf(
            "ok" to created,
            "path" to dir.relPath()
        )
    }

    /**
     * Copy a file (COPY).
     */
    fun copyFile(srcPath: String, destPath: String): Map<String, Any> {
        val src = resolveFile(srcPath)
        val dest = resolveFile(destPath)

        if (!src.exists()) {
            return mapOf("ok" to false, "error" to "Source not found: $srcPath")
        }

        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)

        log.info("WebDAV COPY: {} -> {}", src.relPath(), dest.relPath())
        return mapOf(
            "ok" to true,
            "src" to src.relPath(),
            "dest" to dest.relPath(),
            "size" to dest.length()
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // BACKUP & WORKING COPY
    // ════════════════════════════════════════════════════════════════════

    /**
     * Create a timestamped backup of a master file.
     *
     * Master file: domain/user/script.ink (no timestamp = main merged copy)
     * Backup file: domain/user/script/yyyy-MM-dd_HH-mm-ss.SSSSSSSSS.ink
     *
     * Backups are stored in a subfolder named after the master file (without extension).
     */
    fun createBackup(path: String): Map<String, Any> {
        val file = resolveFile(path)
        if (!file.exists() || !file.isFile) {
            return mapOf("ok" to false, "error" to "Master file not found: $path")
        }

        val timestamp = backupTimestampFormat.format(Instant.now())
        val ext = file.extension
        val baseName = file.nameWithoutExtension

        // Backup folder = same name as master file (without extension)
        val backupDir = File(file.parentFile, baseName)
        backupDir.mkdirs()

        val backupFile = File(backupDir, "$timestamp.$ext")
        file.copyTo(backupFile, overwrite = true)

        log.info("Backup created: {} -> {}", file.relPath(), backupFile.relPath())
        return mapOf(
            "ok" to true,
            "master" to file.relPath(),
            "backup" to backupFile.relPath(),
            "timestamp" to timestamp,
            "size" to backupFile.length()
        )
    }

    /**
     * Create timestamped backups for all matching files (ink, puml, svg) of a script.
     *
     * Given "example.com/alice/script", backs up:
     *   script.ink  -> script/timestamp.ink
     *   script.puml -> script/timestamp.puml
     *   script.svg  -> script/timestamp.svg
     */
    fun createBackupSet(scriptPath: String): Map<String, Any> {
        val extensions = listOf("ink", "puml", "svg")
        val timestamp = backupTimestampFormat.format(Instant.now())
        val backedUp = mutableListOf<String>()

        for (ext in extensions) {
            val masterFile = resolveFile("$scriptPath.$ext")
            if (!masterFile.exists()) continue

            val baseName = masterFile.nameWithoutExtension
            val backupDir = File(masterFile.parentFile, baseName)
            backupDir.mkdirs()

            val backupFile = File(backupDir, "$timestamp.$ext")
            masterFile.copyTo(backupFile, overwrite = true)
            backedUp.add(backupFile.relPath())
        }

        log.info("Backup set created for {}: {} files", scriptPath, backedUp.size)
        return mapOf(
            "ok" to true,
            "script_path" to scriptPath,
            "timestamp" to timestamp,
            "backed_up" to backedUp,
            "count" to backedUp.size
        )
    }

    /**
     * List backups for a master file, newest first.
     */
    fun listBackups(path: String): List<Map<String, Any>> {
        val file = resolveFile(path)
        val baseName = file.nameWithoutExtension
        val backupDir = File(file.parentFile, baseName)

        if (!backupDir.exists() || !backupDir.isDirectory) return emptyList()

        return backupDir.listFiles()
            ?.filter { it.isFile && isAllowedFile(it) }
            ?.sortedByDescending { it.name }
            ?.map { backup ->
                mapOf(
                    "name" to backup.name,
                    "path" to backup.relPath(),
                    "size" to backup.length(),
                    "timestamp" to backup.nameWithoutExtension,
                    "extension" to backup.extension,
                    "last_modified" to backup.lastModified()
                )
            }
            ?: emptyList()
    }

    /**
     * Restore a backup to the master file.
     * Copies the backup content over the master (no-timestamp) file.
     */
    fun restoreBackup(backupPath: String, masterPath: String): Map<String, Any> {
        val backup = resolveFile(backupPath)
        val master = resolveFile(masterPath)

        if (!backup.exists()) {
            return mapOf("ok" to false, "error" to "Backup not found: $backupPath")
        }

        backup.copyTo(master, overwrite = true)

        log.info("Backup restored: {} -> {}", backup.relPath(), master.relPath())
        return mapOf(
            "ok" to true,
            "backup" to backup.relPath(),
            "master" to master.relPath(),
            "size" to master.length()
        )
    }

    /**
     * Purge old backups beyond retention period.
     *
     * @param path Master file path (e.g. "example.com/alice/script.ink")
     * @param retentionDays Number of days to keep backups (default: 14)
     * @return Summary of purged files
     */
    fun purgeBackups(path: String, retentionDays: Long = defaultRetentionDays): Map<String, Any> {
        val file = resolveFile(path)
        val baseName = file.nameWithoutExtension
        val backupDir = File(file.parentFile, baseName)

        if (!backupDir.exists() || !backupDir.isDirectory) {
            return mapOf("ok" to true, "purged_count" to 0, "message" to "No backup directory")
        }

        val cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS)
        var purged = 0
        val purgedFiles = mutableListOf<String>()

        backupDir.listFiles()?.filter { it.isFile }?.forEach { backup ->
            try {
                val ts = backupTimestampFormat.parse(backup.nameWithoutExtension, Instant::from)
                if (ts.isBefore(cutoff)) {
                    backup.delete()
                    purged++
                    purgedFiles.add(backup.name)
                }
            } catch (_: Exception) {
                // Skip files that don't match timestamp format
            }
        }

        log.info("Purged {} backups older than {} days for {}", purged, retentionDays, path)
        return mapOf(
            "ok" to true,
            "path" to path,
            "retention_days" to retentionDays,
            "purged_count" to purged,
            "purged_files" to purgedFiles
        )
    }

    /**
     * Create a working copy of a user's files for an LLM model.
     *
     * Origin = user's files (master)
     * Working copy = LLM's local copy for editing
     *
     * Layout: domain/user/ (origin) -> domain/modelId/user/ (working copy)
     * Multiple models can share the same working copy path convention.
     * Yjs/HocusPocus sync merges changes back if enabled.
     */
    fun createWorkingCopy(
        originPath: String,
        modelId: String
    ): Map<String, Any> {
        val originDir = resolveFile(originPath)
        if (!originDir.exists() || !originDir.isDirectory) {
            return mapOf("ok" to false, "error" to "Origin directory not found: $originPath")
        }

        // Working copy goes into domain/modelId/user/
        val normalized = originPath.removePrefix("/").removePrefix("ink-scripts/")
        val parts = normalized.split("/").filter { it.isNotEmpty() }
        if (parts.size < 2) {
            return mapOf("ok" to false, "error" to "Invalid origin path: needs domain/user/")
        }

        val domain = parts[0]
        val user = parts[1]
        val workDir = resolveFile("$domain/$modelId/$user")
        workDir.mkdirs()

        var copied = 0
        val copiedFiles = mutableListOf<String>()

        // Copy allowed files from origin to working copy
        originDir.listFiles()?.filter { isAllowedFile(it) }?.forEach { file ->
            file.copyTo(File(workDir, file.name), overwrite = true)
            copied++
            copiedFiles.add(file.name)
        }

        log.info("Working copy created: {} -> {} ({} files)", originDir.relPath(), workDir.relPath(), copied)
        return mapOf(
            "ok" to true,
            "origin" to originDir.relPath(),
            "working_copy" to workDir.relPath(),
            "model_id" to modelId,
            "copied_count" to copied,
            "copied_files" to copiedFiles
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // SARDINE CLIENT (remote WebDAV)
    // ════════════════════════════════════════════════════════════════════

    /**
     * List files on a remote WebDAV server using Sardine.
     */
    fun remoteList(url: String, username: String?, password: String?): List<Map<String, Any>> {
        val sardine = if (username != null && password != null) {
            SardineFactory.begin(username, password)
        } else {
            SardineFactory.begin()
        }

        return try {
            sardine.list(url).map { resource ->
                mapOf(
                    "name" to (resource.name ?: ""),
                    "href" to (resource.href?.toString() ?: ""),
                    "is_directory" to resource.isDirectory,
                    "size" to (resource.contentLength ?: 0L),
                    "content_type" to (resource.contentType ?: ""),
                    "last_modified" to (resource.modified?.time ?: 0L)
                )
            }
        } finally {
            sardine.shutdown()
        }
    }

    /**
     * Get file content from a remote WebDAV server using Sardine.
     */
    fun remoteGet(url: String, username: String?, password: String?): Map<String, Any> {
        val sardine = if (username != null && password != null) {
            SardineFactory.begin(username, password)
        } else {
            SardineFactory.begin()
        }

        return try {
            val stream = sardine.get(url)
            val content = stream.readBytes().toString(StandardCharsets.UTF_8)
            stream.close()
            mapOf(
                "ok" to true,
                "url" to url,
                "content" to content,
                "size" to content.length
            )
        } finally {
            sardine.shutdown()
        }
    }

    /**
     * Put file content to a remote WebDAV server using Sardine.
     */
    fun remotePut(
        url: String,
        content: String,
        contentType: String = "text/plain",
        username: String?,
        password: String?
    ): Map<String, Any> {
        val sardine = if (username != null && password != null) {
            SardineFactory.begin(username, password)
        } else {
            SardineFactory.begin()
        }

        return try {
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            sardine.put(url, ByteArrayInputStream(bytes), contentType, true, bytes.size.toLong())
            mapOf(
                "ok" to true,
                "url" to url,
                "size" to bytes.size
            )
        } finally {
            sardine.shutdown()
        }
    }

    /**
     * Sync files from a remote WebDAV server to a local path.
     * Downloads all files from remoteUrl into the local ink-scripts path.
     */
    fun syncFromRemote(
        remoteUrl: String,
        localPath: String,
        username: String?,
        password: String?
    ): Map<String, Any> {
        val sardine = if (username != null && password != null) {
            SardineFactory.begin(username, password)
        } else {
            SardineFactory.begin()
        }

        var synced = 0
        var errors = 0
        val syncedFiles = mutableListOf<String>()

        try {
            val resources = sardine.list(remoteUrl)
            for (resource in resources) {
                if (resource.isDirectory) continue
                val name = resource.name ?: continue

                // Only sync allowed file types
                val ext = name.substringAfterLast(".", "")
                if (!isAllowedExtension(ext)) continue

                try {
                    val stream = sardine.get(remoteUrl.trimEnd('/') + "/" + name)
                    val content = stream.readBytes()
                    stream.close()

                    val localFile = resolveFile("$localPath/$name")
                    localFile.parentFile?.mkdirs()
                    localFile.writeBytes(content)

                    synced++
                    syncedFiles.add(name)
                } catch (e: Exception) {
                    errors++
                    log.warn("Failed to sync {}: {}", name, e.message)
                }
            }
        } finally {
            sardine.shutdown()
        }

        log.info("WebDAV sync from {}: {} files synced, {} errors", remoteUrl, synced, errors)
        return mapOf(
            "ok" to true,
            "remote_url" to remoteUrl,
            "local_path" to localPath,
            "synced_count" to synced,
            "error_count" to errors,
            "synced_files" to syncedFiles
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    private fun resolveFile(path: String): File {
        val normalized = path.removePrefix("/").removePrefix("ink-scripts/")
        return File(baseDir, normalized).canonicalFile.also {
            // Path traversal protection
            require(it.canonicalPath.startsWith(baseDir.canonicalPath)) {
                "Path traversal not allowed: $path"
            }
        }
    }

    /** Return the relative path from baseDir using forward slashes (platform-independent). */
    private fun File.relPath(): String =
        relativeTo(baseDir).path.replace('\\', '/')

    private fun isAllowedFile(file: File): Boolean {
        return file.isFile && isAllowedExtension(file.extension)
    }

    private fun isAllowedExtension(ext: String): Boolean {
        return ext.lowercase() in allowedExtensions
    }

    private fun contentTypeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "ink" -> "text/x-ink"
            "puml" -> "text/x-plantuml"
            "svg" -> "image/svg+xml"
            "vcf" -> "text/vcard"
            "ics" -> "text/calendar"
            "json" -> "application/json"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            else -> if (file.isDirectory) "httpd/unix-directory" else "application/octet-stream"
        }
    }
}
