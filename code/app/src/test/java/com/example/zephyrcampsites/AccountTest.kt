// Hiral - [AI-10] OpenAI ChatGPT Add admin removal flow
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Account data class.
 */
class AccountTest {

    @Test
    fun ownerModeUsesSavedProfileBeforeRevocationListenerResponds() {
        val owner = Account("auth_owner")
        assertTrue(owner.canUseOwnerMode(null))
        assertFalse(owner.copy(ownerPrivilegesRevoked = true).canUseOwnerMode(null))
        assertFalse(owner.canUseOwnerMode(setOf(owner.id)))
    }

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

    @Test
    fun missingAdminClaimOverridesSavedAdminRole() {
        val saved = Account("auth_user", listOf("entrant", "owner", "admin"), name = "Taylor")

        assertEquals(
            saved.copy(userType = listOf("entrant", "owner")),
            saved.withVerifiedAdminRole(false)
        )
    }

    @Test
    fun verifiedAdminClaimGrantsAdminRoleWithoutSavedAdminRole() {
        val saved = Account("auth_user")

        assertEquals(
            saved.copy(userType = listOf("entrant", "owner", "admin")),
            saved.withVerifiedAdminRole(true)
        )
    }
}
