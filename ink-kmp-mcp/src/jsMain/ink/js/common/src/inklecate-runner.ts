/**
 * Inklecate Runner — Shared binary path detection, error parsing, and process spawning.
 *
 * Unifies duplicate logic from:
 *   - electron/main-process/inklecate.js (lines 11-25, 152)
 *   - ai/src/main/compiler.js (lines 113-139)
 *   - ai/src/shared/config.js (lines 58-101)
 *
 * Two error‐parsing regexes existed:
 *   electron:  /^(ERROR|WARNING|RUNTIME ERROR|RUNTIME WARNING|TODO): ('([^']+)' )?(line (\d+):)?(.*)/
 *   ai:        /^(ERROR|WARNING|TODO|AUTHOR):\s+([^:]+):?\s+line\s+(\d+):\s*(.+)$/i
 *
 * This module provides a single unified regex that handles both formats.
 */

// ── Types ──────────────────────────────────────────────────────────

export type InkIssueType =
  | "ERROR"
  | "WARNING"
  | "RUNTIME ERROR"
  | "RUNTIME WARNING"
  | "TODO"
  | "AUTHOR";

/** Structured ink compiler issue (error, warning, TODO, etc.). */
export interface InkIssue {
  type: InkIssueType;
  filename: string;
  lineNumber: number;
  message: string;
  /** The original unparsed line (if available). */
  raw?: string;
}

// ── Error Parsing ──────────────────────────────────────────────────

/**
 * Unified inklecate issue regex.
 *
 * Matches both formats:
 *   1. Electron: `ERROR: 'filename.ink' line 42: some message`
 *   2. AI:       `ERROR: filename.ink line 42: some message`
 *   3. No file:  `RUNTIME ERROR: line 42: some message`
 *   4. No line:  `WARNING: some message`
 *
 * Groups:
 *   [1] type       — ERROR | WARNING | RUNTIME ERROR | RUNTIME WARNING | TODO | AUTHOR
 *   [2] (unused)   — full quoted-filename group `'file.ink' ` (optional)
 *   [3] filename   — from quoted format: `file.ink`
 *   [4] filename   — from unquoted format: `file.ink`
 *   [5] (unused)   — full `line N:` group (optional)
 *   [6] lineNumber — digits
 *   [7] message    — rest of line
 */
const ISSUE_REGEX =
  /^(ERROR|WARNING|RUNTIME ERROR|RUNTIME WARNING|TODO|AUTHOR):\s*('([^']+)'\s+)?(?:([^\s:]+(?:\.[^\s:]+))\s+)?(line\s+(\d+):\s*)?(.*)/i;

/**
 * Parse a single line of inklecate output into a structured issue.
 *
 * @param line  A single line from inklecate stderr/stdout
 * @returns     Parsed InkIssue, or null if the line is not an issue
 */
export function parseInklecateIssue(line: string): InkIssue | null {
  const trimmed = line.trim();
  if (!trimmed) return null;

  const m = trimmed.match(ISSUE_REGEX);
  if (!m) return null;

  const type = m[1].toUpperCase() as InkIssueType;
  const filename = m[3] || m[4] || "";
  const lineNumber = m[6] ? parseInt(m[6], 10) : 0;
  const message = (m[7] || "").trim();

  return { type, filename, lineNumber, message, raw: trimmed };
}

/**
 * Parse multi-line inklecate output into an array of structured issues.
 *
 * @param output  Raw compiler output (stdout + stderr combined)
 * @returns       Array of parsed issues
 */
export function parseInklecateOutput(output: string): InkIssue[] {
  const issues: InkIssue[] = [];
  for (const line of output.split("\n")) {
    const issue = parseInklecateIssue(line);
    if (issue) issues.push(issue);
  }
  return issues;
}

// ── Binary Name / Path Detection ───────────────────────────────────

/** Platform-to-binary name mapping. */
const INKLECATE_BINARIES: Record<string, string> = {
  darwin: "inklecate_mac",
  win32: "inklecate_win.exe",
  linux: "inklecate_linux",
};

/**
 * Get the inklecate binary filename for the current (or specified) platform.
 *
 * @param platform  Node.js process.platform value (defaults to current)
 * @returns         Binary filename (e.g. "inklecate_mac")
 * @throws          If platform is not supported
 */
export function getInklecateBinaryName(platform?: string): string {
  const p = platform ?? (typeof process !== "undefined" ? process.platform : "linux");
  const name = INKLECATE_BINARIES[p];
  if (!name) throw new Error(`Unsupported platform: ${p}`);
  return name;
}

/**
 * Options for finding the inklecate binary path.
 */
export interface FindInklecateOptions {
  /** Additional paths to search (checked first, in order). */
  searchPaths?: string[];
  /** Environment variable name to check (default: "INKLECATE_PATH"). */
  envVar?: string;
  /** Platform override (default: process.platform). */
  platform?: string;
}

/**
 * Find the inklecate binary path by searching multiple locations.
 *
 * Unified from:
 *   - electron/inklecate.js (release asar path, then dev __dirname)
 *   - ai/config.js (sibling install, home dir, env var, PATH)
 *
 * Search order:
 *   1. Caller-provided searchPaths
 *   2. Environment variable (INKLECATE_PATH)
 *   3. Fallback: bare binary name (assumes it's in system PATH)
 *
 * @param options  Search configuration
 * @returns        Resolved path or bare binary name as fallback
 */
export function findInklecatePath(options?: FindInklecateOptions): string {
  // Guard: this function requires Node.js fs/path — skip if in browser
  if (typeof process === "undefined") {
    return getInklecateBinaryName(options?.platform);
  }

  /* eslint-disable @typescript-eslint/no-var-requires */
  const fs = require("fs") as typeof import("fs");
  const path = require("path") as typeof import("path");
  const os = require("os") as typeof import("os");

  const binaryName = getInklecateBinaryName(options?.platform);
  const envVar = options?.envVar ?? "INKLECATE_PATH";
  const envPath = process.env[envVar];

  const candidates: string[] = [
    ...(options?.searchPaths ?? []),
    // Electron release (asar-unpacked)
    path.join(__dirname, "../../app.asar.unpacked/main-process/ink", binaryName),
    // Electron dev
    path.join(__dirname, "../ink", binaryName),
    // Sibling Inky install
    path.join(__dirname, "../../../inky_digi/ink-electron/main-process/ink", binaryName),
    // Home directory installs
    path.join(os.homedir(), "inky_digi/ink-electron/main-process/ink", binaryName),
    path.join(os.homedir(), "Inky/ink-electron/main-process/ink", binaryName),
  ];

  // Environment variable override
  if (envPath) candidates.push(envPath);

  for (const candidate of candidates) {
    try {
      if (fs.existsSync(candidate)) return candidate;
    } catch {
      // Continue searching
    }
  }

  // Fallback: assume binary is in system PATH
  return binaryName;
}

// ── Temp Directory ─────────────────────────────────────────────────

/**
 * Get a platform-appropriate temp directory for ink compilation.
 *
 * From electron/inklecate.js lines 27-32.
 *
 * @param namespace  Subdirectory name (default: "inky_compile")
 * @returns          Absolute path to temp compile directory
 */
export function getTempCompileDir(namespace = "inky_compile"): string {
  if (typeof process === "undefined") return `/tmp/${namespace}`;
  const path = require("path") as typeof import("path");

  if (process.platform === "darwin" || process.platform === "linux") {
    const tmpdir = process.env.TMPDIR || "/tmp";
    return path.join(tmpdir, namespace);
  }
  return path.join(process.env.temp || process.env.TEMP || "C:\\Temp", namespace);
}

// ── BOM Stripping ──────────────────────────────────────────────────

/**
 * Strip Unicode BOM (Byte Order Mark) from inklecate output.
 * Mono-based inklecate prepends BOM to stderr/stdout.
 *
 * From electron/inklecate.js lines 109, 159.
 */
export function stripBom(text: string): string {
  return text.replace(/^\uFEFF/, "");
}
