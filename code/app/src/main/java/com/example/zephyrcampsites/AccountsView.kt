package com.example.zephyrcampsites
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zephyrcampsites.ui.theme.ProfileMenuGreen

/**
 * View class for displaying and editing entrant account details
 * Uses AccountsController to update personal information in Firestore.
 */
class AccountsView {

    /***
     * Displays the main profile page
     * @param account Current entrants account
     * @param onEdit opens the personal information editing screen
     * @param onPolicy Opens the app policy and guidelines.
     * @param onBack returns to the previous screen
     */

    @Composable
    fun ProfileOverviewView(
        account: Account,
        onEdit: () -> Unit,
        onPolicy: () -> Unit,
        onBack: () -> Unit
    ){
        Column(modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "PROFILE",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (account.name.isBlank()){
                    "FULL NAME"
                } else {
                    account.name
                },
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "Device ID Number",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = account.id,
                style = MaterialTheme.typography.bodySmall
            )

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                profileOption(
                    title = "Personal Information",
                    description = "View and change personal information",
                    tag = "personalInformationButton",
                    onClick = onEdit
                )
                profileOption(
                    title = stringResource(R.string.policy_entry_title),
                    description = stringResource(R.string.policy_entry_description),
                    tag = "PolicyAndGuidelinesEntry",
                    onClick = onPolicy
                )
            }
            Text("Account and App Settings", style = MaterialTheme.typography.titleMedium)
            Text("Change settings related to notifications, app themes, etc")
            Text("DELETE PROFILE", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onBack) {
                Text("BACK")
            }
        }
    }

    /**
     * Displays a clickable profile menu entry with a title, description, and navigation arrow.
     *
     * @param title Name of the destination.
     * @param description Description displayed below the title.
     * @param tag Test identifier for the entry.
     * @param onClick Opens the destination.
     */
    @Composable
    private fun profileOption(title: String, description: String, tag: String, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().testTag(tag),
            shape = RoundedCornerShape(2.dp),
            color = ProfileMenuGreen,
            contentColor = Color.White
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 98.dp)
                    .padding(start = 15.dp, top = 10.dp, end = 10.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(end = 28.dp, bottom = 28.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Light)
                    Text(description, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraLight)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(24.dp)
                )
            }
        }
    }

    /**
     * Displays the editable personal information form
     *
     * @param account Currently identified account.
     * @param accountsController Controller for validating and saving updates
     * @param onSaved Called after a successfully saving the updated account.
     * @param onBack Returns to the profile overview
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "PERSONAL INFORMATION", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name:") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileName")
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email ID:") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profileEmail")
            )

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profilePhone")
            )

            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
            }

            if (successMessage.isNotEmpty()) {
                Text(text = successMessage, color = MaterialTheme.colorScheme.primary)
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saveProfile"),
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
                Text(if (saving) "Saving..." else "SAVE")
            }
            OutlinedButton(
                onClick = onBack,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK")
            }
        }
    }
}
