package com.jmisabella.zrooms

import java.util.Date
import java.util.UUID

/**
 * Represents a custom meditation with title and text
 */
data class CustomMeditation(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var text: String,
    val dateCreated: Date = Date()
)
