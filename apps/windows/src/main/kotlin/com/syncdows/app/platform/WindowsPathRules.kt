package com.syncdows.app.platform

object WindowsPathRules {
    private val reserved = buildSet {
        addAll(listOf("CON", "PRN", "AUX", "NUL", "CLOCK$"))
        (1..9).forEach { number ->
            add("COM$number")
            add("LPT$number")
        }
    }

    fun validateRelativePath(path: String): String {
        val normalized = path.replace('\\', '/').trim('/')
        require(normalized.isNotBlank()) { "File path is empty" }
        normalized.split('/').forEach { component ->
            require(component.isNotEmpty() && component != "." && component != "..") {
                "File path contains an invalid component"
            }
            require(component.none { it < ' ' || it in INVALID_CHARACTERS }) {
                "File name contains a character Windows does not support"
            }
            require(!component.endsWith(' ') && !component.endsWith('.')) {
                "Windows file names cannot end with a space or period"
            }
            require(!isReservedName(component)) { "File path uses a reserved Windows name" }
        }
        return normalized
    }

    fun isReservedName(component: String): Boolean =
        component.substringBefore('.').uppercase() in reserved

    private val INVALID_CHARACTERS = setOf('<', '>', ':', '"', '|', '?', '*')
}
