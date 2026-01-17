package com.jmisabella.zrooms

data class StoryCollection(
    val directoryName: String,
    val displayName: String,
    val storyFiles: List<StoryFile> = emptyList(),
    val poemFiles: List<String> = emptyList()
) {
    val id: String
        get() = directoryName
    data class StoryFile(
        val filename: String,
        val sequenceNumber: Int
    )

    companion object {
        fun formatDisplayName(dirName: String): String {
            return dirName.replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    val sortedChapters: List<StoryFile>
        get() = storyFiles.sortedBy { it.sequenceNumber }

    val chapterCount: Int
        get() = storyFiles.size

    val poemCount: Int
        get() = poemFiles.size
}
