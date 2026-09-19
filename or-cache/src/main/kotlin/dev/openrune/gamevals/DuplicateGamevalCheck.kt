package dev.openrune.gamevals

import java.io.File

/**
 * Fails when two names claim the same id within one gameval table. Scans the merged
 * `.data/gamevals` rscm tables and every `gamevals.toml` under `content/` (whose collisions
 * would otherwise only surface during a cache build, as a `Mapping conflict`).
 */
fun main(args: Array<String>) {
    val root = args.firstOrNull()?.let(::File) ?: File(System.getProperty("user.dir"))
    val errors = mutableListOf<String>()

    val gamevalsDir = File(root, ".data/gamevals")
    val rscmFiles =
        if (gamevalsDir.isDirectory) {
            gamevalsDir.listFiles { f -> f.isFile && f.extension == "rscm" }.orEmpty().sortedBy { it.name }
        } else {
            errors += "missing directory: ${gamevalsDir.invariantSeparatorsPath}"
            emptyList()
        }
    rscmFiles.forEach { checkRscm(it, errors) }

    val contentDir = File(root, "content")
    if (contentDir.isDirectory) {
        contentDir
            .walkTopDown()
            .filter { it.isFile && it.name == "gamevals.toml" && !it.isGeneratedPath() }
            .forEach { checkToml(it, errors) }
    }

    if (errors.isNotEmpty()) {
        System.err.println("Duplicate gameval ids found:")
        errors.forEach { System.err.println("  $it") }
        System.err.println(
            "Every id within a table must be unique (see docs/plugins.md). Move fork-invented ids " +
                "into the documented reserved bands, never upstream's growth path."
        )
        kotlin.system.exitProcess(1)
    }
    println("checkGamevals: ${rscmFiles.size} tables clean")
}

private fun checkRscm(file: File, errors: MutableList<String>) {
    val byValue = LinkedHashMap<Int, MutableSet<String>>()
    file.useLines { lines ->
        lines.forEachIndexed { idx, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val split = line.indexOf('=')
            if (split <= 0) {
                errors += "${file.name}:${idx + 1}: invalid line '$line'"
                return@forEachIndexed
            }
            val value = line.substring(split + 1).trim().toIntOrNull()
            if (value == null) {
                errors += "${file.name}:${idx + 1}: invalid id '${line.substring(split + 1).trim()}'"
                return@forEachIndexed
            }
            byValue.getOrPut(value) { LinkedHashSet() }.add(line.substring(0, split).trim())
        }
    }
    byValue.filterValues { it.size > 1 }.forEach { (value, names) ->
        errors += "${file.name}: id $value claimed by ${names.joinToString(", ")}"
    }
}

private fun checkToml(file: File, errors: MutableList<String>) {
    val byValue = LinkedHashMap<Pair<String, Int>, MutableSet<String>>()
    var table: String? = null
    file.useLines { lines ->
        lines.forEachIndexed { idx, raw ->
            val line = raw.trim()
            val section = TOML_SECTION_REGEX.matchEntire(line)
            if (section != null) {
                table = section.groupValues[1]
                return@forEachIndexed
            }
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val current = table ?: return@forEachIndexed
            val split = line.indexOf('=')
            if (split <= 0) return@forEachIndexed
            val value = line.substring(split + 1).trim().toIntOrNull() ?: return@forEachIndexed
            val key = line.substring(0, split).trim()
            byValue.getOrPut(current to value) { LinkedHashSet() }.add(key)
        }
    }
    byValue.filterValues { it.size > 1 }.forEach { (tableValue, names) ->
        val (section, value) = tableValue
        errors += "${file.name}: [$section] id $value claimed by ${names.joinToString(", ")}"
    }
}

private val TOML_SECTION_REGEX = Regex("""^\s*\[gamevals\.([^\.\]]+)\]\s*$""")

private fun File.isGeneratedPath(): Boolean {
    val normalized = invariantSeparatorsPath
    return "/build/" in normalized || "/out/" in normalized || "/target/" in normalized
}
