package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test

/** Checks profile-removal authorization and callback forwarding without Firebase. */
class AdminProfileControllerTest {
    private class FakeAccountsModel : AccountsModel() {
        var calls = 0
        var removedId: String? = null
        var failure: Exception? = null

        override fun deleteAccount(
            targetAccount: Account,
            adminId: String,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit,
        ) {
            calls++
            removedId = targetAccount.id
            val error = failure
            if (error == null) {
                onSuccess()
            } else {
                onError(error)
            }
        }
    }

    @Test
    fun nonAdminsCannotRemoveProfiles() {
        val model = FakeAccountsModel()
        val controller = AccountsController(model)
        for (account in listOf(null, Account("entrant"), Account("owner", userType = "owner"))) {
            var error: Exception? = null
            controller.deleteProfile(
                account,
                Account("target"),
                { fail("Removal should be blocked") },
                { error = it },
            )
            assertNotNull(error)
        }
        assertEquals(0, model.calls)
    }

    @Test
    fun invalidTargetIdIsRejectedBeforeStorage() {
        val model = FakeAccountsModel()
        for (id in listOf("", "invalid/id")) {
            AccountsController(model)
                .deleteProfile(
                    Account("admin", userType = "admin"),
                    Account(id),
                    { fail("Invalid ID was accepted") },
                    {},
                )
        }
        assertEquals(0, model.calls)
    }

    @Test
    fun adminRemovalWaitsForModelAndForwardsItsResult() {
        val model = FakeAccountsModel()
        var success = false
        AccountsController(model)
            .deleteProfile(
                Account("admin", userType = "admin"),
                Account("target"),
                { success = true },
                { fail(it.message) },
            )
        assertTrue(success)
        assertEquals("target", model.removedId)
        assertEquals(1, model.calls)
    }

    @Test
    fun storageFailureIsReturnedWithoutSuccess() {
        val model =
            FakeAccountsModel().apply { failure = IllegalStateException("Permission denied") }
        var error: Exception? = null
        AccountsController(model)
            .deleteProfile(
                Account("admin", userType = "admin"),
                Account("target"),
                { fail("Failed removal reported success") },
                { error = it },
            )
        assertEquals("Permission denied", error?.message)
    }
}
