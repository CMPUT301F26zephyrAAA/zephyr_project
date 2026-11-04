package com.example.zephyrcampsites
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * View responsible for displaying and editing account details.
 */
class AccountsView {

    /**
     * Displays the entrant's editable profile.
     *
     * @param account Currently identified account.
     * @param accountsController Controller for updating the profile.
     * @param onSaved Called after a successful update.
     * @param onBack Called when the Back button is pressed.
     */
    @Composable
    fun ProfileView(
        account: Account,
        accountsController: AccountsController,
        onSaved: (Account) -> Unit,
        onBack: () -> Unit
    ) {

        var name by remember(account.id, account.name) {
            mutableStateOf(account.name)
        }

        var email by remember(account.id, account.email) {
            mutableStateOf(account.email)
        }

        var phoneNumber by remember(account.id, account.phoneNumber) {
            mutableStateOf(account.phoneNumber)
        }

        var errorMessage by remember {
            mutableStateOf("")
        }

        var successMessage by remember {
            mutableStateOf("")
        }

        var saving by remember {
            mutableStateOf(false)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Text("My Profile")

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileName")
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileEmail")
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profilePhone")
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage)
            }

            if (successMessage.isNotEmpty()) {
                Text(successMessage)
            }

            Button(
                modifier = Modifier.testTag("saveProfile"),
                enabled = !saving,

                onClick = {

                    errorMessage = ""
                    successMessage = ""
                    saving = true

                    accountsController.updateProfile(
                        account = account,
                        name = name,
                        email = email,
                        phoneNumber = phoneNumber,

                        onSuccess = { updatedAccount ->
                            onSaved(updatedAccount)
                            saving = false
                            successMessage =
                                "Profile updated successfully"
                        },

                        onError = { error ->
                            saving = false
                            errorMessage = error
                        }
                    )
                }
            ) {
                Text(if (saving) "Saving..." else "Save Changes")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
