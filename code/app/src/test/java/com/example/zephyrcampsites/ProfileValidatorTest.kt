
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for validating entrant profile information.
 */
class ProfileValidatorTest {

    private lateinit var validator: ProfileValidator

    @Before
    fun setUp() {
        validator = ProfileValidator()
    }

    @Test
    fun validProfile_returnsNull() {
        val result = validator.validate(
            "Test User",
            "test@example.com",
            "7805551234"
        )

        assertNull(result)
    }

    @Test
    fun blankName_returnsError() {
        val result = validator.validate(
            "   ",
            "test@example.com",
            "7805551234"
        )

        assertEquals("Name cannot be blank", result)
    }

    @Test
    fun invalidEmail_returnsError() {
        val result = validator.validate(
            "Test User",
            "invalidemail",
            "7805551234"
        )

        assertEquals("Invalid email address", result)
    }

    @Test
    fun invalidPhone_returnsError() {
        val result = validator.validate(
            "Test User",
            "test@example.com",
            "123"
        )

        assertEquals("Invalid phone number", result)
    }
}
