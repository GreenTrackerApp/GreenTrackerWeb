package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class SmokeSession(
    val id: Long = 0L,
    val timestamp: Long,
    val grams: Double,
    val strain: String = "",
    val notes: String = "",
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
