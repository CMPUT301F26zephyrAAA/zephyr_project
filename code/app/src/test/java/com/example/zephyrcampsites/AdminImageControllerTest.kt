package com.example.zephyrcampsites

import org.junit.Assert.*
import org.junit.Test

/** Verifies image-removal authorization and error forwarding with an in-memory account model. */
class AdminImageControllerTest {
    private class FakeAccountsModel : AccountsModel() {
        var calls = 0
        var removedPath: String? = null
        var failure: Exception? = null

        override fun removeProfileImage(
            account: Account,
            path: String,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit,
        ) {
            calls++
            removedPath = path
            val error = failure
            if (error == null) {
                onSuccess()
            } else {
                onError(error)
            }
        }
    }

    @Test
    fun entrantAndOwnerCannotRemoveProfileImages() {
        val model = FakeAccountsModel()
        for (role in listOf("entrant", "owner")) {
            var rejected = false
            AccountsController(model)
                .removeProfileImage(
                    Account("actor", userType = role),
                    Account("target"),
                    "profiles/photo.png",
                    { fail("Non-admin removal was accepted") },
                    { rejected = true },
                )
            assertTrue(rejected)
        }
        assertEquals(0, model.calls)
    }

    @Test
    fun adminForwardsTheExactSelectedImagePath() {
        val model = FakeAccountsModel()
        var success = false
        AccountsController(model)
            .removeProfileImage(
                Account("admin", userType = "admin"),
                Account("target"),
                "profiles/photo.png",
                { success = true },
                { fail(it.message) },
            )
        assertTrue(success)
        assertEquals("profiles/photo.png", model.removedPath)
    }

    @Test
    fun deletionFailureIsForwardedInsteadOfReportedAsSuccess() {
        val model =
            FakeAccountsModel().apply { failure = IllegalStateException("Storage denied deletion") }
        var error: Exception? = null
        AccountsController(model)
            .removeProfileImage(
                Account("admin", userType = "admin"),
                Account("target"),
                "profiles/photo.png",
                { fail("Failed deletion reported success") },
                { error = it },
            )
        assertEquals("Storage denied deletion", error?.message)
    }
}
