package com.example.update

internal object VersionRules {

    private val BODY_HINT = Regex(
        "version(?:\\s|-)?code[^0-9]{0,16}(\\d+)",
        setOf(RegexOption.IGNORE_CASE)
    )

    private val TAG_TRIPLE = Regex(
        "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun versionCodeFromTag(tag: String): Int? {
        val t = tag.trim()
        val m = TAG_TRIPLE.matchEntire(t) ?: return null
        val major = m.groupValues[1].toIntOrNull() ?: return null
        val minor = m.groupValues[2].toIntOrNull() ?: return null
        val patch = m.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (major < 0 || minor < 0 || patch < 0) return null
        if (minor > 99 || patch > 99) return null
        return major * 10_000 + minor * 100 + patch
    }

    fun versionCodeFromBody(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        val m = BODY_HINT.find(body) ?: return null
        val code = m.groupValues[1].toIntOrNull() ?: return null
        return if (code > 0) code else null
    }

    fun versionNameFromTag(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V").ifBlank { tag.trim() }

    fun resolveVersionCode(tag: String, body: String?): Int? =
        versionCodeFromBody(body) ?: versionCodeFromTag(tag)

    fun isNewer(remote: Int, local: Int): Boolean = remote > local
}
