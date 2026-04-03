package com.agentsessiontab

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Coarse activity hint from the OS scheduler (same letters `ps` uses: R run, S sleep, I idle, etc.).
 */
enum class ProcessActivityKind {
    /** Runnable / on CPU (R, sometimes rare in a snapshot). */
    Active,
    /** Sleeping, idle, or waiting on I/O — normal for most agent CLIs between turns. */
    IdleOrWaiting,
    /** Stopped (job control). */
    Stopped,
    /** Defunct process. */
    Zombie,
    Unknown,
}

data class ProcessActivity(
    val kind: ProcessActivityKind,
    /** Raw `state` / `stat` field from ps. */
    val rawState: String,
) {
    val shortLabel: String
        get() = when (kind) {
            ProcessActivityKind.Active -> "Active"
            ProcessActivityKind.IdleOrWaiting -> "Idle / waiting"
            ProcessActivityKind.Stopped -> "Stopped"
            ProcessActivityKind.Zombie -> "Zombie"
            ProcessActivityKind.Unknown -> "State ?"
        }
}

/**
 * Live agent CLI matched from argv, with cwd and scheduler state.
 */
data class RunningAgent(
    val label: String,
    val pid: Long,
    val activity: ProcessActivity,
    /** Resolved cwd when available; null if lsof/proc failed or disallowed. */
    val cwd: String?,
    /** Short command line for disambiguation. */
    val argvPreview: String,
)

private val homeDir = System.getProperty("user.home") ?: ""

private val agentMatchers: List<Pair<Regex, String>> = listOf(
    Regex("""claude-code""", RegexOption.IGNORE_CASE) to "Claude",
    Regex("""@anthropic-ai/claude""", RegexOption.IGNORE_CASE) to "Claude",
    Regex("""[/]claude(?!-desktop)(\s|$)""", RegexOption.IGNORE_CASE) to "Claude",
    Regex("""(openai\.cli\.codex|codex-cli|/bin/codex(\s|$)|/.local/bin/codex(\s|$))""", RegexOption.IGNORE_CASE) to "Codex",
    Regex("""(gemini-cli|@google/gemini|google-gemini-cli)""", RegexOption.IGNORE_CASE) to "Gemini",
    Regex("""[/]gemini(\s|$)""", RegexOption.IGNORE_CASE) to "Gemini",
    Regex("""hermes-agent|hermes\s+monitor""", RegexOption.IGNORE_CASE) to "Hermes",
    Regex("""cursor-agent""", RegexOption.IGNORE_CASE) to "Cursor",
)

internal fun classifyProcessState(raw: String): ProcessActivity {
    val s = raw.trim()
    if (s.isEmpty()) return ProcessActivity(ProcessActivityKind.Unknown, raw)
    val c = s.first()
    return when (c.uppercaseChar()) {
        'R' -> ProcessActivity(ProcessActivityKind.Active, raw)
        'T', 't' -> ProcessActivity(ProcessActivityKind.Stopped, raw)
        'Z' -> ProcessActivity(ProcessActivityKind.Zombie, raw)
        'S', 'I', 'D', 'U', 'W' -> ProcessActivity(ProcessActivityKind.IdleOrWaiting, raw)
        else -> ProcessActivity(ProcessActivityKind.Unknown, raw)
    }
}

private fun classifyAgent(argv: String): String? {
    for ((regex, label) in agentMatchers) {
        if (regex.containsMatchIn(argv)) return label
    }
    return null
}

private val psPidStateArgs =
    Regex("""^\s*(\d+)\s+(\S+)\s+(.*)$""")

private fun parsePsLine(line: String): Triple<Long, String, String>? {
    val m = psPidStateArgs.matchEntire(line.trim()) ?: return null
    val pid = m.groupValues[1].toLongOrNull() ?: return null
    val state = m.groupValues[2]
    val argv = m.groupValues[3].trim()
    if (argv.isEmpty()) return null
    return Triple(pid, state, argv)
}

private fun resolveCwd(pid: Long): String? {
    val os = System.getProperty("os.name").lowercase()
    if (!os.contains("mac") && !os.contains("darwin")) {
        val procCwd = Path.of("/proc/$pid/cwd")
        if (Files.isSymbolicLink(procCwd) || Files.exists(procCwd)) {
            try {
                return Files.readSymbolicLink(procCwd).toString()
            } catch (_: Exception) {
                // fall through to lsof
            }
        }
    }
    return cwdViaLsof(pid)
}

private fun lsofExecutable(): String {
    val candidates = listOf("/usr/sbin/lsof", "/usr/bin/lsof")
    return candidates.firstOrNull { Files.isExecutable(Path.of(it)) } ?: "lsof"
}

private fun cwdViaLsof(pid: Long): String? {
    val pb = ProcessBuilder(lsofExecutable(), "-a", "-p", pid.toString(), "-d", "cwd", "-n", "-P")
    pb.redirectErrorStream(true)
    return try {
        val proc = pb.start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor(2, TimeUnit.SECONDS)
        val line = text.lineSequence().firstOrNull { it.contains(" cwd ") && it.contains(" DIR ") }
            ?: return null
        val idx = line.indexOf('/')
        if (idx < 0) null else line.substring(idx).trim()
    } catch (_: Exception) {
        null
    }
}

private fun previewArgv(argv: String, maxLen: Int = 96): String {
    val oneLine = argv.replace(Regex("\\s+"), " ").trim()
    return if (oneLine.length <= maxLen) oneLine else oneLine.take(maxLen - 1) + "…"
}

internal fun shortenHomePath(absolute: String): String {
    if (homeDir.isNotEmpty() && absolute.startsWith(homeDir)) {
        return "~" + absolute.substring(homeDir.length)
    }
    return absolute
}

internal fun listRunningAgents(): List<RunningAgent> {
    val os = System.getProperty("os.name").lowercase()
    val cmd = if (os.contains("mac") || os.contains("darwin")) {
        listOf("ps", "-ax", "-o", "pid=,state=,args=")
    } else {
        listOf("ps", "-eo", "pid=,stat=,args=")
    }
    val pb = ProcessBuilder(cmd)
    pb.redirectErrorStream(true)
    val lines: List<String> = try {
        val proc = pb.start()
        val psText = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor(3, TimeUnit.SECONDS)
        psText.lineSequence().toList()
    } catch (_: Exception) {
        emptyList()
    }

    val seen = mutableSetOf<Long>()
    val agents = mutableListOf<RunningAgent>()
    for (line in lines) {
        if (line.isBlank()) continue
        val (pid, stateToken, argv) = parsePsLine(line) ?: continue
        if (!seen.add(pid)) continue
        val label = classifyAgent(argv) ?: continue
        val cwd = resolveCwd(pid)
        agents.add(
            RunningAgent(
                label = label,
                pid = pid,
                activity = classifyProcessState(stateToken),
                cwd = cwd,
                argvPreview = previewArgv(argv),
            ),
        )
    }
    return agents.sortedWith(compareBy({ it.label }, { it.pid }))
}
