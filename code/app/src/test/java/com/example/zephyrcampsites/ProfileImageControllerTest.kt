
package com.example.zephyrcampsites

import android.content.Context
import android.net.Uri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests entrant profile image management without
 * connecting to Firebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileImageControllerTest {

    private lateinit var context: Context
    private lateinit var model: FakeAccountsModel
    private lateinit var controller: AccountsController

    private val uid = "test-user"
    private val accountId = "auth_$uid"

    private lateinit var imageUri: Uri

    /**
     * Simulates successful and unsuccessful model operations.
     */
    private class FakeAccountsModel : AccountsModel() {

        var updateCalls = 0
        var removeCalls = 0
        var failure: Exception? = null

        override fun updateOwnProfileImage(
            context: Context,
            account: Account,
            imageUri: Uri,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit
        ) {
            updateCalls++

            val error = failure

            if (error != null) {
                onError(error)
            } else {
                account.compressedImage = "new-compressed-image"
                account.imagePath = ""
                onSuccess()
            }
        }

        override fun removeOwnProfileImage(
            account: Account,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit
        ) {
            removeCalls++

            val error = failure

            if (error != null) {
                onError(error)
            } else {
                account.compressedImage = ""
                account.imagePath = ""
                onSuccess()
            }
        }
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()

        imageUri = Uri.parse(
            "content://test/profile.jpg"
        )

        model = FakeAccountsModel()
        controller = AccountsController(model) { uid }
    }

    @Test
    fun entrantCanAddOwnProfileImage() {
        val account = Account(
            id = accountId,
            userType = listOf("entrant")
        )

        var result: Account? = null

        controller.updateOwnProfileImage(
            context,
            account,
            imageUri,
            onSuccess = { result = it },
            onError = { fail(it) }
        )

        assertEquals(1, model.updateCalls)
        assertNotNull(result)

        assertEquals(
            "new-compressed-image",
            result?.compressedImage
        )

        assertEquals("", result?.imagePath)
        assertEquals("", account.compressedImage)
    }

    @Test
    fun entrantCanReplaceOwnProfileImage() {
        val account = Account(
            id = accountId,
            userType = listOf("entrant"),
            compressedImage = "old-image"
        )

        var result: Account? = null

        controller.updateOwnProfileImage(
            context,
            account,
            imageUri,
            onSuccess = { result = it },
            onError = { fail(it) }
        )

        assertEquals(1, model.updateCalls)

        assertEquals(
            "new-compressed-image",
            result?.compressedImage
        )

        assertEquals(
            "old-image",
            account.compressedImage
        )
    }

    @Test
    fun entrantCanRemoveOwnProfileImage() {
        val account = Account(
            id = accountId,
            userType = listOf("entrant"),
            compressedImage = "existing-image"
        )

        var result: Account? = null

        controller.removeOwnProfileImage(
            account,
            onSuccess = { result = it },
            onError = { fail(it) }
        )

        assertEquals(1, model.removeCalls)
        assertNotNull(result)
        assertEquals("", result?.compressedImage)
        assertEquals("", result?.imagePath)
    }

    @Test
    fun entrantCannotUpdateAnotherAccountsImage() {
        val account = Account(
            id = "another-device",
            userType = listOf("entrant")
        )

        var error: String? = null

        controller.updateOwnProfileImage(
            context,
            account,
            imageUri,
            onSuccess = { fail("Update should be rejected") },
            onError = { error = it }
        )

        assertEquals(0, model.updateCalls)

        assertEquals(
            "You can only update your own profile image.",
            error
        )
    }

    @Test
    fun entrantCannotRemoveAnotherAccountsImage() {
        val account = Account(
            id = "another-device",
            userType = listOf("entrant"),
            compressedImage = "existing-image"
        )

        var error: String? = null

        controller.removeOwnProfileImage(
            account,
            onSuccess = { fail("Removal should be rejected") },
            onError = { error = it }
        )

        assertEquals(0, model.removeCalls)

        assertEquals(
            "You can only remove your own profile image.",
            error
        )
    }

    @Test
    fun nonEntrantCannotUpdateProfileImage() {
        val account = Account(
            id = accountId,
            userType = listOf("owner")
        )

        var error: String? = null

        controller.updateOwnProfileImage(
            context,
            account,
            imageUri,
            onSuccess = { fail("Update should be rejected") },
            onError = { error = it }
        )

        assertEquals(0, model.updateCalls)

        assertEquals(
            "Only entrants can update their profile image.",
            error
        )
    }

    @Test
    fun nonEntrantCannotRemoveProfileImage() {
        val account = Account(
            id = accountId,
            userType = listOf("owner"),
            compressedImage = "existing-image"
        )

        var error: String? = null

        controller.removeOwnProfileImage(
            account,
            onSuccess = { fail("Removal should be rejected") },
            onError = { error = it }
        )

        assertEquals(0, model.removeCalls)

        assertEquals(
            "Only entrants can remove their profile image.",
            error
        )
    }

    @Test
    fun updateFailureReturnsErrorAndPreservesImage() {
        val account = Account(
            id = accountId,
            userType = listOf("entrant"),
            compressedImage = "old-image"
        )

        model.failure =
            IllegalStateException("Image compression failed")

        var error: String? = null

        controller.updateOwnProfileImage(
            context,
            account,
            imageUri,
            onSuccess = { fail("Update should fail") },
            onError = { error = it }
        )

        assertEquals(1, model.updateCalls)
        assertEquals("Image compression failed", error)
        assertEquals("old-image", account.compressedImage)
    }

    @Test
    fun removalFailureReturnsErrorAndPreservesImage() {
        val account = Account(
            id = accountId,
            userType = listOf("entrant"),
            compressedImage = "existing-image"
        )

        model.failure =
            IllegalStateException("Firestore update failed")

        var error: String? = null

        controller.removeOwnProfileImage(
            account,
            onSuccess = { fail("Removal should fail") },
            onError = { error = it }
        )

        assertEquals(1, model.removeCalls)
        assertEquals("Firestore update failed", error)

        assertEquals(
            "existing-image",
            account.compressedImage
        )
    }

    @Test
    fun updateFailureWithoutMessageReturnsDefaultError() {
        val account = Account(
            id = accountId,
            userType = listOf("entrant")
        )

        model.failure = Exception()

        var error: String? = null

        controller.updateOwnProfileImage(
            context,
            account,
            imageUri,
            onSuccess = { fail("Update should fail") },
            onError = { error = it }
        )

        assertEquals(
            "Failed to update profile image.",
            error
        )
    }
}