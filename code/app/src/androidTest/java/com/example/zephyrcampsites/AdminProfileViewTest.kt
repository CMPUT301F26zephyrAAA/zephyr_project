// Hiral - [AI-10] OpenAI ChatGPT Add admin removal flow
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
    private val admin = Account("admin", userType = listOf("admin"))
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
        var revocations = 0
        var removalReason: String? = null

        override fun removeOwnerPrivileges(
            ownerId: String,
            reason: String,
            onSuccess: () -> Unit,
            onError: (Exception) -> Unit,
        ) {
            revocations++
            removalReason = reason
            val error = failure
            if (error == null) {
                onSuccess()
            } else {
                onError(error)
            }
        }
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
            AdminBrowseProfileView().AdminProfileListView(admin, model, { account, _ ->
                selected = account
            }, {})
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
    fun ownersTabRequiresARegisteredCampsiteAndKeepsBackVisible() {
        val entrant = profile
        val owner = Account("owner", name = "Owner Profile")
        val adminProfile = admin.copy(name = "Admin Profile")
        val model = FakeAccountsModel(listOf(entrant, owner, adminProfile))
        val registeredOwnerIds = mutableStateOf(setOf(owner.id))
        var openedFromOwners: Boolean? = null
        compose.setContent {
            AdminBrowseProfileView().AdminProfileListView(
                admin, model, { _, fromOwners -> openedFromOwners = fromOwners }, {},
                registeredOwnerIds = registeredOwnerIds.value,
            )
        }
        compose.onNodeWithText("All").assertIsSelected()
        compose.onNodeWithText("Admin Profile").assertIsDisplayed()
        compose.onNodeWithTag("ProfileItem_owner").performClick()
        compose.runOnIdle { assertEquals(false, openedFromOwners) }

        compose.onNodeWithText("Owners").performClick().assertIsSelected()
        compose.onNodeWithText("Owner Profile").assertIsDisplayed()
        compose.onNodeWithTag("ProfileItem_owner").performClick()
        compose.runOnIdle { assertEquals(true, openedFromOwners) }
        compose.onNodeWithText("Taylor Morgan").assertDoesNotExist()
        compose.onNodeWithText("Admin Profile").assertDoesNotExist()

        compose.runOnIdle { registeredOwnerIds.value = emptySet() }
        compose.onNodeWithText("No profiles found.").assertIsDisplayed()

        compose.onNodeWithText("Entrants").performClick().assertIsSelected()
        compose.onNodeWithText("Taylor Morgan").assertIsDisplayed()
        compose.onNodeWithTag("ProfileItem_owner").performClick()
        compose.runOnIdle { assertEquals(false, openedFromOwners) }
        compose.onNodeWithText("BACK").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, model.listens) }
    }

    @Test
    fun emptyListAndBackAreDisplayed() {
        var back = false
        compose.setContent {
            AdminBrowseProfileView()
                .AdminProfileListView(admin, FakeAccountsModel(emptyList()), { _, _ -> }, { back = true })
        }
        compose.onNodeWithText("No profiles found.").assertIsDisplayed()
        compose.onNodeWithText("BACK").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun nonAdminDoesNotStartProfileListener() {
        val model = FakeAccountsModel(listOf(profile))
        compose.setContent {
            AdminBrowseProfileView().AdminProfileListView(Account("entrant"), model, { _, _ -> }, {})
        }
        compose.onNodeWithText("Administrator access required.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, model.listens) }
    }

    @Test
    fun ownerRevocationRequiresAReasonAndShowsSuccess() {
        val model = FakeAccountsModel(listOf(profile))
        compose.setContent {
            AdminBrowseProfileView().AdminProfileDetailView(
                admin, profile, AccountsController(model), {}, {},
                showOwnerRemoval = true,
            )
        }
        val removeButton = compose.onNodeWithText("Remove Owner Privileges")
        removeButton.performScrollTo().performClick()
        compose.onNodeWithText("Remove Privileges").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, model.revocations) }

        removeButton.performScrollTo().performClick()
        compose.onNodeWithText("Reason for removal").performTextInput("Policy violation")
        compose.onNodeWithText("Remove Privileges").performClick()
        compose.onNodeWithText("Owner privileges removed.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, model.revocations)
            assertEquals("Policy violation", model.removalReason)
            assertEquals(0, model.deletions)
        }
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
        compose.onNodeWithText("Remove Owner Privileges").assertDoesNotExist()
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
