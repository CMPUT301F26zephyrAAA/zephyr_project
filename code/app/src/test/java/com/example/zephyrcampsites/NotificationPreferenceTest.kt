package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies notification preference updates and profile edits using an in-memory account model.
 */
class NotificationPreferenceTest {
    private val account = Account(
        id = "device-test",
        userType = "owner",
        name = "Taylor Morgan",
        email = "taylor@example.com",
        phoneNumber = "7805550100"
    )
    private val model = TestAccountsModel(account)
    private val controller = AccountsController(model)

    @Test
    fun disablingNotificationsReturnsSavedAccountWithoutChangingProfile() {
        var updated: Account? = null
        controller.updateNotificationPreference(account, false, { updated = it }, { throw AssertionError(it) })

        assertFalse(model.saved.notificationsEnabled)
        assertEquals(account.copy(notificationsEnabled = false), updated)
        assertNotSame(account, updated)
        assertTrue(account.notificationsEnabled)
    }

    @Test
    fun enablingNotificationsRestoresPreference() {
        val disabled = account.copy(notificationsEnabled = false)
        model.saved = disabled.copy()
        var updated: Account? = null
        controller.updateNotificationPreference(disabled, true, { updated = it }, { throw AssertionError(it) })

        assertTrue(model.saved.notificationsEnabled)
        assertTrue(updated!!.notificationsEnabled)
    }

    @Test
    fun failedSaveDoesNotChangeTheSavedPreference() {
        model.failUpdates = true
        var updated: Account? = null
        var error: String? = null
        controller.updateNotificationPreference(account, false, { updated = it }, { error = it })

        assertNull(updated)
        assertNotNull(error)
        assertTrue(model.saved.notificationsEnabled)
        assertTrue(account.notificationsEnabled)
    }

    @Test
    fun invalidAccountIdDoesNotReachTheModel() {
        for (id in listOf("", " ", "invalid/path")) {
            var error: String? = null
            controller.updateNotificationPreference(account.copy(id = id), false,
                { throw AssertionError("Invalid accounts must not be updated.") }, { error = it })
            assertNotNull(error)
        }
        assertEquals(0, model.preferenceUpdates)
    }

    @Test
    fun editingProfileKeepsNotificationsDisabled() {
        val disabled = account.copy(notificationsEnabled = false)
        model.saved = disabled.copy()
        var updated: Account? = null
        controller.updateProfile(disabled, "Updated Name", "updated@example.com", "7805550199",
            { updated = it }, { throw AssertionError(it) })

        assertEquals("Updated Name", updated!!.name)
        assertEquals(account.id, updated!!.id)
        assertEquals(account.userType, updated!!.userType)
        assertFalse(updated!!.notificationsEnabled)
        assertFalse(model.saved.notificationsEnabled)
    }

    /**
     * Stores account changes in memory and can simulate a failed save.
     *
     * @param account Initial saved account.
     */
    private class TestAccountsModel(account: Account) : AccountsModel() {
        var saved = account.copy()
        var failUpdates = false
        var preferenceUpdates = 0

        override fun updateNotificationPreference(
            deviceId: String,
            enabled: Boolean,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit
        ) {
            preferenceUpdates++
            if (failUpdates) {
                onError(IllegalStateException("Simulated failure"))
            } else {
                assertEquals(saved.id, deviceId)
                saved = saved.copy(notificationsEnabled = enabled)
                onSuccess()
            }
        }

        override fun updateAccount(account: Account, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
            saved = saved.copy(name = account.name, email = account.email, phoneNumber = account.phoneNumber)
            onSuccess()
        }
    }
}
