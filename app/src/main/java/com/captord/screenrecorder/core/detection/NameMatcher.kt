
package com.captord.screenrecorder.core.detection

object NameMatcher {

    private val defaultNames = listOf("هيبة")

    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[\\u064B-\\u065F\\u0670ـ]"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun findMatches(
        recognizedText: String,
        names: List<String> = defaultNames
    ): List<String> {
        val text = normalize(recognizedText)

        if (text.isBlank()) return emptyList()

        return names
            .filter { it.isNotBlank() }
            .distinct()
            .filter { name ->
                normalize(name).let { target ->
                    target.isNotBlank() && text.contains(target)
                }
            }
    }
}
