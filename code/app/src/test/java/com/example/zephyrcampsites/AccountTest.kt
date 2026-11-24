
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Account data class.
 */
class AccountTest {

    /**
     * Checks that a new account has the default entrant and owner roles.
     */
    @Test
    fun newAccount_defaultUserType_includesEntrantAndOwner() {
        val account = Account("testDevice123")
        assertEquals(listOf("entrant", "owner"), account.userType)
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
