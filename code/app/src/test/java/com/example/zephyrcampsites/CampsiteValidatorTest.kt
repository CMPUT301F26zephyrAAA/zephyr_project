package com.example.zephyrcampsites

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class CampsiteValidatorTest {
    private val valid = CampsiteFormInput(
        name = "Lakeview", location = "123 Trail Rd",
        maxOccupancy = "4", pricePerNight = "25.50"
    )

    @Test fun validInputHasNoErrors() =
        assertFalse(CampsiteValidator.validate(valid).hasErrors)

    @Test fun blankNameAndLocation() {
        val blank = CampsiteValidator.validate(valid.copy(name = " ", location = " "))
        assertNotNull(blank.name); assertNotNull(blank.location)
    }

    @Test fun occupancyError() {
        assertNotNull(CampsiteValidator.validate(valid.copy(maxOccupancy = "0")).maxOccupancy)
        assertNotNull(CampsiteValidator.validate(valid.copy(maxOccupancy = "abc")).maxOccupancy)
    }

    @Test fun priceError() {
        assertNotNull(CampsiteValidator.validate(valid.copy(pricePerNight = "-1")).pricePerNight)
        assertNull(CampsiteValidator.validate(valid.copy(pricePerNight = "0")).pricePerNight)
    }
}