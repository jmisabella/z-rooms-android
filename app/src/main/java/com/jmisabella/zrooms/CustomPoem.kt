package com.jmisabella.zrooms

import java.util.Date
import java.util.UUID

/**
 * Data class representing a custom poem
 */
data class CustomPoem(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var text: String,
    val dateCreated: Date = Date()
)
