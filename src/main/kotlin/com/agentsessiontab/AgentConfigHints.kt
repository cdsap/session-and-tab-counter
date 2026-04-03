package com.agentsessiontab

import java.nio.file.Files
import java.nio.file.Path

private const val MaxSnippetChars = 96_384

private val jsonModelPatterns = listOf(
    Regex(""""model"\s*:\s*"([^"]{1,160})""""),
    Regex(""""defaultModel"\s*:\s*"([^"]{1,160})""""),
    Regex(""""modelId"\s*:\s*"([^"]{1,160})""""),
    Regex(""""claude\.defaultModel"\s*:\s*"([^"]{1,160})""""),
    Regex(""""cursor\.cpp\.defaultModel"\s*:\s*"([^"]{1,160})""""),
    Regex(""""cursor\.general\.model"\s*:\s*"([^"]{1,160})""""),
)

private val jsonTokenPatterns = listOf(
    Regex(""""maxTokens"\s*:\s*(\d{2,9})""", RegexOption.IGNORE_CASE),
    Regex(""""contextWindow"\s*:\s*(\d{2,9})""", RegexOption.IGNORE_CASE),
    Regex(""""max_input_tokens"\s*:\s*(\d{2,9})""", RegexOption.IGNORE_CASE),
    Regex(""""contextLength"\s*:\s*(\d{2,9})""", RegexOption.IGNORE_CASE),
    Regex(""""maxOutputTokens"\s*:\s*(\d{2,9})""", RegexOption.IGNORE_CASE),
)

private fun extractFromJsonLike(text: String): Pair<List<String>, List<String>> {
    val models = linkedSetOf<String>()
    val tokens = linkedSetOf<String>()
    for (r in jsonModelPatterns) {
        r.findAll(text).forEach { m ->
            m.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { models.add(it) }
        }
    }
    for (r in jsonTokenPatterns) {
        r.findAll(text).forEach { m ->
            m.groupValues.getOrNull(1)?.let { tokens.add(it) }
        }
    }
    return models.toList() to tokens.toList()
}

private fun readSnip(path: Path): String? {
    if (!Files.isRegularFile(path)) return null
    return try {
        val s = Files.readString(path)
        if (s.length > MaxSnippetChars) s.substring(0, MaxSnippetChars) else s
    } catch (_: Exception) {
        null
    }
}

private fun hintsFromFile(path: Path, source: String): List<String> {
    val text = readSnip(path) ?: return emptyList()
    val (models, caps) = extractFromJsonLike(text)
    val out = mutableListOf<String>()
    if (models.isNotEmpty()) {
        out.add("$source · model field(s): ${models.take(3).joinToString(", ")}${if (models.size > 3) "…" else ""}")
    }
    if (caps.isNotEmpty()) {
        out.add("$source · token/window field(s): ${caps.take(4).joinToString(", ")}")
    }
    return out
}

/**
 * Best-effort strings from local JSON configs and argv. Not live runtime context — files may be stale or absent.
 */
internal fun gatherConfigHints(label: String, cwd: String?, argv: String): List<String> {
    val home = System.getProperty("user.home") ?: return argvHintsOnly(argv)
    val acc = LinkedHashSet<String>()
    when (label) {
        "Claude" -> {
            hintsFromFile(Path.of(home, ".claude/settings.json"), "~/.claude/settings.json").forEach { acc.add(it) }
            hintsFromFile(Path.of(home, ".claude.json"), "~/.claude.json").forEach { acc.add(it) }
            if (cwd != null) {
                hintsFromFile(Path.of(cwd, ".claude/settings.local.json"), "project .claude/settings.local.json").forEach { acc.add(it) }
            }
        }
        "Cursor" -> {
            hintsFromFile(Path.of(home, "Library/Application Support/Cursor/User/settings.json"), "Cursor settings").forEach { acc.add(it) }
            hintsFromFile(Path.of(home, ".config/Cursor/User/settings.json"), "Cursor settings").forEach { acc.add(it) }
        }
        "Gemini" -> {
            hintsFromFile(Path.of(home, ".gemini/settings.json"), "~/.gemini/settings.json").forEach { acc.add(it) }
            hintsFromFile(Path.of(home, ".gemini/config.json"), "~/.gemini/config.json").forEach { acc.add(it) }
            hintsFromFile(Path.of(home, ".config/gemini/settings.json"), "~/.config/gemini").forEach { acc.add(it) }
            if (cwd != null) {
                hintsFromFile(Path.of(cwd, ".gemini/settings.json"), "project .gemini/settings.json").forEach { acc.add(it) }
            }
        }
        else -> {}
    }
    argvHintsOnly(argv).forEach { acc.add(it) }
    return acc.take(8)
}

private fun argvHintsOnly(argv: String): List<String> {
    val out = mutableListOf<String>()
    Regex("""(?:^|\s)--model(?:=|\s+)(\S+)""", RegexOption.IGNORE_CASE).findAll(argv).forEach { m ->
        out.add("command line · --model ${m.groupValues[1]}")
    }
    Regex("""(?:^|\s)-m(?:=|\s+)(\S+)""").findAll(argv).take(1).forEach { m ->
        if (out.none { it.contains("command line") }) out.add("command line · -m ${m.groupValues[1]}")
    }
    Regex("""(?:^|\s)--model-id(?:=|\s+)(\S+)""", RegexOption.IGNORE_CASE).findAll(argv).forEach { m ->
        out.add("command line · --model-id ${m.groupValues[1]}")
    }
    return out
}
