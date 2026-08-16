package com.syncdroid.app.storage

data class SyncFilterRules(
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
) {
    fun shouldSync(relativePath: String, isDirectory: Boolean = false): Boolean {
        if (isDirectory) return true
        val normalized = relativePath.replace('\\', '/')
        if (excludes.any { globMatches(it, normalized) }) return false
        return includes.isEmpty() || includes.any { globMatches(it, normalized) }
    }

    fun summary(): String = when {
        includes.isEmpty() && excludes.isEmpty() -> "All files"
        includes.isNotEmpty() && excludes.isEmpty() -> includes.joinToString()
        includes.isEmpty() -> "All except ${excludes.joinToString()}"
        else -> "${includes.joinToString()} · excluding ${excludes.joinToString()}"
    }
}

private fun globMatches(rawPattern: String, path: String): Boolean {
    val pattern = rawPattern.trim().replace('\\', '/')
    if (pattern.isEmpty()) return false
    val matchTarget = if ('/' in pattern) path else path.substringAfterLast('/')
    val regex = buildString {
        append('^')
        var index = 0
        while (index < pattern.length) {
            when (val char = pattern[index]) {
                '*' -> {
                    if (index + 1 < pattern.length && pattern[index + 1] == '*') {
                        append(".*")
                        index++
                    } else {
                        append("[^/]*")
                    }
                }
                '?' -> append("[^/]")
                '.', '(', ')', '[', ']', '$', '^', '{', '}', '+', '|', '\\' -> append("\\$char")
                else -> append(char)
            }
            index++
        }
        append('$')
    }
    return Regex(regex, RegexOption.IGNORE_CASE).matches(matchTarget)
}
