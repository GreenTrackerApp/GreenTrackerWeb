package com.example.data

import kotlinx.serialization.Serializable

@Serializable
data class StrainEntry(
    val id: Long = 0,
    val strainName: String,
    val producerCultivar: String = "",
    val category: String = "Hybrid",
    val thcPercentage: Double = 0.0,
    val cbdPercentage: Double = 0.0,
    val rating: String = "THUMBS_UP",
    val notes: String = "",
    val photoUri: String = "",
    val needsReview: Boolean = false,
    val reviewReminderTime: Long? = null,
    val createdAt: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)
