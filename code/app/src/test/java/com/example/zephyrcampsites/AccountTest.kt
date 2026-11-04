
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Account data class.
 */
class AccountTest {

    /**
     * Checks that a new account has the entrant role.
     */
    @Test
    fun newAccount_defaultUserType_isEntrant() {
        val account = Account("testDevice123")
        assertEquals("entrant", account.userType)
    }

    /**
     * Checks that an account stores the correct device ID.
     */
    @Test
    fun newAccount_deviceId_isStoredCorrectly() {
        val account = Account("testDevice123")
        assertEquals("testDevice123", account.id)
    }
}
