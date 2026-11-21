package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.ListenerRegistration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests admin profile browsing and confirmation with in-memory account fixtures. */
@RunWith(AndroidJUnit4::class)
class AdminProfileViewTest {
    @get:Rule val compose = createComposeRule()
    private val admin = Account("admin", userType = "admin")
    private val profile =
        Account(
            "entrant",
            name = "Taylor Morgan",
            email = "taylor@example.com",
            phoneNumber = "7805550100",
        )

    private class FakeAccountsModel(private val profiles: List<Account>) : AccountsModel() {
        var listens = 0
        var deletions = 0
        var failure: Exception? = null

        override fun observeAccounts(
            onSuccess: (List<Account>) -> Unit,
            onError: (Exception) -> Unit,
        ): ListenerRegistration {
            listens++
            onSuccess(profiles)
            return ListenerRegistration {}
        }

        override fun deleteAccount(
            targetAccount: Account,
            adminId: String,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit,
        ) {
            deletions++
            val error = failure
            if (error == null) {
                onSuccess()
            } else {
                onError(error)
            }
        }
    }

    @Test
    fun browseDisplaysProfileFieldsAndSelectionWithoutRestartingListener() {
        val model = FakeAccountsModel(listOf(profile))
        var selected: Account? = null
        compose.setContent {
            AdminBrowseProfileView().AdminProfileListView(admin, model, { selected = it }, {})
        }
        compose.onNodeWithText("Taylor Morgan").assertIsDisplayed()
        compose.onNodeWithText("Email: taylor@example.com").assertIsDisplayed()
        compose.onNodeWithText("Phone Number: 7805550100").assertIsDisplayed()
        compose.onNodeWithText("No profile image").assertIsDisplayed()
        compose.onNodeWithTag("ProfileItem_entrant").performClick()
        compose.runOnIdle {
            assertEquals(profile, selected)
            assertEquals(1, model.listens)
        }
    }

    @Test
    fun emptyListAndBackAreDisplayed() {
        var back = false
        compose.setContent {
            AdminBrowseProfileView()
                .AdminProfileListView(admin, FakeAccountsModel(emptyList()), {}, { back = true })
        }
        compose.onNodeWithText("No profiles found.").assertIsDisplayed()
        compose.onNodeWithText("BACK").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun nonAdminDoesNotStartProfileListener() {
        val model = FakeAccountsModel(listOf(profile))
        compose.setContent {
            AdminBrowseProfileView().AdminProfileListView(Account("entrant"), model, {}, {})
        }
        compose.onNodeWithText("Administrator access required.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, model.listens) }
    }

    @Test
    fun removalRequiresConfirmationAndNavigatesOnlyAfterSuccess() {
        val model = FakeAccountsModel(listOf(profile))
        var returned = false
        compose.setContent {
            AdminBrowseProfileView()
                .AdminProfileDetailView(
                    admin,
                    profile,
                    AccountsController(model),
                    { returned = true },
                    {},
                )
        }
        compose.onNodeWithTag("AdminDeleteProfileButton").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, model.deletions)
            assertFalse(returned)
        }
        compose.onNodeWithTag("AdminDeleteProfileButton").performScrollTo().performClick()
        compose.onNodeWithTag("ConfirmRemoveProfile").performClick()
        compose.runOnIdle {
            assertEquals(1, model.deletions)
            assertTrue(returned)
        }
    }

    @Test
    fun failureStaysOnProfileAndDisplaysError() {
        val model =
            FakeAccountsModel(listOf(profile)).apply {
                failure = IllegalStateException("Permission denied")
            }
        var returned = false
        compose.setContent {
            AdminBrowseProfileView()
                .AdminProfileDetailView(
                    admin,
                    profile,
                    AccountsController(model),
                    { returned = true },
                    {},
                )
        }
        compose.onNodeWithTag("AdminDeleteProfileButton").performScrollTo().performClick()
        compose.onNodeWithTag("ConfirmRemoveProfile").performClick()
        compose.onNodeWithText("Permission denied").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertFalse(returned) }
    }
}
