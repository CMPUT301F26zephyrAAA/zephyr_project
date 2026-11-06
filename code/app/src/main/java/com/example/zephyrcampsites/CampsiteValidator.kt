package com.example.zephyrcampsites

data class CampsiteFormInput(
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val maxOccupancy: String = "",
    val pricePerNight: String = "",
    val tags: String = "",
    val rules: String = "",
    val toggle: Boolean = true
)

data class CampsiteFormErrors(
    val name: String? = null,
    val location: String? = null,
    val maxOccupancy: String? = null,
    val pricePerNight: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(name, location, maxOccupancy, pricePerNight).any { it != null }
}

object CampsiteValidator {
    fun validate(input: CampsiteFormInput): CampsiteFormErrors {
        val occupancy = input.maxOccupancy.trim().toIntOrNull()
        val price = input.pricePerNight.trim().toDoubleOrNull()

        return CampsiteFormErrors(
            name = if (input.name.isBlank()) "Name is required" else null,
            location = if(input.location.isBlank()) "Location is required" else null,
            maxOccupancy = when {
                input.maxOccupancy.isBlank() -> "Required"
                occupancy == null || occupancy <= 0 -> "Enter a number above 0"
                else -> null
            },
            pricePerNight = when {
                input.pricePerNight.isBlank() -> "Required"
                price == null || price < 0 -> "Enter 0 or more"
                else -> null
            }
        )
    }
}