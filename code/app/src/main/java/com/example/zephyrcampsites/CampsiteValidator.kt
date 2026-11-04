package com.example.zephyrcampsites

data class CampsiteFormInput(
    val name: String = "",
    val location: String = "",
    val longitude: String = "",
    val latitude: String = "",
    val description: String = "",
    val maxOccupancy: String = "",
    val tags: String = ""
)

data class CampsiteFormErrors(
    val name: String? = null,
    val location: String? = null,
    val longitude: String? = null,
    val latitude: String? = null,
    val maxOccupancy: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(name, location, longitude, latitude, maxOccupancy).any { it != null }
}

object CampsiteValidator